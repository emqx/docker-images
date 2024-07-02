FROM couchbase:community-7.6.1
ENV RETRIES_INIT=50\
    RETRIES_ADDBUCKETS=10
ADD ./entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
CMD ["couchbase-server"]
