(ns ^:no-doc clj-concordion.internal.utils)

(defn arg->type [arg]
  {:pre [(symbol? arg)]}
  (or
    (-> arg meta :tag)
    String))

(defn var->meta [v]
  {:pre [(var? v)]}
  (let [{:keys [tag arglists name]} (meta v)
        arglist (first arglists)]
    (when (> (count arglists) 1)
      (throw (ex-info "Multiple arity functions are not supported (yet?)"
                      {:fn v})))
    {:tag tag, :arglist arglist :name name}))

(defn var->method-descr
  "Get arg and return types of the given fn; v is a var
  Ex.: `(fn->types `add)` =>\n   {:namesym add :args [Integer Integer], :ret java.lang.Integer}"
  [v]
  {:pre [(var? v)]}
  (let [{:keys [tag arglist name]} (var->meta v)]
    {:namesym name
     :args    (mapv arg->type arglist)
     :ret     (or tag String)}))

(defn var->arglist [v]
  (:arglist
    (var->meta v)))

(defn ->defn [prefix v]
  (let [fname (->> v meta :name (str prefix) symbol)
        arglist-fn (var->arglist v)
        arglist+this (vec (cons '_ arglist-fn))]
    `(defn ~fname ~arglist+this
       (~v ~@arglist-fn))))