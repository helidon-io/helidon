#!/bin/bash
#
# Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

LOG_FILE=$(mktemp -t XXXcheckstyle-log)
readonly LOG_FILE

RESULT_FILE=$(mktemp -t XXXcheckstyle-result)
readonly  RESULT_FILE

die(){ echo "${1}" ; exit 1 ;}

# Remove cache
rm -f "${WS_DIR}"/target/checkstyle-*

# shellcheck disable=SC2086
mvn ${MVN_ARGS} checkstyle:checkstyle-aggregate \
    -f "${WS_DIR}"/pom.xml \
    -Dcheckstyle.output.format="plain" \
    -Dcheckstyle.output.file="${RESULT_FILE}" \
    > ${LOG_FILE} 2>&1 || (cat ${LOG_FILE} ; exit 1)

grep "^\[ERROR\]" "${RESULT_FILE}" \
    && die "CHECKSTYLE ERROR" || echo "CHECKSTYLE OK"
