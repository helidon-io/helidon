#!/bin/bash
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

# WERCKER=true when running inside a wercker pipeline

if [ "${WERCKER}" = "true" ] ; then
    export MAVEN_OPTS="-Dmaven.repo.local=${WERCKER_CACHE_DIR}/local_repository -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    rm -rf ~/.m2/settings* ~/.gitconfig ~/.ssh ${WERCKER_CACHE_DIR}/local_repository/io/helidon
    # Work around https://github.com/oracle/oci-java-sdk/issues/25
    TEMP_OCI_SDK_DIR=$(mktemp -d "oci-java-sdk.XXX")
    git clone --depth 1 --branch v$(mvn -f "${WERCKER_ROOT}/pom.xml" -Dexpression=version.lib.oci-java-sdk-objectstorage org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate | grep '^[0-9]') "https://github.com/oracle/oci-java-sdk.git" "${TEMP_OCI_SDK_DIR}" && mvn -U -f "${TEMP_OCI_SDK_DIR}/pom.xml" -Dmaven.test.skip=true -Dmaven.source.skip=true -Dmaven.javadoc.skip=true -Dlombok.delombok.skip=true -pl bmc-objectstorage -am install && rm -rf "${TEMP_OCI_SDK_DIR}"
fi
