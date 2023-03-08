FROM openjdk:11-jre-slim

COPY ./entrypoint.sh /bin/entrypoint.sh

RUN set -eux ; \
    apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates openssl && \
    apt clean && \
    apt autoremove -y && \
    rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["/bin/entrypoint.sh"]
