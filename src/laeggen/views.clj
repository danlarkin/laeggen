(ns laeggen.views
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-stack-trace]]
            [laeggen.dispatch :refer [urls]]))

(defn assets [request path]
  (let [path (if (.endsWith path "/")
               (subs path 0 (dec (count path)))
               path)
        body (io/as-file (io/resource (str "assets/" path)))]
    (when body
      (merge
       {:status 200
        :body body}
       (cond (.endsWith path ".css")
             {:headers {"content-type" "text/css"}}

             (.endsWith path ".png")
             {:headers {"content-type" "image/png"}})))))

(defn handler404 [request]
  {:status 404
   :headers {"content-type" "text/plain"}
   :body "404"})

(defn handler500 [request exception]
  {:status 500
   :headers {"content-type" "text/plain"}
   :body (with-out-str
           (print-stack-trace exception))})

(def default-urls
  (urls
   #"^/assets/(.*)$" assets
   :404 handler404
   :500 handler500))
