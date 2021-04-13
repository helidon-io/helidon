#!/bin/bash -ex
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

set -o pipefail || true  # trace ERR through pipes
set -o errtrace || true # trace ERR through commands and functions
set -o errexit || true  # exit the script if any statement returns a non-true return value

on_error(){
    CODE="${?}" && \
    set +x && \
    printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
        "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

# Path to this script
if [ -h "${0}" ] ; then
    readonly SCRIPT_PATH="$(readlink "${0}")"
else
    readonly SCRIPT_PATH="${0}"
fi

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)

source ${WS_DIR}/etc/scripts/pipeline-env.sh

JAVA_HOME='/tools/graalvm-ce-java11-21.0.0'
PATH="${PATH}:${JAVA_HOME}/bin"

mvn ${MAVEN_ARGS} --version

# Temporary workaround until job stages will share maven repository
mvn ${MAVEN_ARGS} -f ${WS_DIR}/pom.xml \
    install -e \
    -DskipTests \
    -Ppipeline

# Run integrations tests for Vault
cd tests/integration/vault

mvn ${MAVEN_ARGS} clean verify \
      -Dmaven.test.failure.ignore=true
