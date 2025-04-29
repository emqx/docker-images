## Using an image with clojure so we may use `add-lib` in nREPL
ARG RUN_FROM=clojure:temurin-17-tools-deps-1.12.0.1530-bookworm-slim

FROM ${RUN_FROM}

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Cache layer with dependencies
COPY ./deps.edn /usr/src/app/deps.edn
RUN clojure -X:deps prep

COPY ./src /usr/src/app/src

CMD ["clojure", "-M:run", "-m", "spark-query.core"]
