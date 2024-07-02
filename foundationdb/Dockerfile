# Dockerfile
#
# This source file is part of the FoundationDB open source project
#
# Copyright 2013-2021 Apple Inc. and the FoundationDB project authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
FROM public.ecr.aws/amazonlinux/amazonlinux:2023 as base

RUN yum install -y \
    bind-utils \
    binutils \
    gdb \
    hostname \
    jq  \
    less \
    libubsan \
    lsof \
    net-tools \
    nmap-ncat \
    perf \
    perl \
    procps-ng \
    strace \
    sysstat \
    tar \
    tcpdump \
    telnet \
    traceroute \
    unzip \
    openssl \
    vim-enhanced && \
    yum clean all && \
    rm -rf /var/cache/yum

WORKDIR /tmp

RUN curl -Ls https://github.com/krallin/tini/releases/download/v0.19.0/tini-amd64 -o tini  && \
    echo "93dcc18adc78c65a028a84799ecf8ad40c936fdfc5f2a57b1acda5a8117fa82c  tini" > tini-sha.txt && \
    sha256sum --quiet -c tini-sha.txt && \
    chmod +x tini && \
    mv tini /usr/bin/ && \
    rm -rf /tmp/*

WORKDIR /

FROM base as foundationdb-base

WORKDIR /tmp
ARG FDB_VERSION=7.3.27
ARG FDB_LIBRARY_VERSIONS="${FDB_VERSION}"
ARG FDB_WEBSITE=https://github.com/apple/foundationdb/releases/download

RUN mkdir -p /var/fdb/{logs,tmp,lib} && \
    mkdir -p /usr/lib/fdb/multiversion && \
    echo ${FDB_VERSION} > /var/fdb/version

# Set up a non-root user
RUN groupadd --gid 4059 \
             fdb && \
    useradd --gid 4059 \
            --uid 4059 \
            --no-create-home \
            --shell /bin/bash \
            fdb && \
    chown -R fdb:fdb /var/fdb

# Install FoundationDB Binaries
RUN for file in fdbserver fdbbackup fdbcli fdbmonitor; do \
        curl --fail -Ls ${FDB_WEBSITE}/${FDB_VERSION}/$file.x86_64 -o $file; \
        chmod +x $file; \
        mv $file /usr/bin; \
    done

# Setup all symlinks for the other binaries that are a copy of fdbbackup
RUN for file in fdbdr fdbrestore backup_agent dr_agent fastrestore_tool; do \
        ln -s /usr/bin/fdbbackup /usr/bin/$file; \
    done

# Install additional FoundationDB Client Libraries
RUN for version in $FDB_LIBRARY_VERSIONS; do \
    curl --fail -Ls ${FDB_WEBSITE}/${version}/libfdb_c.x86_64.so -o /usr/lib/fdb/multiversion/libfdb_c_${version%.*}.so; \
    done

# Install additional FoundationDB Client Libraries (for sidecar)
RUN mkdir -p /var/fdb/lib && \
    for version in $FDB_LIBRARY_VERSIONS; do \
    curl --fail -Ls ${FDB_WEBSITE}/${version}/libfdb_c.x86_64.so -o /var/fdb/lib/libfdb_c_${version%.*}.so; \
    done

RUN curl -Ls $FDB_WEBSITE/$FDB_VERSION/libfdb_c.x86_64.so -o /usr/lib/libfdb_c.so

RUN rm -rf /tmp/*
WORKDIR /

FROM foundationdb-base as foundationdb

WORKDIR /tmp
RUN curl -LsO https://raw.githubusercontent.com/brendangregg/FlameGraph/90533539b75400297092f973163b8a7b067c66d3/stackcollapse-perf.pl && \
    curl -LsO https://raw.githubusercontent.com/brendangregg/FlameGraph/90533539b75400297092f973163b8a7b067c66d3/flamegraph.pl && \
    echo "a682ac46497d6fdbf9904d1e405d3aea3ad255fcb156f6b2b1a541324628dfc0  flamegraph.pl" > flamegraph-sha.txt && \
    echo "5bcfb73ff2c2ab7bf2ad2b851125064780b58c51cc602335ec0001bec92679a5  stackcollapse-perf.pl" >> flamegraph-sha.txt && \
    sha256sum --quiet -c flamegraph-sha.txt && \
    chmod +x stackcollapse-perf.pl flamegraph.pl && \
    mv stackcollapse-perf.pl flamegraph.pl /usr/bin && \
    rm -rf /tmp/*
WORKDIR /
# Set Up Runtime Scripts and Directories
ADD fdb.bash fdb_single.bash /var/fdb/scripts/
RUN chmod a+x /var/fdb/scripts/fdb.bash /var/fdb/scripts/fdb_single.bash
VOLUME /var/fdb/data
ENV FDB_PORT 4500
ENV FDB_CLUSTER_FILE /var/fdb/fdb.cluster
ENV FDB_NETWORKING_MODE container
ENV FDB_COORDINATOR ""
ENV FDB_COORDINATOR_PORT 4500
ENV FDB_CLUSTER_FILE_CONTENTS ""
ENV FDB_PROCESS_CLASS unset
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "/var/fdb/scripts/fdb.bash"]
