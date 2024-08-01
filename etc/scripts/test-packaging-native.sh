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

if [ -z "${GRAALVM_HOME}" ]; then
    echo "ERROR: GRAALVM_HOME is not set";
    exit 1
fi

if [ ! -x "${GRAALVM_HOME}/bin/native-image" ]; then
    echo "ERROR: ${GRAALVM_HOME}/bin/native-image does not exist or is not executable";
    exit 1
fi

# shellcheck disable=SC2086
mvn ${MAVEN_ARGS} --version

echo "GRAALVM_HOME=${GRAALVM_HOME}";
"${GRAALVM_HOME}"/bin/native-image --version;

# Run native image tests
cd "${WS_DIR}/tests/integration/native-image"

# Prime build all native-image tests
# shellcheck disable=SC2086
mvn ${MAVEN_ARGS} -e clean install

# Build native images
readonly native_image_tests="se-1 mp-1 mp-3"
for native_test in ${native_image_tests}; do
    cd "${WS_DIR}/tests/integration/native-image/${native_test}"
    # shellcheck disable=SC2086
    mvn ${MAVEN_ARGS} -e clean package -Pnative-image
done

# Run this one because it has no pre-reqs and self-tests
# Uses relative path to read configuration
cd "${WS_DIR}/tests/integration/native-image/mp-1"
./target/helidon-tests-native-image-mp-1 || true

# Run se-1 exiting on started
cd "${WS_DIR}/tests/integration/native-image/se-1"
./target/helidon-tests-native-image-se-1 -Dexit.on.started=! || true
