(ns clj-concordion.core-test
  (:require
    [clj-concordion.core :as conc]
    [clojure.test :refer :all]
    [io.aviso.repl :refer [install-pretty-exceptions]]))

(install-pretty-exceptions)

(defn ^Integer add
  "Called by Concordion"
  [^Integer n1, ^Integer n2]
  (int (+ n1 n2)))

(defn ^Integer subtract
  "Called by Concordion"
  [^Integer n1, ^Integer n2]
  (int (- n1 n2)))

(conc/deffixture
  "math.AdditionFixture"
  [add subtract])


