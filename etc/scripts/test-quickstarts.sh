#!/bin/bash -e
#
# Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
    SCRIPT_PATH="${0}"
fi
readonly SCRIPT_PATH

# Path to the root of the workspace
# shellcheck disable=SC2046
WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)
readonly WS_DIR

version() {
  awk 'BEGIN {FS="[<>]"} ; /<helidon.version>/ {print $3; exit 0}' "${1}"
}

if [ -z "${GRAALVM_HOME}" ]; then
    echo "ERROR: GRAALVM_HOME is not set";
    exit 1
fi

if [ ! -x "${GRAALVM_HOME}/bin/native-image" ]; then
    echo "ERROR: ${GRAALVM_HOME}/bin/native-image does not exist or is not executable";
    exit 1
fi

# shellcheck disable=SC2086
mvn ${MVN_ARGS} --version

"${GRAALVM_HOME}"/bin/native-image --version;

HELIDON_VERSION=$(version "${WS_DIR}/bom/pom.xml")
readonly HELIDON_VERSION

# If needed we clone the helidon-examples repo
if [ ! -d "${WS_DIR}/helidon-examples" ]; then
  echo "Cloning examples repository into ${WS_DIR}/helidon-examples"
  # We can't clone the dev-4.x branch since that has already moved on to the Helidon version in main.
  # So we use the tag of the last matching minor, then update the helidon version in examples to
  # match what is in our branch
  git clone --branch 4.2.0 --single-branch https://github.com/helidon-io/helidon-examples.git "${WS_DIR}/helidon-examples"
  "${WS_DIR}/helidon-examples"/etc/scripts/update-version.sh "${HELIDON_VERSION}"
fi

# Build quickstart native-image executable and run jar file
readonly quickstarts="helidon-quickstart-mp helidon-quickstart-se"
for quickstart in ${quickstarts}; do
  cd "${WS_DIR}/helidon-examples/examples/quickstarts/${quickstart}"
  # shellcheck disable=SC2086
  mvn ${MVN_ARGS} -e clean install -Pnative-image -DskipTests
  ./target/"${quickstart}" -Dexit.on.started=!
done
