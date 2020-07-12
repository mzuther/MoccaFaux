(ns de.mzuther.moccafaux.helpers
  (:require [clojure.string :as string]
            [trptcolin.versioneer.core :as version]))


(def page-width 80)
(def padding   "          ")


(defn get-timestamp
  "Get current local time.

  Return a string formatted as \"[HH:mm:ss]\"."
  []
  (->> (java.time.LocalTime/now)
       (.format (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss"))
       (format "[%s]")))


(defn fill-string
  "Create a string by n repetitions of ch.

  Return this string."
  [n ch]
  (->> ch
       (repeat n)
       (apply str)))


(defn add-borders
  "Create a string consisting of ch followed by s followed by ch."
  [s ch]
  (string/join [ch s ch]))


(defn printfln
  "Print formatted output, as per format, followed by (newline)."
  [fmt & args]
  (println (apply format fmt args)))


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
    (newline)
    (print-line \- \o)
    (println full-header)
    (print-line \- \o)))


(defn print-list
  "Print coll as a list with the first element prepended by
  padding-first (probably containing an annotation) and the remaining
  elements by padding-rest (usually just white space).  The respective
  padding and element will be spaced by sep and a single space. "
  [padding-first padding-rest sep coll]
  (let [paddings  (concat [padding-first]
                          (repeat padding-rest))
        separator (str sep " ")]
    ;; mapv is not lazy!
    (mapv #(println (string/join separator [%1 %2]))
          paddings
          coll)))


(defn print-settings
  "Print settings using a nice layout."
  [interval task-names watch-names]
  (let [padding-tasks   (format "%s  Tasks:    " padding)
        padding-watches (format "%s  Watches:  " padding)
        padding-rest    (format "%s            " padding)]
    (newline)
    (printfln "%s  Probe:    every %s seconds"
              (get-timestamp)
              interval)
    (newline)
    (print-list padding-tasks padding-rest "-"
                (map name task-names))
    (newline)
    (print-list padding-watches padding-rest "-"
                (map name watch-names))))


(defn exit-after-printing-help-and-errors
  "Print help, command line parsing errors (if any) and exit with
  given exit-code."
  [args exit-code]
  (let [{:keys [summary errors]} args]
    (when errors
      (newline)
      (doseq [error errors]
        (println "ERROR:" error)))

    (newline)
    (println "Usage: java -jar moccafaux.jar [OPTION...]")
    (newline)
    ;; display command line help
    (println summary)
    (newline)
    (flush)
    (System/exit exit-code)))
