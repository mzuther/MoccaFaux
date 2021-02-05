;; MoccaFaux
;; =========
;; Adapt power management to changes in the environment
;;
;; Copyright (c) 2020-2021 Martin Zuther (http://www.mzuther.de/) and
;; contributors
;;
;; This program and the accompanying materials are made available under
;; the terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0.
;;
;; This Source Code may also be made available under the following
;; Secondary Licenses when the conditions for such availability set forth
;; in the Eclipse Public License, v. 2.0 are satisfied: GNU General
;; Public License as published by the Free Software Foundation, either
;; version 2 of the License, or (at your option) any later version, with
;; the GNU Classpath Exception which is available at
;; https://www.gnu.org/software/classpath/license.html.


(ns de.mzuther.moccafaux.core
  (:require [de.mzuther.moccafaux.helpers :as helpers]
            [de.mzuther.moccafaux.tray :as tray]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [chime.core :as chime]
            [com.rpl.specter :as sp]
            [popen])
  (:gen-class))


(def cli-options
  [["-h" "--help"
    :desc "display version and usage information"]])


(defn import-edn [& path-parts]
  (try (let [ ;; "io/file" takes care of line separators
             file-handle (io! (apply io/file path-parts))
             temp-data   (edn/read-string {:eof nil}
                                          (slurp file-handle))]
         (if (map? temp-data)
           temp-data
           (throw (Exception. "EDN error (not handled by library)"))))
       (catch Throwable e
         (io! (newline)
              (println (str e))
              (newline)
              (flush))
         (System/exit 1))))


(def preferences (import-edn (System/getProperty "user.home") ".config" "moccafaux" "config.edn"))


(def task-names
  (->> preferences
       (sp/select [:tasks sp/MAP-KEYS])
       sort))


(def defined-watches
  (->> preferences
       (sp/select-one [:watches])
       sort))


(def watch-names
  (sp/select [sp/MAP-KEYS] defined-watches))


;; empty ref forces an update on first call to "update-status"
(def task-states (ref {}))


(defrecord TaskState [id state]
  Object
  (toString [this]
    (let [id    (name (get this :id))
          state (if-let [state (get this :state)]
                  (name state)
                  "nil")]
      (str id "=" state))))


(defrecord TaskStates [id states]
  Object
  (toString [this]
    (let [id (name (get this :id))]
      (str id ": " (string/join ", " states)))))


(defn make-task-state
  "Create new TaskState object with given task ID and current state
  (:active if exit state was *non-zero*, :idle if it was *zero* and
  nil if the watch was either disabled or not assigned to a task)."
  [id state]
  {:pre [(keyword? id)
         (or (nil? state)
             (get #{:active :idle} state))]}
  (TaskState. id state))


(defn make-task-states
  "Create new TaskStates object with given ID and a TaskState seq."
  [id states]
  {:pre [(keyword? id)
         (every? #(= (type %) TaskState) states)]}
  (TaskStates. id states))


(defrecord ProcessObject [process state])


(defn make-process-object
  "Create new ProcessObject object consisting of a Java process object
  and an exit state (:forked if command has forked, :success if
  command has exited with a zero exit code, and :failed in any other
  case)."
  [process state]
  {:pre [(= (type process) java.lang.ProcessImpl)
         (get #{:forked :success :failed} state)]}
  (ProcessObject. process state))


(defn shell-exec
  "Execute command in a shell compatible to the Bourne shell and
  fork process if fork? is true.

  Return a ProcessObject with an exit state of :forked if command has
  forked, :success if command has exited with a zero exit code, and
  :failed in any other case."
  [command fork?]
  {:pre [(string? command)
         (seq command)
         (boolean? fork?)]}
  (io! (let [new-process (popen/popen ["sh" "-c" command])]
         (if-not fork?
           (if (zero? (popen/exit-code new-process))
             (make-process-object new-process :success)
             (make-process-object new-process :failed))
           (do
             ;; wait for 10 ms to check whether the process is actually
             ;; created and running
             (Thread/sleep 10)
             (if (popen/running? new-process)
               (make-process-object new-process :forked)
               (make-process-object new-process :failed)))))))


(defn watch-exec
  "Execute watch and apply exit state of watch to all defined tasks.

  Return a TaskStates object containing the watch's name and a
  TaskState object for every task in task-names.

  Background information: energy saving is enabled when a watch is
  enabled and the respective shell command failed (returned a
  *non-zero* exit code), as this means that no interfering
  processes (or whatever) were found.

  For example, 'grep' and 'pgrep' return a *non-zero* exit code when
  they do not find any matching lines or processes."
  [task-names [watch-name {:keys [enabled command tasks]}]]
  (let [save-energy? (when enabled
                       (let [{exit-state :state} (shell-exec command false)]
                         (if (= exit-state :failed)
                           :active
                           :idle)))]
    ;; apply exit state of watch to all defined tasks (or nil when the
    ;; watch has not been assigned to the given task).
    (make-task-states watch-name
                      (map (fn [task]
                             (make-task-state task
                                              (when (get tasks task)
                                                save-energy?)))
                           task-names))))


(defn update-energy-saving
  "Update the state of a task according to new-state (:active or :idle)
  and toggle its energy saving state by executing a command.  Print
  new state and related information.

  Return exit state of command (as described in shell-exec).
  "
  [task new-state]
  (when (nil? new-state)
    (throw (IllegalArgumentException.
             "eeek, a NIL entered \"update-energy-saving\"")))
  (let [prefs     (sp/select-one [:tasks task new-state] preferences)
        command   (sp/select-one [:command] prefs)
        fork?     (sp/select-one [:fork] prefs)
        message   (sp/select-one [:message] prefs)]
    (io! (when command
           (newline)
           (helpers/printfln "%s  Task:     %s"
                             (helpers/get-timestamp) (name task))
           (helpers/printfln "%s  State:    %s -> %s"
                             helpers/padding (name new-state) message)
           (helpers/printfln "%s  Command:  %s"
                             helpers/padding command)
           ;; execute command (finally)
           (let [{exit-state :state} (shell-exec command fork?)]
             (helpers/printfln "%s  Result:   %s"
                               helpers/padding (name exit-state))
             exit-state)))))


(defn active-idle-or-nil?
  "Reduce coll to a scalar.

  Return :idle if coll contains an :idle value.  If it doesn't and
  contains a non-nil value, return :active.  In any other case, return
  nil."
  [coll]
  (let [coll (remove nil? coll)]
    (cond
      ;; disabling energy saving has preference
      (some (partial = :idle) coll)
        :idle
      (seq coll)
        :active
      ;; be explicit; either the watch is disabled or has not been
      ;; assigned to the current task
      :else
        nil)))


(defn poll-task-states
  "Execute all watches in parallel threads and gather states for each
  task in task-names.

  Return new task states, consisting of a map containing keys for all
  defined tasks with values according to \"active-idle-or-nil?\"."
  [task-names watches]
  (let [exit-states   (->> watches
                           (pmap (partial watch-exec task-names))
                           (doall))
        extract-state (fn [task] (->> exit-states
                                      (sp/select [sp/ALL :states sp/ALL #(= (:id %) task) :state])
                                      (active-idle-or-nil?)
                                      (vector task)))]
    (into {} (map extract-state task-names))))


(defn update-status
  "Execute all watches and gather states for all defined tasks.  Should
  a task state differ from its current state, update the state and
  toggle its energy saving state by executing a command.  Also update
  system tray icon according to new task states.

  Return new task states, consisting of a map containing keys for all
  defined tasks with values according to \"active-idle-or-nil?\"."
  [_]
  (let [new-task-states (poll-task-states task-names defined-watches)
        update-needed?  (not= new-task-states @task-states)
        update-task     (fn [task]
                          (let [new-state (sp/select-one [task] new-task-states)
                                old-state (sp/select-one [task] @task-states)]
                            (when (not= new-state old-state)
                              (update-energy-saving task new-state))))]
    (doseq [task task-names]
      (update-task task))
    (when update-needed?
      (io! (newline)
           (helpers/print-line \-))

      ;; add or update system tray icon
      (when (sp/select-one [:settings :add-traybar-icon] preferences)
        (let [collected-states   (vals new-task-states)
              icon-resource-path (cond
                                   (every? #{:idle} collected-states)
                                     "moccafaux-full.png"
                                   (some #{:idle} collected-states)
                                     "moccafaux-medium.png"
                                   :else
                                     "moccafaux-empty.png")]
          (tray/add-to-traybar new-task-states icon-resource-path))))
    (dosync
      (ref-set task-states
               new-task-states))))


(defn- start-scheduler
  "Execute f immediately, delay by interval and repeat indefinitely.
  Should the computer become unresponsive or enter energy saving
  modes, the intermediate scheduled instants will be dropped."
  [f interval]
  (chime/chime-at (chime/periodic-seq (java.time.Instant/now)
                                      (java.time.Duration/ofSeconds interval))
                  (fn [timestamp]
                    (let [actual-millis (.toEpochMilli (java.time.Instant/now))
                          target-millis (.toEpochMilli ^java.time.Instant timestamp)
                          seconds-late  (/ (- actual-millis target-millis)
                                           1000.0)]
                      ;; skip scheduled instants that were actually
                      ;; spent in computer Nirvana
                      (when-not (>= seconds-late interval)
                        (f timestamp))))
                  ;; display exception and kill scheduler
                  {:error-handler (fn [e] (io! (println (str e))))}))


(defn -main
  "Print information on application and schedule watchers."
  [& unparsed-args]
  (io! (helpers/print-header))

  (let [args (cli/parse-opts unparsed-args cli-options)]
    (cond
      ;; display errors and exit
      (sp/select-one [:errors] args)
        (io! (helpers/exit-after-printing-help-and-errors args 2))

      ;; display help and exit
      (sp/select-one [:options :help] args)
        (io! (helpers/exit-after-printing-help-and-errors args 0)))

    ;; clean up when application ends (System/exit or Ctrl-C)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(io! (newline)
                                     (helpers/printfln "%s  Shutting down gracefully..."
                                                       (helpers/get-timestamp))

                                     ;; set all tasks to "idle", regardless of current state
                                     (doseq [task task-names]
                                       (update-energy-saving task :idle))

                                     (newline)
                                     (helpers/printfln "%s  Good-bye."
                                                       (helpers/get-timestamp))
                                     (newline))))

    ;; display settings and enter main loop
    (let [interval     (sp/select-one [:settings :probing-interval] preferences)
          traybar-icon (sp/select-one [:settings :add-traybar-icon] preferences)]
      (io! (helpers/print-settings interval traybar-icon task-names watch-names)
           (newline)
           (helpers/print-line \-))

      (start-scheduler update-status interval))))
