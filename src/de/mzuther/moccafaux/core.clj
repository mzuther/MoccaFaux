(ns de.mzuther.moccafaux.core
  (:require [de.mzuther.moccafaux.helpers :as helpers]
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
             file-name  (io/file (System/getProperty "user.home")
                                 ".config" "moccafaux" "config.json")
             user-prefs (json/read-str (slurp file-name)
                                       :key-fn keyword)]
         (if (map? user-prefs)
           user-prefs
           (throw (Exception. "JSON error (not handled by library)"))))
       (catch Throwable e
         (helpers/print-header)
         (newline)
         (println (str e))
         (newline)
         (flush)
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


(defrecord TaskStates [id states]
  Object
  (toString [this]
    (let [id (name (get this :id))]
      (str id ": " (string/join ", " states)))))


(defrecord TaskState [id state]
  Object
  (toString [this]
    (let [id    (name (get this :id))
          state (if-let [state (get this :state)]
                  (name state)
                  "nil")]
      (str id "=" state))))


(defn make-task-states [id states]
  (TaskStates. id states))


(defn make-task-state [id state]
  (TaskState. id state))


(defn- shell-exec
  "Execute command in a shell compatible to the Bourne shell and
  fork process if fork? is true.

  Return a vector of a keyword and the created process object.  The
  keyword is :forked if command has forked, :success if command has
  exited with a zero exit code, and :failed in any other case."
  [command fork?]
  (let [new-process (popen/popen ["sh" "-c" command])]
    (if-not fork?
      (if (zero? (popen/exit-code new-process))
        [:success new-process]
        [:failed new-process])
      (do
        ;; wait for 10 ms to check whether the process is actually
        ;; created and running
        (Thread/sleep 10)
        (if (popen/running? new-process)
          [:forked new-process]
          [:failed new-process])))))


(defn- watch-exec
  "Execute watch and apply exit state of watch to all defined tasks.

  Return a map containing the watch's name and an exit state for every
  defined task (:enabled if exit state was *non-zero*, :disabled if it
  was *zero*).  In case a watch has not been enabled or to a given
  task, set exit state to nil.

  Background information: energy saving is enabled when a watch is
  enabled and the respective shell command failed (returned a
  *non-zero* exit code), as this means that no interfering
  processes (or whatever) were found.

  For example, 'grep' and 'pgrep' return a *non-zero* exit code when
  they do not find any matching lines or processes."
  [[watch-name {:keys [enabled command tasks]}]]
  (let [save-energy? (when enabled
                       (let [[exit-state _] (shell-exec command false)]
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
    (when command
      (newline)
      (helpers/printfln "%s  Task:     %s %s"
                        (helpers/get-timestamp) (name new-state) (name task))
      (helpers/printfln "%s  State:    %s"
                        helpers/padding message)
      (helpers/printfln "%s  Command:  %s"
                        helpers/padding command)
      ;; execute command (finally)
      (let [[exit-state _] (shell-exec command fork?)]
        (helpers/printfln "%s  Result:   %s"
                          helpers/padding (name exit-state))
        exit-state))))


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


(defn update-status
  "Execute all watches and gather states for all defined tasks.  Should
  a task state differ from its current state, update the state and
  toggle its energy saving state by executing a command.

  Return new task states, consisting of a map containing keys for all
  defined tasks with values according to \"enable-disable-or-nil?\"."
  [_]
  (let [exit-states     (map watch-exec defined-watches)
        extract-state   (fn [task] (->> exit-states
                                        (sp/select [sp/ALL :states sp/ALL #(= (:id %) task) :state])
                                        (enable-disable-or-nil?)
                                        (vector task)))
        new-task-states (into {} (map extract-state task-names))
        update-needed?  (not= new-task-states @task-states)
        update-task     (fn [task]
                          (let [new-state (sp/select-one [task] new-task-states)
                                old-state (sp/select-one [task] @task-states)]
                            (when (not= new-state old-state)
                              (update-energy-saving task new-state))))]
    (doseq [task task-names]
      (update-task task))
    (when update-needed?
      (newline)
      (helpers/print-line \-))
    (dosync (ref-set task-states
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
                          target-millis (.toEpochMilli timestamp)
                          seconds-late  (/ (- actual-millis target-millis)
                                           1000.0)]
                      ;; skip scheduled instants that were actually
                      ;; spent in computer Nirvana
                      (when-not (>= seconds-late interval)
                        (f timestamp))))
                  ;; display exception and kill scheduler
                  {:error-handler (fn [e] (println (str e)))}))


(defn -main
  "Print information on application and schedule watchers."
  [& unparsed-args]
  (helpers/print-header)

  (let [args (cli/parse-opts unparsed-args cli-options)]
    (cond
      ;; display errors and exit
      (sp/select-one [:errors] args)
        (helpers/exit-after-printing-help-and-errors args 2)

      ;; display help and exit
      (sp/select-one [:options :help] args)
        (helpers/exit-after-printing-help-and-errors args 0))

    ;; display settings and enter main loop
    (let [interval (sp/select-one [:scheduler :probing-interval] preferences)]
      (helpers/print-settings interval task-names watch-names)

      (newline)
      (helpers/print-line \-)

      (start-scheduler update-status interval))))
