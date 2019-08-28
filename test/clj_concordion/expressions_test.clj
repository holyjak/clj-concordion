(ns clj-concordion.expressions-test
  (:require
    [clojure.test :refer :all]
    [clj-concordion.core :refer [deffixture]]))

(defn hello [] "hello")

(defn getNil [] nil)

(defn toString [x] (str x))

(defn prStr [x] (pr-str x))

(defn sum [& nums] (apply + nums))

(defn eq [& exps] (apply = exps))

(defrecord Employee [name age kid])
(defrecord Kid [name])

(deffixture Expressions)