#!/bin/bash

set -euo pipefail

set -x

# Source https://github.com/zmstone/docker-kafka/blob/master/generate-certs.sh

HOST="*."
DAYS=3650
PASS="password"
JKS_PREFIX="${CERTGEN_JKS_PREFIX:-kafka}"

if [[ ! "$JKS_PREFIX" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "Invalid CERTGEN_JKS_PREFIX: $JKS_PREFIX" >&2
    exit 1
fi

KEYSTORE="${JKS_PREFIX}.keystore.jks"
TRUSTSTORE="${JKS_PREFIX}.truststore.jks"

cd /var/lib/secret/

# Delete old files
rm -f ca.key ca.crt server.key server.csr client.key client.csr client.crt server.p12 \
    server.crt "$KEYSTORE" "$TRUSTSTORE"

ls

echo '== Generate self-signed server and client certificates'
echo '= generate CA'
openssl req -new -x509 -keyout ca.key -out ca.crt -days $DAYS -nodes -subj "/C=SE/ST=Stockholm/L=Stockholm/O=brod/OU=test/CN=$HOST"

echo '= generate server certificate request'
openssl req -newkey rsa:2048 -sha256 -keyout server.key -out server.csr -days "$DAYS" -nodes -subj "/C=SE/ST=Stockholm/L=Stockholm/O=brod/OU=test/CN=$HOST"

echo '= sign server certificate'
openssl x509 -req -CA ca.crt -CAkey ca.key -in server.csr -out server.crt -days "$DAYS" -CAcreateserial

echo '= generate client certificate request'
openssl req -newkey rsa:2048 -sha256 -keyout client.key -out client.csr -days "$DAYS" -nodes -subj "/C=SE/ST=Stockholm/L=Stockholm/O=brod/OU=test/CN=$HOST"

echo '== sign client certificate'
openssl x509 -req -CA ca.crt -CAkey ca.key -in client.csr -out client.crt -days $DAYS -CAserial ca.srl

echo '= Convert self-signed certificate to PKCS#12 format'
openssl pkcs12 -export -name "$HOST" -in server.crt -inkey server.key -out server.p12 -CAfile ca.crt -passout pass:"$PASS"

echo '= Import PKCS#12 into a java keystore'

keytool -importkeystore \
    -destkeystore "$KEYSTORE" \
    -srckeystore server.p12 \
    -srcstoretype pkcs12 \
    -alias "$HOST" \
    -srcstorepass "$PASS" \
    -deststorepass "$PASS"


echo '= Import CA into java truststore'

keytool -keystore "$TRUSTSTORE" -alias CARoot -import -file ca.crt -storepass "$PASS" -noprompt
