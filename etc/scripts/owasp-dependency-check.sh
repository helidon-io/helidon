#!/bin/bash -e
#
# Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

on_error(){
    CODE="${?}" && \
    set +x && \
    printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
        "${CODE}" "${BASH_SOURCE[0]}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

RESULT_FILE=$(mktemp -t XXXdependency-check-result)
readonly  RESULT_FILE

die() { cat "${RESULT_FILE}" ; echo "Dependency report in ${WS_DIR}/target" ; echo "${1}" ; exit 1 ;}

if [ "${PIPELINE}" = "true" ] ; then
    # If in pipeline do a priming build before scan
    # shellcheck disable=SC2086
    mvn ${MAVEN_ARGS} -f "${WS_DIR}"/pom.xml clean install -DskipTests
fi

# Setting NVD_API_KEY is not required but improves behavior of NVD API throttling

# shellcheck disable=SC2086
mvn ${MAVEN_ARGS} -Dorg.slf4j.simpleLogger.defaultLogLevel=WARN org.owasp:dependency-check-maven:aggregate \
        -f "${WS_DIR}"/pom.xml \
        -Dtop.parent.basedir="${WS_DIR}" \
        -Dnvd-api-key="${NVD_API_KEY}" \
        > "${RESULT_FILE}" || die "Error running the Maven command"

grep -i "One or more dependencies were identified with known vulnerabilities" "${RESULT_FILE}" \
    && die "CVE SCAN ERROR" || echo "CVE SCAN OK"
