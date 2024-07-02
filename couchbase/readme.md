# Couchbase

Based on https://github.com/cha87de/couchbase-docker-cloudnative

```sh
docker run --rm -it \
  -e CLUSTER=localhost \
  -e USER=admin \
  -e PASS=public \
  -e PORT=8091 \
  -e RAMSIZEMB=2048 \
  -e RAMSIZEINDEXMB=512 \
  -e RAMSIZEFTSMB=512 \
  -e BUCKETS=mqtt \
  -e BUCKETSIZES=100 \
  -e AUTOREBALANCE=true \
  -p 8091-8093:8091-8093 \
  ghcr.io/emqx/couchbase:1.0.0

```

```sh
curl -v http://localhost:8093/query/service \
     -d 'statement=INSERT INTO mqtt (KEY, VALUE) VALUES ("1", {"id": 1})
       & args=[]' \
     -u admin:public | jq .

curl -v http://localhost:8093/query/service \
     -d 'statement=INSERT INTO mqtt (KEY, VALUE) VALUES ("3", {"id": 33}),
                   VALUES ("10", {"name": "foo"})
       & args=[]' \
     -u admin:public | jq .

curl -v http://localhost:8093/query/service \
     -d 'statement=SELECT * FROM mqtt
       & args=[]' \
     -u admin:public | jq .

```
