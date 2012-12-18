(ns laeggen.auth
  (:import (java.util UUID)))

(defonce auth-map (atom {}))

(defn parse-cookies [request]
  (when-let [cookies (get-in request [:headers "cookie"])]
    (into {} (map #(vec (map (memfn trim) (.split % "=")))
                  (.split cookies ";")))))

(defn authorize! [laeggen-id id]
  (swap! auth-map assoc laeggen-id id))

(defn deauthorize! [request]
  (swap! auth-map dissoc (get (parse-cookies request) "laeggen-id")))

(defn authorized? [request]
  (when-let [cookie-map (parse-cookies request)]
    (@auth-map (cookie-map "laeggen-id"))))

(defn authorize-and-forward! [id forward-uri]
  (let [laeggen-id (str (UUID/randomUUID))]
    (authorize! laeggen-id id)
    {:status 302
     :headers {"location" forward-uri
               "set-cookie" (str "laeggen-id=" laeggen-id
                                 "; Path=/")}}))

(defn deauthorize-and-forward! [request forward-uri]
  (deauthorize! request)
  {:status 302
   :headers {"location" forward-uri
             "set-cookie" (str "laeggen-id=EALOD;"
                               " Path=/;"
                               " Expires=Wed, 1-Jan-1970 22:23:01 GMT")}})

;; helpers

(defn authorization-required [forward-uri f]
  (with-meta
    (fn [request & args]
      (println "for websocket check:" (:websocket request) " authorized:" (authorized? request))
      (if-let [laeggen-id-value (authorized? request)]
        (apply f (assoc request :laeggen-id-value laeggen-id-value) args)
        {:status 302
         :headers {"location" forward-uri}}))
    (meta f)))
