(ns de.mzuther.moccafaux.core
  (:require [de.mzuther.moccafaux.helpers :as helpers]
            [de.mzuther.moccafaux.tray :as tray]
            [clojure.data.json :as json]
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


(def preferences
  (try (let [;; "io/file" takes care of line separators
             file-name  (io! (io/file (System/getProperty "user.home")
                                      ".config" "moccafaux" "config.json"))
             user-prefs (json/read-str (slurp file-name)
                                       :key-fn keyword)]
         (if (map? user-prefs)
           user-prefs
           (throw (Exception. "JSON error (not handled by library)"))))
       (catch Throwable e
         (io! (helpers/print-header)
              (newline)
              (println (str e))
              (newline)
              (flush))
         (System/exit 1))))


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
  "Create new TaskState object with given task ID and an exit state
  (:enable if exit state was *non-zero*, :disable if it was *zero*
  and nil if the watch was either disabled or not assigned to a
  task)."
  [id state]
  {:pre [(keyword? id)
         (or (nil? state)
             (get #{:enable :disable} state))]}
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

  Return a vector of a keyword and the created process object.  The
  keyword is :forked if command has forked, :success if command has
  exited with a zero exit code, and :failed in any other case."
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

  Return a map containing the watch's name and an exit state for every
  task in task-names (:enable if exit state was *non-zero*, :disable
  if it was *zero*).  In case a watch has not been enabled or assigned
  to a task, set exit state to nil.

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
                           :enable
                           :disable)))]
    ;; apply exit state of watch to all defined tasks (or nil when the
    ;; watch has not been assigned to the given task).
    (make-task-states watch-name
                      (map (fn [task]
                             (make-task-state task
                                              (when (get tasks task)
                                                save-energy?)))
                           task-names))))


(defn update-energy-saving
  "Update the state of a task according to new-state (:enable
  or :disable) and toggle its energy saving state by executing a
  command.  Print new state and related information.

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
           (helpers/printfln "%s  Task:     %s %s"
                             (helpers/get-timestamp) (name new-state) (name task))
           (helpers/printfln "%s  State:    %s"
                             helpers/padding message)
           (helpers/printfln "%s  Command:  %s"
                             helpers/padding command)
           ;; execute command (finally)
           (let [{exit-state :state} (shell-exec command fork?)]
             (helpers/printfln "%s  Result:   %s"
                               helpers/padding (name exit-state))
             exit-state)))))


(defn enable-disable-or-nil?
  "Reduce coll to a scalar.

  Return :disable if coll contains a :disable value.  If it doesn't
  and contains a non-nil value, return :enable.  In any other case,
  return nil."
  [coll]
  (let [coll (remove nil? coll)]
    (cond
      ;; disabling energy saving has preference
      (some (partial = :disable) coll)
        :disable
      (seq coll)
        :enable
      ;; be explicit; either the watch is disabled or has not been
      ;; assigned to the current task
      :else
        nil)))


(defn poll-task-states
  "Execute all watches in parallel threads and gather states for each
  task in task-names.

  Return new task states, consisting of a map containing keys for all
  defined tasks with values according to \"enable-disable-or-nil?\"."
  [task-names watches]
  (let [exit-states   (->> watches
                           (pmap (partial watch-exec task-names))
                           (doall))
        extract-state (fn [task] (->> exit-states
                                      (sp/select [sp/ALL :states sp/ALL #(= (:id %) task) :state])
                                      (enable-disable-or-nil?)
                                      (vector task)))]
    (into {} (map extract-state task-names))))


(defn update-status
  "Execute all watches and gather states for all defined tasks.  Should
  a task state differ from its current state, update the state and
  toggle its energy saving state by executing a command.

  Return new task states, consisting of a map containing keys for all
  defined tasks with values according to \"enable-disable-or-nil?\"."
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
           (helpers/print-line \-)))
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

                                     ;; disable all tasks, regardless of current state
                                     (doseq [task task-names]
                                       (update-energy-saving task :disable))

                                     (newline)
                                     (helpers/printfln "%s  Good-bye."
                                                       (helpers/get-timestamp))
                                     (newline))))

    ;; add default icon to system tray bar
    (tray/add-to-traybar "moccafaux-fruit.png")

    ;; display settings and enter main loop
    (let [interval (sp/select-one [:scheduler :probing-interval] preferences)]
      (io! (helpers/print-settings interval task-names watch-names)
           (newline)
           (helpers/print-line \-))

      (start-scheduler update-status interval))))
