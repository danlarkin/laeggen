# Laeggen

From the danish word for 'calf' (like your leg, not the cow).

## What is it?

Laeggen is a routing library written on top of
[Aleph](https://github.com/ztellman/aleph/) designed to be simple,
easy to use, and powerful (since you do everything yourself).

## Usage

```clojure
[laeggen "0.3"]

;; In your project:
(ns foo.bar
  (:require [laeggen.core :as laeggen]
            [laeggen.dispatch :as dispatch]))
```

Laeggen uses a list of regular expressions mapped to clojure functions
to determine where a request is handled. Each handler function takes
_at least one_ argument, the request. If the regular expression for
routing has matching groups, those are mapped into additional
arguments to the handler.

For example:

```clojure
(defn hello
  [request]
  {:status 200 :body "Hello World!"})

(defn hello-person
  [request name]
  {:status 200 :body (str "Hello " name "!")})

(def my-urls
  (dispatch/urls
   #"^/hello$" #'hello
   ;; the expression "([^/]+)" means to match everything except for "/"
   #"^/hello/([^/]+)" #'hello-person
```

This matches a request for `http://localhost:2345/hello` to the
`hello` handler, and a request for `http://localhost:2345/hello/bob`
would call `hello-person` with the request and "bob" as arguments.

Next, define a function to be called to start the server, using the
urls created by the `dispatch/urls` function:

```clojure
(defn start-server []
  (laeggen/start {:port 2345
                  :append-slash? true
                  :urls my-urls}))
```

The `laeggen/start` function will return a method that can be used to
stop the server, to start the server:

```clojure
(def stop-server (start-server))
;; laeggen will start up the server

;; to stop the server
(stop-server)
```

The URLs map can also use the same handler to handler multiple routes,
so to write the example from above using a single handler using either
optional arguments, or multiple arities:

```clojure
(defn hello-world
  [request & [name]]
  {:status 200 :body (str "Hello " (or name "World") "!")})

;; or, the same function using multi-arities:

(defn hello-world2
  ([request]
     {:status 200 :body "Hello World!"})
  ([request name]
     {:status 200 :body (str "Hello " name "!")}))

;; the same handler is specified for two 'routes'
(def my-urls
  (dispatch/urls
   #"^/hello$" #'hello-world
   #"^/hello/([^/]+)" #'hello-world
   #"^/hello2$" #'hello-world2
   #"^/hello2/([^/]+)" #'hello-world2
```

### Retrieving a query-string

Sometimes it is desirable to use the query string from the handler, in
laeggen, the query string is parsed into a map as part of the request:

```clojure
(defn your-info
  [request]
  (let [name (-> request :query-string :name)
        age (-> request :query-string :age)]
    {:status 200 :body (str name " is " age " years old.")}))

(def my-urls
  (dispatch/urls
   #"^/info$" #'your-info))
```

Which can be tested using curl:

```
% curl "http://localhost:2345/info?name=Jim&age=25"
Jim is 25 years old.
```

## Authentication

Laeggen believes that you should be responsible for your own
authentication, so provides helpers built around that idea.

```clojure
(ns eggplant.baz
  (:require [laeggen.core :as laeggen]
            [laeggen.dispatch :as dispatch]
            [laeggen.auth :as auth]))

(defn home
  [request]
  {:status 200 :body "Welcome home."})

(defn login
  [request]
  (let [username (-> request :query-string :user)
        password (-> request :query-string :pass)]
    (if (auth/authorized? request)
      {:status 200 :body "You have already logged in"}
      (if (and (= username "Dan") (= password "Larkin"))
        (auth/authorize-and-forward! username "/private")
        {:status 401 :body "Unauthorized!"}))))

(defn private
  [request]
  {:status 200 :body "This page is a secret"})

(def my-urls
  (dispatch/urls
   #"^/$" #'home
   #"^/login$" #'login
   #"^/private$" (auth/authorization-required "/" #'private)))
```

In this example, notice that in order to protect a page, it should be
wrapped in the `authorization-required` function, which takes a page
to redirect to in the event the user *is not* authenticated, and a
handler to user if the user *is* authenticated (in this case, the
`private` handler).

Inside the `login` method, it is up to the handler itself to do
whatever authentication is necessary, and when authenticated, call the
`authorize-and-forward!` method with the username (or some identifier)
to store the authorization and redirect to the specified page. The
`authorized?` method can be called with the request at any time to
determine whether the identifier has been authenticated.

To authenticate to this request with curl, you could use:

```
% curl "http://localhost:2345/login?user=Dan&pass=Larkin"
```

## Websockets

To be documented.

## License

Copyright 2012 Dan "The Larkin" Larkin
