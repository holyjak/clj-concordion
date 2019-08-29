(ns clj-concordion.features-test
  (:require
    [clojure.test :refer :all]
    [clj-concordion.core :refer [deffixture]]))

(defn return [x] x)

(deffixture Features)

(deffixture
  TrimWhitespace)

(deffixture
  NoTrimWhitespace
  {:cc/no-trim? true})

(deffixture
  EmptySpecNoWarning
  {:cc/no-asserts? true})