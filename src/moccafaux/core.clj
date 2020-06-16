(ns moccafaux.core
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as string])
  (:gen-class))


(def default-settings
  {:probing-interval 60,
   :probing-priority 10,

   :monitor-processes  true,
   :monitor-pulseaudio false,
   :monitor-zfs-scrub  false,

   :monitor-processes-regex [],

   :dpms-enable  nil,
   :dpms-disable nil,

   :suspend-enable  nil,
   :suspend-disable nil })


(def settings
  (let [file-name      "moccafaux.json"
        user-settings  (try (json/read-str (slurp file-name)
                                           :key-fn keyword)
                            (catch Exception _
                              (printf (format "Warning: could not open \"%s\"."
                                              file-name))))
        added-defaults (merge default-settings user-settings)]
    (->> added-defaults
         :monitor-processes-regex
         (string/join "|")
         (format "pgrep -af '%s'")
         (assoc added-defaults :monitor-processes-cmd))))


(defn shell-exec-success? [shell-cmd pred-key]
  (when (settings pred-key)
    (->> shell-cmd
         (shell/sh "sh" "-c")
         :exit
         (= 0))))


(defn update-status []
  (let [processes?  (shell-exec-success?
                     (settings :monitor-processes-cmd)
                     :monitor-processes)
        pulseaudio? (shell-exec-success?
                     "pactl list short sinks | grep -E '\\bRUNNING$'"
                     :monitor-pulseaudio)
        zfs-scrub?  (shell-exec-success?
                     "zpool status | grep -E '(resilver|scrub) in progress'"
                     :monitor-zfs-scrub)
        new-status  {:processes        processes?
                     :pulseaudio       pulseaudio?
                     :zfs-scrub        zfs-scrub?
                     :dpms-disabled    (boolean pulseaudio?)
                     :suspend-disabled (boolean (or processes?
                                                    pulseaudio?
                                                    zfs-scrub?))}]
    new-status))


(defn -main
  [& _]
  (println (update-status))
  ;; finish threads in pool (see https://stackoverflow.com/a/33357417)
  ;; and https://clojuredocs.org/clojure.java.shell/sh)
  (shutdown-agents))
