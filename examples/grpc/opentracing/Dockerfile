#
# Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

# 1st stage, build the app
FROM maven:3.6-jdk-11 as build

WORKDIR /helidon

# Create a first set of layers to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update
# the pom
RUN mkdir common
ADD common/pom.xml common/pom.xml
RUN mvn -f common/pom.xml install -DskipTests
RUN mkdir opentracing
ADD opentracing/pom.xml opentracing/pom.xml
RUN mvn -f opentracing/pom.xml package -DskipTests

# Do the Maven build!
# Incremental docker builds will resume here when you change sources
ADD common/src ./common/src
RUN mvn -f common/pom.xml install -DskipTests
ADD opentracing/src ./opentracing/src
RUN mvn -f opentracing/pom.xml package -DskipTests
RUN echo "done!"

# 2nd stage, build the runtime image
FROM openjdk:8-jre-slim
WORKDIR /helidon

# Copy the binary built in the 1st stage
COPY --from=build /helidon/opentracing/target/helidon-examples-grpc-opentracing.jar ./
COPY --from=build /helidon/opentracing/target/libs ./libs

ENV tracing.host="zipkin"
CMD [ "java",  "-jar", "helidon-examples-grpc-opentracing.jar" ]

EXPOSE 1408
