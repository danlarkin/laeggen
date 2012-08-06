(ns laeggen.dispatch)

(defonce url-patterns (atom ()))

(defonce url-specials (atom {}))

(defn url-matches? [uri [pattern page-fn]]
  (when-let [match (re-find pattern uri)]
    (if (coll? match)
      (fn [request]
        (apply page-fn request (rest match)))
      page-fn)))

;; TODO: memoize this in production somehow
(defn find-match-for [uri]
  (if (keyword? uri)
    (@url-specials uri)
    (->> @url-patterns
         (map (partial url-matches? uri))
         (remove nil?)
         first)))

(defn urls! [& patterns]
  (let [{specials true
         urls false} (group-by (comp keyword? first)
                               (partition 2 2 patterns))]
    (if (and (even? (count patterns))
             (every? keyword? (map first specials))
             (every? (partial instance? java.util.regex.Pattern)
                     (map first urls))
             (every? #(or (fn? %)
                          (and (var? %)
                               (fn? @%)))
                     (map second (concat specials urls))))
      (do
        (swap! url-patterns (partial concat urls))
        (swap! url-specials merge (into {} (map vec specials))))
      (throw (Exception. "patterns are malformed")))))
