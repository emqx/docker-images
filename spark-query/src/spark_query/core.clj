(ns spark-query.core
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [cider.nrepl :refer [cider-nrepl-handler]]
   [nrepl.server :as nrepl-server]
   [org.httpkit.server :as server]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.util.request :as ring-req]
   [compojure.core :refer [defroutes GET POST]]
   [taoensso.telemere :as log])
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
   [org.apache.hadoop.conf Configuration]
   [org.apache.spark.sql SparkSession]
   (org.apache.parquet.avro AvroParquetReader)
   (org.apache.parquet.conf PlainParquetConfiguration)
   (org.apache.parquet.io SeekableInputStream InputFile))
  (:gen-class))

(def PORT 8090)
(def NREPL-PORT 7890)

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

(defn spark-session
  []
  (-> (SparkSession/builder)
      (.config "spark.sql.defaultCatalog" "demo")
      (.config "spark.sql.catalog.demo" "org.apache.iceberg.spark.SparkCatalog")
      (.config "spark.sql.catalog.demo.type" "rest")
      (.config "spark.sql.catalog.demo.uri" "http://iceberg-rest:8181")
      (.config "spark.sql.catalog.demo.io-impl" "org.apache.iceberg.aws.s3.S3FileIO")
      (.config "spark.sql.catalog.demo.warehouse" "s3://warehouse/wh/")
      (.config "spark.sql.catalog.demo.s3.endpoint" "http://minio.net:9000")
      (.master "local")
      .getOrCreate))

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

(defn scan-table
  [table]
  (-> table
      IcebergGenerics/read
      .build
      .iterator
      iterator-seq
      (into [])))

(defn partition-data->vec
  [pdata]
  (let [size (.size pdata)
        fields (->> pdata
                    .getPartitionType
                    .fields
                    (mapv #(.name %)))
        values (mapv #(.get pdata %) (range size))]
    (zipmap fields values)))

(defn table-partitions-from-meta
  [table]
  (letfn [(content-data-file->vec [content-data-file]
            (-> content-data-file
                .partition
                partition-data->vec))]
    (-> table
        .currentSnapshot
        (.addedDataFiles (.io table))
        (->> (map content-data-file->vec)))))

(defn table-partitions-from-data
  [table]
  (-> table
      .newScan
      .planTasks
      .iterator
      iterator-seq
      (->> (mapcat #(.files %))
           (map #(-> %
                     .partition
                     partition-data->vec)))))

(defn handle-scan-table
  [ns-in table-in]
  (let [table (load-table (get-catalog) ns-in table-in)
        rows (scan-table table)
        response-body (->> rows
                           (mapv record->vec)
                           json/write-str)]
    {:body response-body}))

(defn handle-table-partitions
  [ns-in table-in]
  (let [table (load-table (get-catalog) ns-in table-in)
        partitions-from-meta (table-partitions-from-meta table)
        partitions-from-data (table-partitions-from-data table)
        response {:from-data partitions-from-data
                  :from-meta partitions-from-meta}
        response-body (json/write-str response)]
    {:body response-body}))

(defn handle-spark-sql
  [request]
  (let [sql (ring-req/body-string request)
        session (spark-session)
        dataset (.sql session sql)
        _ (.show dataset)
        response-body (-> dataset
                          .toJSON
                          .toLocalIterator
                          iterator-seq
                          (->> (map json/read-str))
                          json/write-str)]
    {:body response-body}))

(defn avro->json
  [avro]
  (-> avro
      .toString
      json/read-str))

(defn new-seekable-input-stream
  [in-ba]
  (let [pos (atom 0)]
    (letfn [(read1-byte []
              (let [x (aget in-ba @pos)]
                (swap! pos inc)
                (byte x)))
            (read1 []
              (let [x (read1-byte)
                    x-u (bit-and x 0xff)]
                (int x-u)))
            (read-array [out-array]
              (let [to-read (alength out-array)]
                (doseq [i (range to-read)]
                  (let [x (read1-byte)]
                    (aset-byte out-array i x)))))]
      (proxy [SeekableInputStream] []
        (read
          ([]
           (read1))
          ([byte-buffer]
           :todo))
        (getPos []
          @pos)
        (seek [new-pos]
          (reset! pos new-pos))
        (readFully
          ([out-array]
           (if (bytes? out-array)
             (read-array out-array)
             ;; java.nio.ByteBuffer
             (let [to-read (.remaining out-array)
                   tmp (byte-array to-read (byte 0))]
               (read-array tmp)
               (.put out-array
                     tmp
                     (+ (.position out-array) (.arrayOffset out-array))
                     (.remaining out-array)))))
          ([out-array start len]
           (doseq [i (range len)]
             (let [x (read1-byte)]
               (aset-byte out-array (+ start i) x)))))))))

(defn new-mem-input-file
  [in-ba]
  (proxy [InputFile] []
    (getLength []
      (alength in-ba))
    (newStream []
      (new-seekable-input-stream in-ba))))

(defn read-parquet-avro
  [input-file]
  (with-open [reader (AvroParquetReader/genericRecordReader
                      input-file
                      (PlainParquetConfiguration.
                       {"parquet.avro.readInt96AsFixed" "true"}))]
    (loop [record (.read reader)
           acc []]
      (if record
        (recur (.read reader)
               (->> record
                    avro->json
                    (conj acc)))
        acc))))

(defn read-parquet
  [data-raw]
  (with-open [in (io/input-stream data-raw)
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (-> out
        .toByteArray
        new-mem-input-file
        read-parquet-avro)))

(defn handle-read-parquet
  [request]
  (let [result (read-parquet (:body request))]
    {:body (json/write-str result)}))

(defroutes app-routes
  (GET "/scan/:ns/:table" [ns table] (handle-scan-table ns table))
  (GET "/partitions/:ns/:table" [ns table] (handle-table-partitions ns table))
  (POST "/sql" request (handle-spark-sql request))
  (POST "/read-parquet" request (handle-read-parquet request)))

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
