(ns laeggen.dispatch)

(defn url-matches? [uri [pattern page-fn]]
  (when-let [match (re-find pattern uri)]
    (if (coll? match)
      (fn [request]
        (apply page-fn request (rest match)))
      page-fn)))

;; TODO: memoize this in production somehow
(defn find-match [urls uri]
  (if (keyword? uri)
    ((:specials urls) uri)
    (->> (:patterns urls)
         (map (partial url-matches? uri))
         (remove nil?)
         first)))

(defn urls [& patterns]
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
      {:patterns urls
       :specials (into {} (map vec specials))}
      (throw (Exception. "patterns are malformed")))))

(defn merge-urls [& [urls & more-urls]]
  (reduce
   (fn [urls new-urls]
     {:patterns (concat (:patterns new-urls) (:patterns urls))
      :specials (merge (:specials urls) (:specials new-urls))})
   urls
   more-urls))
