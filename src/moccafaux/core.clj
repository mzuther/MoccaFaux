(ns moccafaux.core
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as string]
            [chime.core :as chime])
  (:gen-class))


(def default-settings
  {:probing-interval 60,

   :monitor-processes  true,
   :monitor-pulseaudio false,
   :monitor-zfs-scrub  false,

   :monitor-processes-regex [],

   :dpms-enable  nil,
   :dpms-disable nil,

   :sleep-enable  nil,
   :sleep-disable nil })


;; force update on status
(def status
  (ref (zipmap [:processes :pulseaudio :zfs-scrub
                :dpms-disabled :sleep-disabled]
               (repeat nil))))


(def settings
  (let [file-name      "moccafaux.json"
        user-settings  (try (json/read-str (slurp file-name)
                                           :key-fn keyword)
                            (catch Exception _
                              (println (format "Warning: could not open \"%s\"."
                                               file-name))))
        added-defaults (merge default-settings user-settings)]
    (->> added-defaults
         :monitor-processes-regex
         (string/join "|")
         (format "pgrep -af '%s'")
         (assoc added-defaults :monitor-processes-cmd))))


(defn shell-exec [command]
  (->> command
       (shell/sh "sh" "-c")
       :exit))


(defn shell-exec-success? [command pred-key]
  (if (settings pred-key)
    (->> (shell-exec command)
         (= 0))
    (println (format "Key %s not found in settings."
                     pred-key))))


(defn get-new-status []
  (let [processes?  (shell-exec-success?
                      (settings :monitor-processes-cmd)
                      :monitor-processes)
        pulseaudio? (shell-exec-success?
                      "pactl list short sinks | grep -E '\\bRUNNING$'"
                      :monitor-pulseaudio)
        zfs-scrub?  (shell-exec-success?
                      "zpool status | grep -E '(resilver|scrub) in progress'"
                      :monitor-zfs-scrub)
        new-status  {:processes      processes?
                     :pulseaudio     pulseaudio?
                     :zfs-scrub      zfs-scrub?
                     :dpms-disabled  (boolean pulseaudio?)
                     :sleep-disabled (boolean (or processes?
                                                  pulseaudio?
                                                  zfs-scrub?))}]
    new-status))


(defn update-power-management [selector]
  (let [message {:dpms-enable   "Enabling DPMS ..... "
                 :dpms-disable  "Disabling DPMS .... "
                 :sleep-enable  "Enabling sleep .... "
                 :sleep-disable "Disabling sleep ... "}
        command (get settings selector)]
    (when command
      (print (message selector))
      (if (shell-exec command)
        (println "ok")
        (println "failed")))))


(defn update-status []
  (let [new-status     (get-new-status)
        dpms-updated?  (not= (get @status :dpms-disabled)
                             (get new-status :dpms-disabled))
        sleep-updated? (not= (get @status :sleep-disabled)
                             (get new-status :sleep-disabled))]
    (dosync
      (ref-set status
               new-status))
    (when sleep-updated?
      (if (get @status :sleep-disabled)
        (update-power-management :sleep-disable)
        (update-power-management :sleep-enable)))
    (when dpms-updated?
      (if (get @status :dpms-disabled)
        (update-power-management :dpms-disable)
        (update-power-management :dpms-enable)))
    new-status))


(defn -main
  [& _]
  (chime/chime-at (->> (settings :probing-interval)
                       (java.time.Duration/ofSeconds)
                       (chime/periodic-seq (java.time.Instant/now)))
                  (fn [_]
                    (update-status))))
