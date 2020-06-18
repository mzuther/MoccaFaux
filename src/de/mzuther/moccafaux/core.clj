(ns de.mzuther.moccafaux.core
  (:require [clojure.data.json :as json]
            [chime.core :as chime]
            [popen])
  (:gen-class))


(def default-settings {:probing-interval  60,
                       :monitor           [],

                       :sleep-enable-cmd  nil,
                       :sleep-disable-cmd nil,

                       :dpms-enable-cmd   nil,
                       :dpms-disable-cmd  nil})


;; force update on first call to "update-status"
(def status (ref {:sleep-keep-awake nil
                  :dpms-keep-awake  nil}))


(def settings
  (let [file-name     "moccafaux.json"
        user-settings (try (json/read-str (slurp file-name)
                                          :key-fn keyword)
                           (catch Exception _
                             (println (format "Warning: could not open \"%s\"."
                                              file-name))))]
    (merge default-settings
           user-settings)))


(defn shell-exec [command fork?]
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


(defn status-shell-exec [{:keys [name enabled command
                                 control-sleep control-dpms]}]
  ;; enable energy saving when the shell command returns a *non-zero*
  ;; exit code, as this means that no interfering processes were found
  ;; (for example, "grep" and "pgrep" return a *zero* exit code when
  ;; they find matching lines or processes)
  (let [enable-energy-saving? (when enabled
                                (zero? (shell-exec command false)))]
    {:name          name
     :disable-sleep (when control-sleep
                      enable-energy-saving?)
     :disable-dpms  (when control-dpms
                      enable-energy-saving?)}))


(defn update-energy-saving [section]
  (let [messages  {:sleep-enable-cmd  "allow computer to save energy"
                   :sleep-disable-cmd "keep computer awake"
                   :dpms-enable-cmd   "allow screen to save energy"
                   :dpms-disable-cmd  "keep screen awake"}
        timestamp (. (java.time.LocalTime/now) toString)

        awake-key  (keyword (str (name section)
                                 "-keep-awake"))
        keep-awake (get @status awake-key)

        command-key (keyword (str (name section)
                                  (if keep-awake "-disable-cmd" "-enable-cmd")))
        command     (get-in settings [command-key :command])
        fork?       (get-in settings [command-key :fork])]
    (when command
      (println)
      (println (format "[%s]  Change:  %s"
                       timestamp
                       (get messages command-key)))
      (println (format "                Command: %s"
                       command))
      (let [exit-code (shell-exec command fork?)]
        (println (format "                Result:  %s"
                         (condp = exit-code
                           0  "success"
                           -1 "forked"
                           "failed")))
        exit-code))))


(defn- true-false-or-nil? [coll]
  (cond
    (some true? coll) true
    (some false? coll) false
    :else nil))


(defn update-status []
  (let [results        (->> (get settings :monitor)
                            (map status-shell-exec)
                            (remove nil?))
        new-status     {:sleep-keep-awake (true-false-or-nil?
                                            (map :disable-sleep results))
                        :dpms-keep-awake  (true-false-or-nil?
                                            (map :disable-dpms results))}
        sleep-updated? (not= (get @status    :sleep-keep-awake)
                             (get new-status :sleep-keep-awake))
        dpms-updated?  (not= (get @status    :dpms-keep-awake)
                             (get new-status :dpms-keep-awake))]
    (dosync
      (ref-set status new-status))
    (when sleep-updated?
      (update-energy-saving :sleep))
    (when dpms-updated?
      (update-energy-saving :dpms))
    new-status))


(defn -main
  [& _]
  (chime/chime-at (->> (settings :probing-interval)
                       (java.time.Duration/ofSeconds)
                       (chime/periodic-seq (java.time.Instant/now)))
                  (fn [_]
                    (update-status))))
