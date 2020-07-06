;; resolve circular dependency
(ns de.mzuther.moccafaux.core)

(ns de.mzuther.moccafaux.spec
  (:require [de.mzuther.moccafaux.core]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]))


(s/def ::enabled
  boolean?)

(s/def ::interval
  (s/and
    int?
    pos?))

(s/def ::command
  (s/and
    string?
    #(pos? (count %))))

(s/def ::tasks
  (s/map-of keyword? boolean?))

(s/def ::task-states
  (s/coll-of (s/cat :name string?
                    :state (s/* ::task-state))))

(s/def ::exit-code
  #{:success :forked :failed})

(s/def ::task-state
  (s/nilable
    #{:enable :disable}))

(s/def ::task-state-coll
  (s/coll-of ::task-state))

(s/def ::watch
  ;; something seems to be broken in spec instrumentation
  map-entry?)

;; -----------------------------------------------------------------------------

(s/def ::shell-exec-fn
  (fn [{:keys [args ret]}]
    (if (get args :fork?)
      (contains? #{:forked :failed} ret)
      (contains? #{:success :failed} ret))))

(s/fdef de.mzuther.moccafaux.core/shell-exec
  :args (s/cat :command ::command
               :fork? boolean?)
  :ret  ::exit-code
  :fn   ::shell-exec-fn)

;; -----------------------------------------------------------------------------

(s/fdef de.mzuther.moccafaux.core/watch-exec
  :args (s/cat :watch ::watch)
  :ret  ::task-states)


(s/fdef de.mzuther.moccafaux.core/update-energy-saving
  :args (s/cat :task keyword?
               :state ::task-state)
  :ret  ::exit-code)


(s/fdef de.mzuther.moccafaux.core/enable-disable-or-nil?
  :args (s/cat :task-states ::task-state-coll)
  :ret  ::task-state)


(s/fdef de.mzuther.moccafaux.core/update-status
  :args (s/cat :timestamp-ignored any?)
  :ret  ::task-states)


(s/fdef de.mzuther.moccafaux.core/start-scheduler
  :args (s/cat :schedule-fn fn?
               :interval ::interval)
  :ret  (s/coll-of (s/cat :name string?
                          :state (s/* ::task-state))))

;; -----------------------------------------------------------------------------

(defn instrument-specs []
  (stest/instrument (stest/enumerate-namespace
                      'de.mzuther.moccafaux.core)))
