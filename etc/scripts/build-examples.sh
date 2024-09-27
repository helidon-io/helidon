#!/bin/bash
#
# Copyright (c) 2024 Oracle and/or its affiliates.
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

version() {
  awk 'BEGIN {FS="[<>]"} ; /<helidon.version>/ {print $3; exit 0}' "${1}"
}

HELIDON_VERSION=$(version "${WS_DIR}/bom/pom.xml")
readonly HELIDON_VERSION

# If needed we clone the helidon-examples repo
if [ ! -d "${WS_DIR}/helidon-examples" ]; then
  echo "Cloning examples repository into ${WS_DIR}/helidon-examples"
  # We can't clone the dev-4.x branch since that has already moved on to the Helidon version in main.
  # So we use the tag of the last matching minor, then update the helidon version in examples to
  # match what is in our branch
  git clone --branch 4.1.1 --single-branch https://github.com/helidon-io/helidon-examples.git "${WS_DIR}/helidon-examples"
  "${WS_DIR}/helidon-examples"/etc/scripts/update-version.sh "${HELIDON_VERSION}"
fi

HELIDON_VERSION_IN_EXAMPLES=$(version "${WS_DIR}/helidon-examples/pom.xml")
readonly HELIDON_VERSION_IN_EXAMPLES

# Make sure the helidon version from the example repo aligns with this repository
if [ "${HELIDON_VERSION}" != "${HELIDON_VERSION_IN_EXAMPLES}" ]; then
  printf "The Helidon version in this repository (%s) does not match the Helidon version in %s (%s)\n" \
    "${HELIDON_VERSION}" \
    "${WS_DIR}/helidon-examples" \
    "${HELIDON_VERSION_IN_EXAMPLES}"
  exit 1
fi

# shellcheck disable=SC2086
mvn ${MAVEN_ARGS} \
    -f "${WS_DIR}/helidon-examples/pom.xml" \
    clean install
