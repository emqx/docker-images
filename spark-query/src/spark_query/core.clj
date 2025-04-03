(ns spark-query.core
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [org.httpkit.server :as server]
   [compojure.core :refer [defroutes GET POST]])
  (:import
   [java.util Properties]
   [org.apache.iceberg
    CatalogProperties]
   [org.apache.iceberg.catalog
    Catalog
    Namespace
    TableIdentifier]
   [org.apache.iceberg.data
    IcebergGenerics
    Record]
   [org.apache.iceberg.rest RESTCatalog]
   [org.apache.iceberg.aws.s3 S3FileIOProperties]
   [org.apache.hadoop.conf Configuration])
  (:gen-class))

(def PORT 8090)

(defn- record->vec
  [record]
  (let [size (.size record)
        columns (->> record
                     .struct
                     .fields
                     (mapv #(.name %)))
        values (mapv #(.get record %) (range size))]
    (zipmap columns values)))

(defn open-catalog
  []
  (let [catalog-props {CatalogProperties/CATALOG_IMPL "org.apache.iceberg.rest.RESTCatalog"
                       CatalogProperties/URI "http://iceberg-rest:8181"
                       CatalogProperties/WAREHOUSE_LOCATION, "s3a://warehouse/wh"
                       CatalogProperties/FILE_IO_IMPL "org.apache.iceberg.aws.s3.S3FileIO"
                       S3FileIOProperties/ENDPOINT "http://minio.net:9000"}
        catalog (RESTCatalog.)
        catalog-config (Configuration.)
        _ (doto catalog
            (.setConf catalog-config)
            (.initialize "demo" catalog-props))]
    catalog))

(def CATALOG (atom nil))

(defn get-catalog
  []
  (if @CATALOG
    @CATALOG
    (let [catalog (open-catalog)]
      (reset! CATALOG catalog)
      catalog)))

(defn load-table
  [catalog ns table]
  (let [ns-id (Namespace/of (into-array String [ns]))
        table-id (TableIdentifier/of ns-id table)
        table (.loadTable catalog table-id)]
    table))

(defn scan-table [ns-in table-in]
  (let [table (load-table (get-catalog) ns-in table-in)
        rows (-> table
                 IcebergGenerics/read
                 .build
                 .iterator
                 iterator-seq
                 (into []))
        response-body (->> rows
                           (mapv record->vec)
                           json/write-str)]
    {:body response-body}))

(defroutes app-routes
  (GET "/scan/:ns/:table" [ns table] (scan-table ns table)))

(defn- block-forever
  []
  (while true
    (Thread/sleep 60000)))

(defn -main
  [& args]
  (try
    (println "starting server on port" PORT)
    (server/run-server app-routes {:port PORT})
    (println "started server on port" PORT)
    (block-forever)
    (catch Exception e
      (println (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))
