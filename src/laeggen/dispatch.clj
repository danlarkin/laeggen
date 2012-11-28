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

(defn breakout-groups [urls]
  (reduce
   (fn [acc [pattern f]]
     (if (coll? pattern)
       (vec (concat acc
                    (for [p pattern]
                      [p f])))
       (conj acc [pattern f])))
   []
   urls))

(defn urls [& patterns]
  (let [{specials true
         urls false} (group-by (comp keyword? first)
                               (partition 2 2 patterns))]
    (if (even? (count patterns))
      (if (every? keyword? (map first specials))
        (if (every? #(or (instance? java.util.regex.Pattern %)
                         (and (coll? %)
                              (every?
                               (partial instance? java.util.regex.Pattern) %)))
                    (map first urls))
          (if (every? #(or (fn? %)
                           (and (var? %)
                                (fn? @%)))
                      (map second (concat specials urls)))
            {:patterns (breakout-groups urls)
             :specials (into {} (map vec specials))}
            (throw (Exception. (str "patterns are malformed: "
                                    "targets should be functions or vars"))))
          (throw (Exception. (str "patterns are malformed: "
                                  "patterns should be regular expressions or "
                                  "collections of regular expressions"))))
        (throw (Exception. (str "patterns are malformed: "
                                "specials should be keywords"))))
      (throw (Exception. (str "patterns are malformed: "
                              "odd count in patterns definition"))))))

(defn merge-urls [& [urls & more-urls]]
  (reduce
   (fn [urls new-urls]
     {:patterns (concat (:patterns new-urls) (:patterns urls))
      :specials (merge (:specials urls) (:specials new-urls))})
   urls
   more-urls))

;; urls helpers

(defn redirect-to [url]
  (fn [request]
    {:status 301
     :headers {"location" url}}))
