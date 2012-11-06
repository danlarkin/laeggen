(ns laeggen.test.core
  (:require [clojure.test :refer :all]
            [aleph.http :refer [sync-http-request websocket-client]]
            [laeggen.core :refer [start]]
            [laeggen.dispatch :as dispatch]
            [lamina.core :as lamina]))

(def test-server-port 8001)

(def default-spec {:port test-server-port
                   :websocket false
                   :append-slash false})

(defmacro with-server [spec & forms]
  `(let [stop-fn# (start (merge default-spec ~spec))]
     (try
       ~@forms
       (finally
         (stop-fn#)))))

(defn request [url]
  (sync-http-request
   {:method :get
    :url (str "http://localhost:" test-server-port url)
    :auto-transform true}))

(deftest test-basic-things-are-working
  (with-server {:urls (dispatch/urls
                       #"^/$" (fn [request]
                                "index")
                       #"^/quux$" (fn [request]
                                    (throw (Exception. "Quux!"))))}
    (is (= "index" (:body (request "/"))))
    (is (= 404 (:status (request "/missing"))))
    (let [req (request "/quux")]
      (is (= 500 (:status req)))
      (is (.contains (:body req) "Quux!")))))

(deftest test-default-handlers
  (let [response404 "this url is missing!"
        response500 "this url blew up!"]
    (with-server {:urls (dispatch/urls
                         :404 (fn [request]
                                {:status 404
                                 :headers {"content-type" "text/plain"}
                                 :body response404})
                         :500 (fn [request e]
                                {:status 500
                                 :headers {"content-type" "text/plain"}
                                 :body response500})
                         #"^/quux$" (fn [request]
                                      (throw (Exception. "Quux!"))))}
      (let [req (request "/")]
        (is (= 404 (:status req)))
        (is (= response404 (:body req))))
      (let [req (request "/quux")]
        (is (= 500 (:status req)))
        (is (= response500 (:body req)))))))

(deftest test-missing-slash-redirection
  (testing "redirects turned off"
    (with-server {:append-slash? false
                  :urls (dispatch/urls
                         #"^/foo$" (fn [request]
                                     "foo")
                         #"^/bar/$" (fn [request]
                                      "bar"))}
      (is (= "foo" (:body (request "/foo"))))
      (is (= 404 (:status (request "/bar"))))))
  (testing "redirects turned on"
    (with-server {:append-slash? true
                  :urls (dispatch/urls
                         #"^/foo/$" (fn [request]
                                      "foo"))}
      (let [req (request "/foo")]
        (is (= 302 (:status req)))
        (is (= "/foo/" (get-in req [:headers "location"])))))))

(deftest test-async
  (with-server {:websocket true
                :urls (dispatch/urls
                       #"^/$" (fn [request]
                                "index")
                       #"^/async/$"
                       ^:async (fn [request]
                                 (lamina/enqueue (:channel request) "hello")))}
    (is (= "index" (:body (request "/"))))
    (testing "connect a websocket to a normal url"
      (let [channel
            (lamina/wait-for-result
             (websocket-client
              {:url (str "ws://localhost:" test-server-port "/")})
             1000)]
        (try
          (Thread/sleep 100)
          (is (lamina/closed? channel))
          (finally
            (lamina/close channel)))))
    (testing "connect a websocket to an async url"
      (let [channel
            (lamina/wait-for-result
             (websocket-client
              {:url (str "ws://localhost:" test-server-port "/async/")})
             1000)]
        (try
          (is (not (lamina/closed? channel)))
          (is (= "hello" (lamina/wait-for-message channel 1000)))
          (finally
            (lamina/close channel)))))))
