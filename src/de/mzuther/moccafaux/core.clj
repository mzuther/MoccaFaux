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
(def task-state (ref {}))


(def preferences
  (let [file-name        (io/file (System/getProperty "user.home")
                                  ".config" "moccafaux" "config.json")
        user-preferences (try (json/read-str (slurp file-name)
                                             :key-fn keyword)
                              (catch Exception e
                                (newline)
                                (printfln "WARNING: could not open \"%s\":"
                                          file-name)
                                (println "        " (.getMessage e))))]
    (merge default-preferences
           user-preferences)))


(defn- shell-exec [command fork?]
  (let [new-process (popen/popen ["sh" "-c" command ])]
    (if fork?
      (do
        ;; wait for 10 ms to check whether the process is actually
        ;; created and running
        (Thread/sleep 10)
        (if (popen/running? new-process)
          -1
          1))
      (popen/exit-code new-process))))


(defn- status-shell-exec [[name {:keys [enabled command tasks]}]]
  ;; enable energy saving when the shell command returns a *non-zero*
  ;; exit code, as this means that no interfering processes were found
  ;; (for example, "grep" and "pgrep" return a *zero* exit code when
  ;; they find matching lines or processes)
  (let [save-energy? (when enabled
                       (-> (shell-exec command false)
                           zero?
                           not))]
    {:name          name
     :disable-sleep (when (:sleep tasks)
                      save-energy?)
     :disable-dpms  (when (:dpms tasks)
                      save-energy?)}))


(defn update-energy-saving [task]
  (let [timestamp (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
                     format
                     (java.time.LocalTime/now))
        new-state (sp/select-one [task] @task-state)
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
      (let [exit-code (shell-exec command fork?)]
        (printfln "%s  Result:  %s"
                  padding
                  (condp = exit-code
                    0  "success"
                    -1 "forked"
                    "failed"))
        exit-code))))


(defn- enable-disable-or-nil? [coll]
  (cond
    ;; disabling energy saving has preference
    (some false? coll) :disable
    (some true? coll) :enable
    :else nil))


(defn update-status [_]
  (let [results        (->> (sp/select-one [:watches] preferences)
                            (map status-shell-exec)
                            (remove nil?))
        new-task-state {:sleep (enable-disable-or-nil?
                                 (map :disable-sleep results))
                        :dpms  (enable-disable-or-nil?
                                 (map :disable-dpms results))}
        sleep-updated? (not= (sp/select-one [:sleep] @task-state)
                             (sp/select-one [:sleep] new-task-state))
        dpms-updated?  (not= (sp/select-one [:dpms] @task-state)
                             (sp/select-one [:dpms] new-task-state))]
    (dosync
      (ref-set task-state new-task-state))
    (when sleep-updated?
      (update-energy-saving :sleep))
    (when dpms-updated?
      (update-energy-saving :dpms))
    new-task-state))


(defn -main
  [& _]
  (println)
  (print-header)

  (println)
  (printfln "[%s]  Skipping one time interval ..."
            (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
               format
               (java.time.LocalTime/now)))

  (chime/chime-at (->> (sp/select-one [:probing-interval] preferences)
                       (java.time.Duration/ofSeconds)
                       (chime/periodic-seq (java.time.Instant/now))
                       (rest))
                  (fn [timestamp]
                    (let [actual-epoch (.toEpochMilli (java.time.Instant/now))
                          target-epoch (.toEpochMilli timestamp)
                          seconds-late (/ (- actual-epoch target-epoch)
                                          1000.0)]
                      ;; skip tasks that were scheduled for instants
                      ;; that were actually spent in computer Nirvana
                      (when (< seconds-late (preferences :probing-interval))
                        (update-status timestamp))))))
