FROM apache/iceberg-rest-fixture:1.9.0 AS base

FROM clojure:temurin-17-tools-deps-1.12.0.1530-bookworm-slim

COPY --from=base /usr/lib/iceberg-rest/iceberg-rest-adapter.jar /opt/server/iceberg-rest-adapter.jar

RUN  set -xeu && \
     apt-get update && \
     apt-get install -y --no-install-recommends curl && \
     rm -rf /var/lib/apt/lists/*

WORKDIR /opt/server/

COPY src /opt/server/src
COPY deps.edn /opt/server/

RUN clojure -P

CMD ["clojure", "-M:run"]
