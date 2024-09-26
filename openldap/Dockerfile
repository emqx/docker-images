FROM buildpack-deps:bookworm

ARG LDAP_TAG=2.5.16

RUN apt-get update && apt-get install -y groff groff-base ldap-utils
RUN wget https://www.openldap.org/software/download/OpenLDAP/openldap-release/openldap-${LDAP_TAG}.tgz \
    && tar xvzf openldap-${LDAP_TAG}.tgz \
    && cd openldap-${LDAP_TAG} \
    && ./configure --enable-memberof --enable-refint && make depend && make && make install \
    && cd .. && rm -rf  openldap-${LDAP_TAG}

WORKDIR /usr/local/etc/openldap

EXPOSE 389 636
