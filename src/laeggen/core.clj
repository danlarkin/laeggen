(ns laeggen.core
  (:require [clojure.tools.logging :as log]
            [clojure.tools.namespace :refer [find-namespaces-on-classpath]]
            [aleph.formats :refer [bytes->string]]
            [aleph.http :refer [start-http-server]]
            [laeggen.dispatch :as dispatch]
            [laeggen.pages.default]
            [lamina.core :refer [enqueue]])
  (:import (java.net URLDecoder)))

(defn parse-query-string [qs]
  (delay
   (when qs
     (into {} (for [pair (.split qs "&")
                    :let [[k v] (.split pair "=")]]
                [(keyword k) (when v (URLDecoder/decode v))])))))

(defn main [{:keys [append-slash?]} channel request]
  (try
    (let [uri (:uri request)
          uri (URLDecoder/decode uri)
          qs (:query-string request)
          request (-> request
                      (update-in [:query-string] parse-query-string)
                      (update-in [:body]
                                 (comp parse-query-string bytes->string)))]
      (if (or (not append-slash?)
              (.endsWith uri "/"))
        (if-let [match-fn (dispatch/find-match-for uri)]
          (enqueue channel
                   (let [r (match-fn request)]
                     (if (map? r)
                       r
                       {:status 200
                        :headers {"content-type" "text/html"}
                        :body r})))
          (enqueue channel ((dispatch/find-match-for :404) request)))
        (enqueue channel
                 {:status 302
                  :headers {"location" (str uri "/" (when qs
                                                      (str "?" qs)))}})))
    (catch Exception e
      (enqueue channel ((dispatch/find-match-for :500) request e)))))

(defn REMOVETHIS [opts channel request]
  (#'main opts channel request))

(defn start [{:keys [port prefix] :as opts}]
  (doseq [page-ns (filter #(.startsWith (str %) prefix)
                          (find-namespaces-on-classpath))]
    (require page-ns :reload-all))
  (def stopfn (start-http-server (partial #'REMOVETHIS opts) {:port port}))
  (log/info "Starting Laeggen... done."))
