(ns iceberg
  (:require
   [clojure.java.process :as proc]
   [org.httpkit.client :as client]
   [org.httpkit.server :as server]
   [ring.middleware.reload :refer [wrap-reload]]
   [compojure.core :refer [defroutes POST]]))

(def PORT 8191)

(defn start-server
  []
  (proc/start
   {:out :inherit
    :err :inherit}
   "java" "-jar" "iceberg-rest-adapter.jar"))

(defonce server (atom (start-server)))

(defn server-healthy?
  []
  (-> (client/get "http://127.0.0.1:8181/v1/config")
      deref
      :status
      (= 200)))

(defn handle-restart
  [_request]
  (println "Terminating process...")
  (.destroy @server)
  (.waitFor @server)
  (println "Starting new process...")
  (reset! server (start-server))
  (println "Waiting for server to be up...")
  (while (not (server-healthy?)))
  (println "New server up.")
  {:status 204})

(defroutes app-routes
  (POST "/restart" request (handle-restart request)))

(defn- block-forever
  []
  (while true
    (Thread/sleep 60000)))

(defn -main
  [& _args]
  (try
    (println "starting server on port" PORT)
    (server/run-server (wrap-reload #'app-routes) {:port PORT})
    (println "started server on port" PORT)
    (block-forever)
    (catch Exception e
      (println (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))
    (finally
      (.destroy @server))))
