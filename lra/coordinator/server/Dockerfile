#
# Copyright (c) 2021 Oracle and/or its affiliates.
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

FROM maven:3.6-jdk-11 as build

WORKDIR /helidon    
ARG HELIDON_BRANCH=master
ARG HELIDON_REPOSITORY=oracle/helidon

ENV HELIDON_BRANCH ${HELIDON_BRANCH}
ENV HELIDON_REPOSITORY ${HELIDON_REPOSITORY}

RUN wget -q -O helidon_repo.zip https://github.com/${HELIDON_REPOSITORY}/archive/refs/heads/${HELIDON_BRANCH}.zip

# Unzip and exclude modules coordinator don't need
RUN unzip helidon_repo.zip -d ./helidon_repo  \
    -x "helidon-${HELIDON_BRANCH}/microprofile/*" \
    -x "helidon-${HELIDON_BRANCH}/openapi/*"  \
    -x "helidon-${HELIDON_BRANCH}/messaging/*"  \
    -x "helidon-${HELIDON_BRANCH}/graphql/*"  \
    -x "helidon-${HELIDON_BRANCH}/tests/*"  \
    -x "helidon-${HELIDON_BRANCH}/examples/*" \
    -x "helidon-${HELIDON_BRANCH}/jersey/*" \
    -x "*src/test/*"
    
RUN mv ./helidon_repo/*/* ./

RUN sed -i '/<module>microprofile/d' ./pom.xml \
&& sed -i '/<module>openapi/d' ./pom.xml \
&& sed -i '/<module>messaging/d' ./pom.xml \
&& sed -i '/<module>jersey/d' ./pom.xml \
&& sed -i '/<module>graphql/d' ./pom.xml

# Build only required modules
RUN mvn install -pl :helidon-lra-coordinator-server -am -DskipTests

RUN echo "Helidon LRA Coordinator build successfully fished"

FROM openjdk:11-jre-slim
WORKDIR /helidon

COPY --from=build /helidon/lra/coordinator/server/target/helidon-lra-coordinator-server.jar ./
COPY --from=build /helidon/lra/coordinator/server/target/libs ./libs

CMD ["java", "-jar", "helidon-lra-coordinator-server.jar"]

EXPOSE 8070
