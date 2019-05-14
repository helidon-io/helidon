#
# Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

FROM debian:stretch as build

WORKDIR /build

RUN set -x \
    && apt-get -y update \
    && apt-get -y install curl

RUN curl -O -L https://github.com/oracle/graal/releases/download/vm-19.0.0/graalvm-ce-linux-amd64-19.0.0.tar.gz && \
    tar -xvzf graalvm-*.tar.gz && \
    rm graalvm-*.tar.gz && \
    mv graalvm-* graalvm && \
    graalvm/bin/gu install native-image

RUN cd graalvm && \
    rm -rf \
    samples \
    man \
    jre/man \
    jre/languages \
    src.zip \
    bin/jvisualvm lib/visualvm \
    bin/js jre/bin/js \
    bin/node jre/bin/node \
    bin/npm jre/bin/npm \
    bin/polyglot jre/bin/polyglot \
    bin/lli jre/bin/lli \
    jre/lib/amd64/libjvmcicompiler.so \
    jre/lib/amd64/liblcms.so \
    jre/lib/boot/graaljs-scriptengine.jar \
    jre/lib/graalvm/graal-truffle-compiler-libgraal.jar \
    jre/lib/graalvm/graaljs-launcher.jar \
    jre/lib/graalvm/sulong-launcher.jar

RUN echo "done!"

FROM debian:stretch-slim as final

RUN set -x \
    && apt-get -y update \
    && apt-get -y install gcc zlib1g-dev \
    && apt-get clean autoclean \
    && apt-get autoremove --yes \
    && rm -rf /var/lib/{apt,dpkg,cache,log}/

WORKDIR /graal
COPY --from=build /build/graalvm /graal/graalvm
ENV GRAALVM_HOME=/graal/graalvm
