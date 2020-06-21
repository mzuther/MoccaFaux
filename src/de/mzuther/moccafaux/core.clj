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
  (let [file-name     (io/file (System/getProperty "user.home")
                               ".config" "moccafaux" "config.json")
        user-settings (try (json/read-str (slurp file-name)
                                          :key-fn keyword)
                           (catch Exception _
                             (newline)
                             (printfln "WARNING: could not open \"%s\"."
                                       file-name)))]
    (merge default-settings
           user-settings)))


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


(defn- status-shell-exec [{:keys [name enabled command
                                  tasks]}]
  ;; enable energy saving when the shell command returns a *non-zero*
  ;; exit code, as this means that no interfering processes were found
  ;; (for example, "grep" and "pgrep" return a *zero* exit code when
  ;; they find matching lines or processes)
  (let [enable-energy-saving? (when enabled
                                (zero? (shell-exec command false)))]
    {:name          name
     :disable-sleep (when (:sleep tasks)
                      enable-energy-saving?)
     :disable-dpms  (when (:dpms tasks)
                      enable-energy-saving?)}))


(defn update-energy-saving [section]
  (let [messages    {:sleep-enable-cmd  "allow computer to save energy"
                     :sleep-disable-cmd "keep computer awake"
                     :dpms-enable-cmd   "allow screen to save energy"
                     :dpms-disable-cmd  "keep screen awake"}
        timestamp   (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
                       format
                       (java.time.LocalTime/now))

        awake-key   (keyword (str (name section)
                                  "-keep-awake"))
        keep-awake  (sp/select-one [awake-key] @status)

        command-key (keyword (str (name section)
                                  (if keep-awake "-disable-cmd" "-enable-cmd")))
        command     (sp/select-one [command-key :command] settings)
        fork?       (sp/select-one [command-key :fork] settings)]
    (when command
      (println)
      (printfln "[%s]  Change:  %s"
                timestamp
                (sp/select-one [command-key] messages))
      (printfln "            Command: %s"
                command)
      (let [exit-code (shell-exec command fork?)]
        (printfln "            Result:  %s"
                  (condp = exit-code
                    0  "success"
                    -1 "forked"
                    "failed"))
        exit-code))))


(defn- true-false-or-nil? [coll]
  (cond
    (some true? coll) true
    (some false? coll) false
    :else nil))


(defn update-status [_]
  (let [results        (->> (sp/select-one [:monitor] settings)
                            (map status-shell-exec)
                            (remove nil?))
        new-status     {:sleep-keep-awake (true-false-or-nil?
                                            (map :disable-sleep results))
                        :dpms-keep-awake  (true-false-or-nil?
                                            (map :disable-dpms results))}
        sleep-updated? (not= (sp/select-one [:sleep-keep-awake] @status)
                             (sp/select-one [:sleep-keep-awake] new-status))
        dpms-updated?  (not= (sp/select-one [:dpms-keep-awake] @status)
                             (sp/select-one [:dpms-keep-awake] new-status))]
    (dosync
      (ref-set status new-status))
    (when sleep-updated?
      (update-energy-saving :sleep))
    (when dpms-updated?
      (update-energy-saving :dpms))
    new-status))


(defn -main
  [& _]
  (println)
  (print-header)

  (println)
  (printfln "[%s]  Skipping one time interval ..."
            (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
               format
               (java.time.LocalTime/now)))

  (chime/chime-at (->> (sp/select-one [:probing-interval] settings)
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
                      (when (< seconds-late (settings :probing-interval))
                        (update-status timestamp))))))
