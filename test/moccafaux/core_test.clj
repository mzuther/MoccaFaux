;; MoccaFaux
;; =========
;; Adapt power management to changes in the environment
;;
;; Copyright (c) 2020-2021 Martin Zuther (http://www.mzuther.de/) and
;; contributors
;;
;; This program and the accompanying materials are made available under
;; the terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0.
;;
;; This Source Code may also be made available under the following
;; Secondary Licenses when the conditions for such availability set forth
;; in the Eclipse Public License, v. 2.0 are satisfied: GNU General
;; Public License as published by the Free Software Foundation, either
;; version 2 of the License, or (at your option) any later version, with
;; the GNU Classpath Exception which is available at
;; https://www.gnu.org/software/classpath/license.html.


(ns moccafaux.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [de.mzuther.moccafaux.core :as moccafaux]))


(deftest shell-exec
  (testing "non-forking"
    (testing "zero exit code"
      (is (= (:state (moccafaux/shell-exec "true" false))
             :success)))

    (testing "non-zero exit code"
      (is (= (:state (moccafaux/shell-exec "false" false))
             :failed)))

    (testing "empty command"
      (is (thrown? AssertionError
                   (moccafaux/shell-exec "" false)))))



  (testing "forking"
    (testing "fork is running"
      (is (= (:state (moccafaux/shell-exec "sleep 1" true))
             :forked)))

    (testing "fork exited early"
      (is (= (:state (moccafaux/shell-exec "sleep 0.002" true))
             :failed)))

    (testing "could not fork"
      (is (= (:state (moccafaux/shell-exec "true" true))
             :failed)))))



(deftest watch-exec
  (testing "no task"
    (let [task-names   nil
          states-empty (moccafaux/make-task-states :test [])]

      (testing "non-zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {}}])
               states-empty)))

      (testing "zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {}}])
               states-empty)))))



  (testing "one task"
    (let [task-names    [:first]
          states-nil    (moccafaux/make-task-states :test [(moccafaux/make-task-state :first nil)])
          states-idle   (moccafaux/make-task-states :test [(moccafaux/make-task-state :first :idle)])
          states-active (moccafaux/make-task-states :test [(moccafaux/make-task-state :first :active)])]

      (testing "disabled task (explicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled false,
                                       :command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "enabled task (explicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "enabled task (implicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "no assigned task (explicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "no assigned task (implicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true"}])
               states-nil)))

      (testing "zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first true}}])
               states-idle)))

      (testing "non-zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:first true}}])
               states-active)))))

  (testing "two tasks"
    (let [task-names    [:first :second]
          states-nil    (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  nil)
                                                           (moccafaux/make-task-state :second nil)])
          states-idle   (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  :idle)
                                                           (moccafaux/make-task-state :second :idle)])
          states-active (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  :active)
                                                           (moccafaux/make-task-state :second :active)])]

      (testing "disabled task (explicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled false,
                                       :command "true",
                                       :tasks   {:first  false
                                                 :second false}}])
               states-nil)))

      (testing "no assigned task"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first  false
                                                 :second false}}])
               states-nil)))

      (testing "zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first  true
                                                 :second true}}])
               states-idle)))

      (testing "non-zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:first  true
                                                 :second true}}])
               states-active)))))



  (testing "one of two tasks"
    (let [task-names    [:first :second]
          states-idle   (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  :idle)
                                                           (moccafaux/make-task-state :second nil)])
          states-active (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  nil)
                                                           (moccafaux/make-task-state :second :active)])]

      (testing "idle task (explicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first  true
                                                 :second false}}])
               states-idle)))

      (testing "idle task (implicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first true}}])
               states-idle)))

      (testing "active task (explicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:first  false
                                                 :second true}}])
               states-active)))

      (testing "active task (implicit)"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:second true}}])
               states-active))))))



(deftest update-energy-saving
  (testing "nil"
    (testing "throws exception?"
      (is (thrown? IllegalArgumentException
                   (moccafaux/update-energy-saving nil))))))



(deftest active-idle-or-nil?
  (testing "nil"
    (testing "empty vector"
      (is (= (moccafaux/active-idle-or-nil? [])
             nil)))

    (testing "single nil"
      (is (= (moccafaux/active-idle-or-nil? [nil])
             nil)))

    (testing "multiple nil"
      (is (= (moccafaux/active-idle-or-nil? [nil nil nil nil nil nil])
             nil))))



  (testing ":idle"
    (testing "single :idle"
      (is (= (moccafaux/active-idle-or-nil? [:idle])
             :idle)))

    (testing "multiple :idle"
      (is (= (moccafaux/active-idle-or-nil? [:idle :idle :idle :idle])
             :idle)))

    (testing "mixed vector"
      (is (= (moccafaux/active-idle-or-nil? [nil 1 2 0 :active :idle])
             :idle))))



  (testing ":active"
    (testing "single :active"
      (is (= (moccafaux/active-idle-or-nil? [:active])
             :active)))

    (testing "single string \"idle\""
      (is (= (moccafaux/active-idle-or-nil? ["idle"])
             :active)))

    (testing "unrelated scalar value"
      (is (= (moccafaux/active-idle-or-nil? [1])
             :active)))

    (testing "mixed vector with string \"idle\""
      (is (= (moccafaux/active-idle-or-nil? [nil 1 2 0 :active "idle"])
             :active)))))



