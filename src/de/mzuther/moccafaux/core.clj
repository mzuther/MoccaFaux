(ns de.mzuther.moccafaux.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [chime.core :as chime]
            [com.rpl.specter :as sp]
            [popen]
            [trptcolin.versioneer.core :as version])
  (:gen-class))


(defn- printfln [fmt & args]
  (println (apply format fmt args)))


(defn- print-header []
  (let [fill-string  (fn [length char] (->> char
                                            (repeat length)
                                            (apply str)))
        add-borders  (fn [s char] (string/join [char s char]))

        header       (str "MoccaFaux v" (version/get-version "de.mzuther"
                                                             "moccafaux.core"))

        page-width   78
        left-margin  (quot (- page-width (count header)) 2)
        right-margin (- page-width (count header) left-margin)

        full-line    (-> (fill-string page-width "-")
                         (add-borders "o"))
        full-header  (-> (string/join [(fill-string left-margin " ")
                                       header
                                       (fill-string right-margin " ")])
                         (add-borders "|"))]
    (println full-line)
    (println full-header)
    (println full-line)))


(def default-preferences {:probing-interval 60,
                          :watches          [],
                          :tasks            {}})


;; empty ref forces an update on first call to "update-status"
(def task-states (ref {}))


(def preferences
  (try (let [file-name  (io/file (System/getProperty "user.home")
                                 ".config" "moccafaux" "config.json")
             user-prefs (json/read-str (slurp file-name)
                                       :key-fn keyword)]
         (when-not (map? user-prefs)
           (throw (Exception. "JSON error (not handled by library)")))
         (merge default-preferences
                user-prefs))
       (catch Throwable e
         (newline)
         (println (.toString e))
         (newline)
         (flush)
         (System/exit 1))))


(def defined-tasks
  (->> preferences
       (sp/select [:tasks sp/MAP-KEYS])
       sort))


(defn- shell-exec [command fork?]
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


(defn- status-shell-exec [[name {:keys [enabled command tasks]}]]
  ;; enable energy saving when the shell command failed (returned a
  ;; non-zero exit code), as this means that no interfering processes
  ;; were found; for example, "grep" and "pgrep" return a non-zero
  ;; exit code when they do not find matching lines or processes
  (let [save-energy? (when enabled
                       (-> (shell-exec command false)
                           (= :failed)))]
    (reduce (fn [m k] (assoc m k (when (get tasks k)
                                   save-energy?)))
            {:name name}
            defined-tasks)))


(defn update-energy-saving [task new-state]
  (let [timestamp (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
                     format
                     (java.time.LocalTime/now))
        prefs     (sp/select-one [:tasks task new-state] preferences)
        command   (sp/select-one [:command] prefs)
        fork?     (sp/select-one [:fork] prefs)
        message   (sp/select-one [:message] prefs)
        padding   "          "]
    (assert (some? new-state) "a NIL entered (update-energy-saving).")
    (when command
      (println)
      (printfln "[%s]  Task:    %s" timestamp (name task))
      (printfln "%s  State:   %s (%s)" padding (name new-state) message)
      (printfln "%s  Command: %s" padding command)
      ;; execute command
      (let [exit-state (shell-exec command fork?)]
        (printfln "%s  Result:  %s" padding (name exit-state))))))


(defn- enable-disable-or-nil? [coll]
  (cond
    ;; disabling energy saving has preference
    (some false? coll) :disable
    (some true? coll) :enable
    :else nil))


(defn update-status [_]
  (let [exit-states     (->> (sp/select-one [:watches] preferences)
                             (map status-shell-exec)
                             (remove nil?))
        new-task-states (reduce (fn [m k] (assoc m k (enable-disable-or-nil?
                                                       (map k exit-states))))
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


(defn -main
  [& _]
  (println)
  (print-header)

  (chime/chime-at (->> (sp/select-one [:probing-interval] preferences)
                       (java.time.Duration/ofSeconds)
                       (chime/periodic-seq (java.time.Instant/now)))
                  (fn [timestamp]
                    (let [actual-epoch (.toEpochMilli (java.time.Instant/now))
                          target-epoch (.toEpochMilli timestamp)
                          seconds-late (/ (- actual-epoch target-epoch)
                                          1000.0)]
                      ;; skip tasks that were scheduled for instants
                      ;; that were actually spent in computer Nirvana
                      (when (< seconds-late (preferences :probing-interval))
                        (update-status timestamp))))))
