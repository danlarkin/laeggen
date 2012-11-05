(ns laeggen.core
  (:require [clojure.tools.logging :as log]
            [aleph.formats :refer [bytes->string]]
            [aleph.http :refer [start-http-server]]
            [laeggen.dispatch :as dispatch]
            [laeggen.views :as views]
            [lamina.core :refer [close enqueue]])
  (:import (java.net URLDecoder)))

(defn parse-query-string [qs]
  (delay
   (when qs
     (into {} (for [pair (.split qs "&")
                    :let [[k v] (.split pair "=")]]
                [(keyword k) (when v (URLDecoder/decode v))])))))

(defn main [{:keys [append-slash? urls]} channel request]
  (try
    (let [uri (URLDecoder/decode (:uri request))
          qs (:query-string request)
          request (-> request
                      (assoc :channel channel)
                      (update-in [:query-string] parse-query-string)
                      (update-in [:body] bytes->string))]
      (if (or (not append-slash?)
              (.endsWith uri "/"))
        (if-let [match-fn (dispatch/find-match urls uri)]
          (cond
           (:async (meta match-fn))
           (match-fn request)

           (:websocket? request)
           (close channel)

           :default
           (enqueue channel
                    (let [r (match-fn request)]
                      (cond
                       (map? r) r
                       (nil? r) ((dispatch/find-match urls :404) request)
                       :default {:status 200
                                 :headers {"content-type" "text/html"}
                                 :body r}))))
          (if (:websocket? request)
            (close channel)
            (enqueue channel ((dispatch/find-match urls :404) request))))
        (if (:websocket? request)
          (close channel)
          (enqueue channel
                   {:status 302
                    :headers {"location" (str uri "/" (when qs
                                                        (str "?" qs)))}}))))
    (catch Exception e
      (if (:websocket? request)
        (close channel)
        (enqueue channel ((dispatch/find-match urls :500) request e))))))

(defn start [{:keys [port urls websocket] :as opts}]
  (let [stop-fn (start-http-server
                 (partial main
                          (update-in opts [:urls]
                                     dispatch/merge-urls
                                     views/default-urls))
                 {:port port
                  :websocket websocket})]
    (log/info "Starting Laeggen... done.")
    stop-fn))
