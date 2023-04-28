#
# Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

FROM container-registry.oracle.com/java/openjdk:17 as build

WORKDIR /usr/share

# Install maven
RUN set -x && \
    curl -O https://archive.apache.org/dist/maven/maven-3/3.8.4/binaries/apache-maven-3.8.4-bin.tar.gz && \
    tar -xvf apache-maven-*-bin.tar.gz  && \
    rm apache-maven-*-bin.tar.gz && \
    mv apache-maven-* maven && \
    ln -s /usr/share/maven/bin/mvn /bin/

WORKDIR /helidon
ARG HELIDON_BRANCH=helidon-3.x
ARG HELIDON_REPOSITORY=oracle/helidon

ENV HELIDON_BRANCH ${HELIDON_BRANCH}
ENV HELIDON_REPOSITORY ${HELIDON_REPOSITORY}

RUN dnf -y update && dnf -y install wget unzip git
RUN wget -q -O helidon_repo.zip https://github.com/${HELIDON_REPOSITORY}/archive/refs/heads/${HELIDON_BRANCH}.zip && \
    unzip helidon_repo.zip -d ./helidon_repo && mv ./helidon_repo/*/* ./

# Build only required modules
RUN mvn install -pl :helidon-lra-coordinator-server -am -DskipTests && \
    echo "Helidon LRA Coordinator build successfully finished"

FROM container-registry.oracle.com/java/openjdk:17
WORKDIR /helidon

COPY --from=build /helidon/lra/coordinator/server/target/helidon-lra-coordinator-server.jar ./
COPY --from=build /helidon/lra/coordinator/server/target/libs ./libs

CMD ["java", "-jar", "helidon-lra-coordinator-server.jar"]

EXPOSE 8070
