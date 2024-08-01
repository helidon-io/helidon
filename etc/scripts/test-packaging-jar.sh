#!/bin/bash
#
# Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
        "${CODE}" "${BASH_SOURCE[0]}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

# Path to this script
if [ -h "${0}" ] ; then
    SCRIPT_PATH="$(readlink "${0}")"
else
    # shellcheck disable=SC155
    SCRIPT_PATH="${0}"
fi
readonly SCRIPT_PATH

# Path to the root of the workspace
# shellcheck disable=SC2046
WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)
readonly WS_DIR

# Run native image tests
cd "${WS_DIR}/tests/integration/native-image"

# Prime build all native-image tests
# shellcheck disable=SC2086
mvn ${MAVEN_ARGS} -e clean install

# Run tests with classpath and then module path

#
# Run MP-1
#
cd "${WS_DIR}/tests/integration/native-image/mp-1"
# Classpath
java -jar target/helidon-tests-native-image-mp-1.jar

# Module Path
java --module-path target/helidon-tests-native-image-mp-1.jar:target/libs \
  --module helidon.tests.nimage.mp

#
# Run MP-3 (just start and stop)
#
cd "${WS_DIR}/tests/integration/native-image/mp-3"
# Classpath
java -Dexit.on.started=! -jar target/helidon-tests-native-image-mp-3.jar

# Module Path
java -Dexit.on.started=! \
  --module-path target/helidon-tests-native-image-mp-3.jar:target/libs \
  --add-modules helidon.tests.nimage.quickstartmp \
  --module io.helidon.microprofile.cdi/io.helidon.microprofile.cdi.Main
