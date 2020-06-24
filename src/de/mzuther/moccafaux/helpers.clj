(ns de.mzuther.moccafaux.helpers
  (:require [clojure.string :as string]
            [trptcolin.versioneer.core :as version]))


(def page-width 80)
(def padding   "          ")


(defn printfln
  "Print formatted output, as per format, followed by (newline)."
  [fmt & args]
  (println (apply format fmt args)))


(defn fill-string
  "Create a string by n repetitions of ch.

  Return this string."
  [n ch]
  (->> ch
       (repeat n)
       (apply str)))


(defn get-timestamp
  "Get current local time.

  Return a string formatted as \"HH:mm:ss\"."
  []
  (. (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss")
     format
     (java.time.LocalTime/now)))


(defn add-borders
  "Create a string consisting of ch followed by s followed by ch."
  [s ch]
  (string/join [ch s ch]))


(defn print-line
  "Print a simple line of length \"page-width\" by repeating ch,
  followed by (newline).  If border-ch is given, change the first and
  last occurrence of ch to it."
  ([ch]
   (println (-> page-width
                (fill-string ch))))
  ([ch border-ch]
   (println (-> (- page-width 2)
                (fill-string ch)
                (add-borders border-ch)))))


(defn print-header
  "Print a nicely formatted header with application name and version
  number."
  []
  (let [raw-header   (str "MoccaFaux v" (version/get-version "de.mzuther"
                                                             "moccafaux.core"))
        header-width (count raw-header)
        left-margin  (quot (- page-width 2 header-width) 2)
        right-margin (- page-width 2 header-width left-margin)

        pre-header   (string/join [(fill-string left-margin \space)
                                   raw-header
                                   (fill-string right-margin \space)])
        full-header  (add-borders pre-header \|)]
    (println)
    (print-line \- \o)
    (println full-header)
    (print-line \- \o)))
