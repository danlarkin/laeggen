(ns laeggen.dispatch)

(defonce url-patterns
  (atom (sorted-map-by (fn [r s] (compare (str r) (str s))))))

(defonce specials (atom {}))

(defn url-matches? [uri [pattern page-fn]]
  (when-let [match (re-find pattern uri)]
    (if (coll? match)
      (fn [request]
        (apply page-fn request (rest match)))
      page-fn)))

(defmacro defpage [regex-or-special & fn-tail]
  `(swap! (if (keyword? ~regex-or-special)
            specials
            url-patterns)
          assoc ~regex-or-special (fn ~@fn-tail)))

(defn find-match-for [uri]
  (if (keyword? uri)
    (@specials uri)
    (->> @url-patterns
         (pmap (partial url-matches? uri))
         (remove nil?)
         first)))
