(ns moccafaux.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [de.mzuther.moccafaux.core :as moccafaux]))


(deftest shell-exec
  (testing "non-forking"
    (testing "zero exit code"
      (is (= (:state (moccafaux/shell-exec "ls" false))
             :success)))

    (testing "non-zero exit code"
      (is (= (:state (moccafaux/shell-exec "this-is-not-a-command" false))
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
      (is (= (:state (moccafaux/shell-exec "ls" true))
             :failed)))))



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