(deftest poll-task-states
  (testing "no task"
    (let [task-names []]

      (testing "assigned to non-existing task"
        (let [watches [[:one-idle {:enabled true
                                   :command "true"
                                   :tasks   {:first true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {})))) ))


  (testing "one task"
    (let [task-names [:first]]

      (testing "watch is disabled"
        (let [watches [[:one-nil {:enabled false
                                  :command ""
                                  :tasks   {:first true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first nil}))))

      (testing "watch is running"
        (let [watches [[:one-idle {:enabled true
                                   :command "true"
                                   :tasks   {:first true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first :idle}))))

      (testing "watch is not running"
        (let [watches [[:one-active {:enabled true
                                     :command "false"
                                     :tasks   {:first true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first :active}))))))



  (testing "two tasks"
    (let [task-names [:first :second]]

      (testing "both disabled"
        (let [watches [[:first-active {:enabled false
                                       :command ""
                                       :tasks   {:first true}}]
                       [:second-nil   {:enabled false
                                       :command ""
                                       :tasks   {:second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  nil
                  :second nil}))))

      (testing "both running"
        (let [watches [[:first-active {:enabled true
                                       :command "true"
                                       :tasks   {:first true}}]
                       [:second-nil   {:enabled true
                                       :command "true"
                                       :tasks   {:second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :idle
                  :second :idle}))))

      (testing "both not running"
        (let [watches [[:first-active {:enabled true
                                       :command "false"
                                       :tasks   {:first true}}]
                       [:second-nil   {:enabled true
                                       :command "false"
                                       :tasks   {:second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :active
                  :second :active}))))

      (testing "first not running, second disabled"
        (let [watches [[:first-active {:enabled true
                                       :command "false"
                                       :tasks   {:first true}}]
                       [:second-nil   {:enabled false
                                       :command ""
                                       :tasks   {:first true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :active
                  :second nil}))))

      (testing "first not running, second running"
        (let [watches [[:first-active {:enabled true
                                       :command "false"
                                       :tasks   {:first true}}]
                       [:second-idle  {:enabled true
                                       :command "true"
                                       :tasks   {:second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :active
                  :second :idle}))))

      (testing "combined watch (disabled)"
        (let [watches [[:combined-active {:enabled false
                                          :command ""
                                          :tasks   {:first  true
                                                    :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  nil
                  :second nil}))))

      (testing "combined watch (running)"
        (let [watches [[:combined-active {:enabled true
                                          :command "true"
                                          :tasks   {:first  true
                                                    :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :idle
                  :second :idle}))))

      (testing "combined watch (not running)"
        (let [watches [[:combined-active {:enabled true
                                          :command "false"
                                          :tasks   {:first  true
                                                    :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :active
                  :second :active}))))))



  (testing "multiple tasks & watches"
    (let [task-names [:first :second]]
      (testing "both running (all watches enabled)"
        (let [watches [[:first-idle  {:enabled true
                                      :command "true"
                                      :tasks   {:first true}}]
                       [:second-idle {:enabled true
                                      :command "true"
                                      :tasks   {:second true}}]
                       [:both-active {:enabled true,
                                      :command "true"
                                      :tasks   {:first  true
                                                :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :idle
                  :second :idle}))))

      (testing "both running (one watch disabled)"
        (let [watches [[:first-idle  {:enabled true
                                      :command "true"
                                      :tasks   {:first true}}]
                       [:second-idle {:enabled true
                                      :command "true"
                                      :tasks   {:second true}}]
                       [:both-nil    {:enabled false,
                                      :command ""
                                      :tasks   {:first  true
                                                :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :idle
                  :second :idle}))))

      (testing "both not running (combined watch)"
        (let [watches [[:first-active  {:enabled true
                                        :command "false"
                                        :tasks   {:first true}}]
                       [:second-active {:enabled true
                                        :command "false"
                                        :tasks   {:second true}}]
                       [:both-idle     {:enabled true,
                                        :command "true"
                                        :tasks   {:first  true
                                                  :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :idle
                  :second :idle}))))

      (testing "both not running (separate watches)"
        (let [watches [[:first-idle  {:enabled true
                                      :command "true"
                                      :tasks   {:first true}}]
                       [:second-idle {:enabled true
                                      :command "true"
                                      :tasks   {:second true}}]
                       [:both-active {:enabled true,
                                      :command "false"
                                      :tasks   {:first  true
                                                :second true}}]]]
          (is (= (moccafaux/poll-task-states task-names watches)
                 {:first  :idle
                  :second :idle})))))))
