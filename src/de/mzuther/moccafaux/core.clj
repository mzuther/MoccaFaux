(ns de.mzuther.moccafaux.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [chime.core :as chime]
            [com.rpl.specter :as sp]
            [popen]
            [trptcolin.versioneer.core :as version])
  (:gen-class))


(def preferences
  (try (let [;; "io/file" takes care of line separators
             file-name     (io/file (System/getProperty "user.home")
                                    ".config" "moccafaux" "config.json")
             user-prefs    (json/read-str (slurp file-name)
                                          :key-fn keyword)
             default-prefs {:probing-interval 60,
                            :watches          {},
                            :tasks            {}}]
         (when-not (map? user-prefs)
           (throw (Exception. "JSON error (not handled by library)")))
         (merge default-prefs
                user-prefs))
       (catch Throwable e
         (newline)
         (println (str e))
         (newline)
         (flush)
         (System/exit 1))))


(def defined-tasks
  (->> preferences
       (sp/select [:tasks sp/MAP-KEYS])
       sort))


;; empty ref forces an update on first call to "update-status"
(def task-states (ref {}))


(defn- printfln
  "Print formatted output, as per format, followed by (newline)."
  [fmt & args]
  (println (apply format fmt args)))


(defn- print-header
  "Print a nicely formatted header with application name and version number."
  []
  (let [fill-string  (fn [length char] (->> char (repeat length) (apply str)))
        add-borders  (fn [s char] (string/join [char s char]))

        header       (str "MoccaFaux v" (version/get-version "de.mzuther" "moccafaux.core"))

        page-width   78
        header-width (count header)
        left-margin  (quot (- page-width header-width) 2)
        right-margin (- page-width header-width left-margin)

        full-line    (add-borders (fill-string page-width "-") "o")
        full-header  (add-borders
                       (string/join [(fill-string left-margin " ")
                                     header
                                     (fill-string right-margin " ")])
                       "|")]
    (println)
    (println full-line)
    (println full-header)
    (println full-line)))


(defn- shell-exec
  "Execute command in a shell compatible to the Bourne shell and
  fork process if fork? is true.

  Return :forked if command has forked, :success if command has
  exited with a zero exit code, and :failed in any other case."
  [command fork?]
  (let [new-process (popen/popen ["sh" "-c" command])]
    (if-not fork?
      (if (zero? (popen/exit-code new-process))
        :success
        :failed)
      (do
        ;; wait for 10 ms to check whether the process is actually
        ;; created and running
        (Thread/sleep 10)
        (if (popen/running? new-process)
          :forked
          :failed)))))


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
                       (let [exit-state (shell-exec command false)]
                         (if (= exit-state :failed)
                           :enable
                           :disable)))]
    ;; Apply exit state of watch to all defined tasks (or nil when the
    ;; watch has not been assigned to the given task).
    (reduce (fn [exit-states task]
              (assoc exit-states
                     task
                     (when (get tasks task)
                       save-energy?)))
            {:watch watch-name}
            defined-tasks)))


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
  (let [timestamp (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
                     format
                     (java.time.LocalTime/now))
        padding   "          "

        prefs     (sp/select-one [:tasks task new-state] preferences)
        command   (sp/select-one [:command] prefs)
        fork?     (sp/select-one [:fork] prefs)
        message   (sp/select-one [:message] prefs)]
    (when command
      (println)
      (printfln "[%s]  Task:    %s" timestamp (name task))
      (printfln "%s  State:   %s (%s)" padding (name new-state) message)
      (printfln "%s  Command: %s" padding command)
      ;; execute command (finally)
      (let [exit-state (shell-exec command fork?)]
        (printfln "%s  Result:  %s" padding (name exit-state))
        exit-state))))


(defn- enable-disable-or-nil?
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
  (let [exit-states     (->> (sp/select-one [:watches] preferences)
                             (map watch-exec))
        new-task-states (reduce (fn [new-ts task]
                                  (assoc new-ts
                                         task
                                         (enable-disable-or-nil?
                                           (map task exit-states))))
                                {}
                                defined-tasks)
        update-task     (fn [task]
                          (let [new-state (sp/select-one [task] new-task-states)
                                old-state (sp/select-one [task] @task-states)]
                            (when-not (= new-state old-state)
                              (update-energy-saving task new-state))))]
    (doseq [task defined-tasks]
      (update-task task))
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
  [& _]
  (print-header)

  (start-scheduler update-status
                   (sp/select-one [:probing-interval] preferences)))
