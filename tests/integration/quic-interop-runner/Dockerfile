#
# Copyright (c) 2026 Oracle and/or its affiliates.
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

FROM container-registry.oracle.com/java/openjdk:27-oraclelinux9@sha256:7477f4f5b0deebce818b5c918c9f0aa6eafb1342eb2ed54628ebbcd0a1dd7ada AS java27

FROM martenseemann/quic-network-simulator-endpoint@sha256:3b2b9e6fa317da238c8140a7b6d2bf8d4d45a8464b873eecac3ff2a32520b71a

COPY --from=java27 /usr/java/jdk-27 /opt/java/openjdk

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /opt/helidon

COPY target/helidon-tests-integration-quic-interop-runner.jar /opt/helidon/app.jar
COPY target/libs/ /opt/helidon/libs/
COPY run_endpoint.sh /run_endpoint.sh

RUN chmod +x /run_endpoint.sh && \
    mkdir -p /logs/server /logs/client /opt/helidon

ENTRYPOINT ["/run_endpoint.sh"]
