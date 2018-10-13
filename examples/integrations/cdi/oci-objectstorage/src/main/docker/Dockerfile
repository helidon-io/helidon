#
# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
FROM openjdk:8-jre-alpine
EXPOSE 8080
RUN mkdir /app
COPY ${project.build.finalName}.jar /app
COPY ${dependenciesDirectory} /app/${dependenciesDirectory}
CMD [ \
"sh", "-c", \
"exec java \
-Doci.auth.fingerprint=\"${OCI_AUTH_FINGERPRINT}\" \
-Doci.auth.passphraseCharacters=\"${OCI_AUTH_PASSPHRASE}\" \
-Doci.auth.privateKey=\"${OCI_AUTH_PRIVATEKEY}\" \
-Doci.auth.tenancy=\"${OCI_AUTH_TENANCY}\" \
-Doci.auth.user=\"${OCI_AUTH_USER}\" \
-Doci.objectstorage.compartmentId=\"${OCI_OBJECTSTORAGE_COMPARTMENT}\" \
-Doci.objectstorage.region=\"${OCI_OBJECTSTORAGE_REGION}\" \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseCGroupMemoryLimitForHeap \
-jar /app/${project.build.finalName}.jar" \
]
