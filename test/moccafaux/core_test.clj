(ns moccafaux.core-test
  (:require [clojure.test :refer :all]
            [de.mzuther.moccafaux.core :refer :all]))


(deftest test-enable-disable-or-nil
  (testing "FIXME, I fail."
    ;; empty vector
    (is (= (enable-disable-or-nil? [])
           nil))
    ;; vector of nils
    (is (= (enable-disable-or-nil? [nil nil nil nil nil nil])
           nil))
    ;; vector with single :disable
    (is (= (enable-disable-or-nil? [:disable])
           :disable))
    ;; vector with multiple :disable
    (is (= (enable-disable-or-nil? [:disable :disable :disable :disable])
           :disable))
    ;; mixed vector with :disable
    (is (= (enable-disable-or-nil? [nil 1 2 0 :enable :disable])
           :disable))
    ;; vector with single :enable
    (is (= (enable-disable-or-nil? [:enable])
           :enable))
    ;; "disable" string instead of keyword
    (is (= (enable-disable-or-nil? ["disable"])
           :enable))
    ;; vector with inrelated scalar value
    (is (= (enable-disable-or-nil? [1])
           :enable))
    ;; mixed vector with "disable" string instead of keyword
    (is (= (enable-disable-or-nil? [nil 1 2 0 :enable "disable"])
           :enable))))
