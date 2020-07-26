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
  (testing "no-task"
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



  (testing "one-task"
    (let [task-names     [:first]
          states-nil     (moccafaux/make-task-states :test [(moccafaux/make-task-state :first nil)])
          states-disable (moccafaux/make-task-states :test [(moccafaux/make-task-state :first :disable)])
          states-enable  (moccafaux/make-task-states :test [(moccafaux/make-task-state :first :enable)])]

      (testing "disabled-task"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled false,
                                       :command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "enabled-task-explicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "enabled-task-implicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "no-assigned-task-explicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first false}}])
               states-nil)))

      (testing "no-assigned-task-implicit"
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
               states-disable)))

      (testing "non-zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:first true}}])
               states-enable)))))

  (testing "two-tasks"
    (let [task-names     [:first :second]
          states-nil     (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  nil)
                                                            (moccafaux/make-task-state :second nil)])
          states-disable (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  :disable)
                                                            (moccafaux/make-task-state :second :disable)])
          states-enable  (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  :enable)
                                                            (moccafaux/make-task-state :second :enable)])]

      (testing "disabled-task"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled false,
                                       :command "true",
                                       :tasks   {:first  false
                                                 :second false}}])
               states-nil)))

      (testing "no-assigned-task"
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
               states-disable)))

      (testing "non-zero exit code"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:first  true
                                                 :second true}}])
               states-enable)))))



  (testing "one-of-two-tasks"
    (let [task-names     [:first :second]
          states-disable (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  :disable)
                                                            (moccafaux/make-task-state :second nil)])
          states-enable  (moccafaux/make-task-states :test [(moccafaux/make-task-state :first  nil)
                                                            (moccafaux/make-task-state :second :enable)])
          ]

      (testing "disable-task-explicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first true
                                                 :second  false}}])
               states-disable)))

      (testing "disable-task-implicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "true",
                                       :tasks   {:first true}}])
               states-disable)))

      (testing "enable-task-explicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:first  false
                                                 :second true}}])
               states-enable)))

      (testing "enable-task-implicit"
        (is (= (moccafaux/watch-exec task-names
                                     [:test
                                      {:enabled true,
                                       :command "false",
                                       :tasks   {:second true}}])
               states-enable))))))



(deftest enable-disable-or-nil?
  (testing "nil"
    (testing "empty vector"
      (is (= (moccafaux/enable-disable-or-nil? [])
             nil)))

    (testing "single nil"
      (is (= (moccafaux/enable-disable-or-nil? [nil])
             nil)))

    (testing "multiple nil"
      (is (= (moccafaux/enable-disable-or-nil? [nil nil nil nil nil nil])
             nil))))



  (testing ":disable"
    (testing "single :disable"
      (is (= (moccafaux/enable-disable-or-nil? [:disable])
             :disable)))

    (testing "multiple :disable"
      (is (= (moccafaux/enable-disable-or-nil? [:disable :disable :disable :disable])
             :disable)))

    (testing "mixed vector"
      (is (= (moccafaux/enable-disable-or-nil? [nil 1 2 0 :enable :disable])
             :disable))))



  (testing ":enable"
    (testing "single :enable"
      (is (= (moccafaux/enable-disable-or-nil? [:enable])
             :enable)))

    (testing "single string \"disable\""
      (is (= (moccafaux/enable-disable-or-nil? ["disable"])
             :enable)))

    (testing "unrelated scalar value"
      (is (= (moccafaux/enable-disable-or-nil? [1])
             :enable)))

    (testing "mixed vector with string \"disable\""
      (is (= (moccafaux/enable-disable-or-nil? [nil 1 2 0 :enable "disable"])
             :enable)))))
