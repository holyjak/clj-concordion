(ns clj-concordion.internal.interop-test
  (:require
    [clj-concordion.internal.interop :refer :all]
    [clojure.test :refer :all]))

(deftest parse-expr-data-test
  (is (= {:single-var 'xx}
         (parse-expr-data (-> "#xx" expr->edn edn->data))))
  (is (= '{:function myfn, :arguments ([:variable v1] [:number 42] [:boolean true]), :set-var nil}
         (parse-expr-data (-> "myfn(#v1, 42, true)" expr->edn edn->data))))
  (is (= '{:function add, :arguments ([:number 1] [:number 2]), :set-var res}
         (parse-expr-data (-> "#res = add(1, 2)" expr->edn edn->data)))))