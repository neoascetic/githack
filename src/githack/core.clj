(ns githack.core
  (:require [clojure.java.jdbc :refer :all]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.util.response :refer :all]
            [ring.util.response :refer :all]
            [hiccup.core :refer :all]
            [hiccup.page :refer :all]
            [hiccup.util :refer :all]
            [hiccup.element :refer :all]
            [clj-http.client :as http]
            [tentacles.users :as users :refer [me]]
            [overtone.at-at :as at]
            [githack.github :as gh]
            [githack.bothack :as bh])
  (:gen-class))

(def users (atom #{}))
(def events-pool (at/mk-pool))
(def def-user {:turns 1 :password "pass" :flags 0 :env ""})
(def app-cfg (load-string (slurp "config.edn")))
(def db {:classname "org.sqlite.JDBC"
         :subname (app-cfg :db-path)
         :subprotocol "sqlite"})

(defn- saved-meta [name]
  (-> (query db ["SELECT meta FROM dglusers WHERE username = ?" name])
      first :meta str load-string))

(defn- available-turns [name]
  (-> (query db ["SELECT turns FROM dglusers WHERE username = ? AND turns > 0" name])
      first :turns))

(defn watch-user! [name]
  (let [[count meta] (gh/events-count name (saved-meta name))]
    (execute!
      db
      ["UPDATE dglusers SET turns = turns + ?, meta = ? WHERE username = ?"
       count (pr-str meta) name])
    (at/after (* 1000 (:poll-interval meta 60)) #(watch-user! name) events-pool)))

(defn play-user! [name]
  (when-let [turns (available-turns name)]
    (when-let [passed (bh/do-turns! name (min turns 100))]
      (execute! db ["UPDATE dglusers SET turns = turns - ? WHERE username = ?" passed name]))))

(defn user-exists? [name]
  (not (empty? (query db ["SELECT * FROM dglusers WHERE username = ?" name]))))

(defn create-user! [name token]
  (when-not (user-exists? name)
    (insert! db :dglusers (assoc def-user
                                :username name
                                :email (str name "@githack.com")
                                :meta (pr-str {:oauth-token token})))
    (future
      (play-user! name)
      (swap! users conj name))
    (future (watch-user! name))))

(defn code->token [code]
  (let [res (http/post
              "https://github.com/login/oauth/access_token"
              {:form-params (assoc app-cfg :code code) :as :json :accept :json})]
    (-> res :body :access_token)))


(defn- latest-rec [name]
  (if-let [file (-> (io/file (app-cfg :userdata-dir) name "ttyrec") .list sort last)]
    (str "/" name "/ttyrec/" file)))

(defn handle-index []
  (response
    (html5
      [:head [:title "GitHack"] (include-css "/core.css")]
      [:body {:class :main}
        [:h1 "NetHack"]
        [:h2 "GitHub + NetHack = zero-player RPG"]
        (link-to
          (url "https://github.com/login/oauth/authorize" {:client_id (app-cfg :client_id)})
          "Register with GitHub!")])))

(defn handle-github-auth [request]
  (if-let [code (get-in request [:query-params "code"])]
    (let [token (code->token code)
          name (:login (users/me {:oauth-token token}))]
      (create-user! name token)
      (redirect (str "/" name)))
    (not-found "404!")))

(defn handle-user [uri]
  (let [[_ name] (re-matches #"/([^/]+)/?" uri)]
    (if (user-exists? name)
      (response
        (html5
          [:head
           [:title "Hello, " name ", welcome to GitHack!"]
           (include-css "/core.css")]
          [:body
           (include-js "//rawcdn.githack.com/oliy/ttyplay/master/js/jbinary.js")
           (include-js "//rawcdn.githack.com/oliy/ttyplay/master/js/ttyplay.js")
           (include-js "//rawcdn.githack.com/oliy/ttyplay/master/js/term.js")
           (if-let [latest-rec (latest-rec name)]
             (javascript-tag (format "window.ttyrecUrl = '%s';" latest-rec)))
           (include-js "/core.js")]))
      (not-found "404!"))))

(defn handler [request]
  (content-type
    (condp = (request :uri)
      "/" (handle-index)
      "/github-auth" (handle-github-auth request)
      (handle-user (request :uri)))
    "text/html"))

(def app
  (-> handler
      (wrap-params)
      (wrap-resource "public")
      (wrap-file (app-cfg :userdata-dir))
      (wrap-content-type "text/html")
      (wrap-not-modified)))

(defn -main [& args]
  (reset! users (set (query db "SELECT username FROM dglusers" :row-fn :username)))
  (future (doall (map watch-user! @users)))
  (future (while true (doall (map play-user! @users))))
  (jetty/run-jetty app {:port 80}))
