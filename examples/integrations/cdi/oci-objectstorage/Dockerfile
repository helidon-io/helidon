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

# Create a first layer to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update
# the pom
ADD pom.xml .
RUN mvn package -Dmaven.test.skip

# Do the Maven build!
# Incremental docker builds will resume here when you change sources
ADD src src
RUN mvn package -DskipTests
RUN echo "done!"

# 2nd stage, build the runtime image
FROM openjdk:11-jre-slim
WORKDIR /helidon

# Copy the binary built in the 1st stage
COPY --from=build /helidon/target/helidon-examples-integrations-cdi-oci-objectstorage.jar ./
COPY --from=build /helidon/target/libs ./libs

CMD [ "sh", "-c", "exec java \
    -Doci.auth.fingerprint=\"${OCI_AUTH_FINGERPRINT}\" \
    -Doci.auth.passphraseCharacters=\"${OCI_AUTH_PASSPHRASE}\" \
    -Doci.auth.privateKey=\"${OCI_AUTH_PRIVATEKEY}\" \
    -Doci.auth.tenancy=\"${OCI_AUTH_TENANCY}\" \
    -Doci.auth.user=\"${OCI_AUTH_USER}\" \
    -Doci.objectstorage.compartmentId=\"${OCI_OBJECTSTORAGE_COMPARTMENT}\" \
    -Doci.objectstorage.region=\"${OCI_OBJECTSTORAGE_REGION}\" \
    -jar helidon-examples-integrations-cdi-oci-objectstorage.jar" ]

EXPOSE 8080
