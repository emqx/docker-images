(ns bigtable.core
  (:require
   [clojure.data.json :as json]
   [cider.nrepl :refer [cider-nrepl-handler]]
   [nrepl.server :as nrepl-server]
   [org.httpkit.server :as server]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.util.request :as ring-req]
   [compojure.core :refer [defroutes GET POST DELETE]]
   [taoensso.telemere :as log])
  (:import
   (com.google.bigtable.v2
    TableName)
   (com.google.bigtable.admin.v2
    DeleteTableRequest)
   (com.google.cloud.bigtable.data.v2
    BigtableDataClient
    BigtableDataSettings)
   (com.google.cloud.bigtable.admin.v2
    BigtableTableAdminClient
    BigtableTableAdminSettings)
   (com.google.cloud.bigtable.admin.v2.models
    CreateTableRequest)
   (com.google.cloud.bigtable.data.v2.models
    Query))
  (:gen-class))

(def PORT 8090)
(def NREPL-PORT 7890)
(def EMULATOR-PORT 8086)
(def HOST (or (System/getenv "EMULATOR_HOST") "gcp-emulator-bigtable"))
(def PROJECT_ID (or (System/getenv "PROJECT_ID") "emqx"))
(def INSTANCE_ID (or (System/getenv "INSTANCE_ID") "emqxinst"))

(defn mk-admin-client
  [{:keys [:host :project-id :instance-id]
    :or {host HOST
         project-id PROJECT_ID
         instance-id INSTANCE_ID}}]
  (let [settings (-> (BigtableTableAdminSettings/newBuilderForEmulator
                      host
                      EMULATOR-PORT)
                     (.setProjectId project-id)
                     (.setInstanceId instance-id)
                     (.build))
        client (BigtableTableAdminClient/create settings)]
    client))

(defn mk-data-client
  [{:keys [:host :project-id :instance-id]
    :or {host HOST
         project-id PROJECT_ID
         instance-id INSTANCE_ID}}]
  (let [settings (-> (BigtableDataSettings/newBuilderForEmulator
                      host
                      EMULATOR-PORT)
                     (.setProjectId project-id)
                     (.setInstanceId instance-id)
                     (.build))
        client (BigtableDataClient/create settings)]
    client))

(defn create-table
  [client opts]
  (let [{:keys [:name :column_families]} opts
        create-opts (CreateTableRequest/of name)
        create-opts (reduce (fn [acc cf]
                              (.addFamily acc cf))
                            create-opts
                            column_families)]
    (.createTable client create-opts)))

(defn delete-table
  [client name]
  (let [table-name (-> (TableName/of
                        PROJECT_ID
                        INSTANCE_ID
                        name)
                       str)
        delete-opts (-> (DeleteTableRequest/newBuilder)
                        (.setName table-name)
                        .build)]
    (-> client
        .getBaseClient
        (.deleteTable delete-opts))))

(defn- row->map
  [row]
  (let [cells (.getCells row)]
    (reduce (fn [acc cell]
              (let [name (-> cell .getQualifier .toStringUtf8)
                    value (-> cell .getValue .toStringUtf8)]
                (assoc acc name value)))
            {}
            cells)))

(defn read-rows
  [client name]
  (let [query (Query/create name)
        stream (.readRows client query)]
    (mapv row->map stream)))

(defn handle-create-table
  [req]
  (let [create-opts (-> req :body slurp (json/read-str :key-fn keyword))
        client (mk-admin-client {})]
    (create-table client create-opts)
    {:status 204}))

(defn handle-delete-table
  [name]
  (let [client (mk-admin-client {})]
    (delete-table client name)
    {:status 204}))

(defn handle-read-table
  [name]
  (let [client (mk-data-client {})
        rows (read-rows client name)]
    {:body (json/write-str rows)}))

(defroutes app-routes
  (POST "/table" request (handle-create-table request))
  (DELETE "/table/:name" [name] (handle-delete-table name))
  (GET "/table/:name" [name] (handle-read-table name)))

(defn- block-forever
  []
  (while true
    (Thread/sleep 60000)))

(defn -main
  [& _args]
  (try
    (println "starting nREPL server on port" NREPL-PORT)
    (nrepl-server/start-server :port NREPL-PORT :bind "0.0.0.0" :handler cider-nrepl-handler)
    (println "started nREPL server on port" NREPL-PORT)
    (println "starting server on port" PORT)
    (server/run-server (wrap-reload #'app-routes) {:port PORT})
    (println "started server on port" PORT)
    (block-forever)
    (catch Exception e
      (println (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))
