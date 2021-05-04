#!/bin/bash -e
#
# Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Load pipeline environment setup and define WS_DIR
. $(dirname -- "${SCRIPT_PATH}")/includes/pipeline-env.sh "${SCRIPT_PATH}" '../..'

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup

readonly RESULT_FILE=$(mktemp -t XXXdependency-check-result)

die() { cat ${RESULT_FILE} ; echo "Dependency report in ${WS_DIR}/target" ; echo "${1}" ; exit 1 ;}

if [ "${PIPELINE}" = "true" ] ; then
    # If in pipeline do a priming build before scan
    mvn ${MAVEN_ARGS} -f ${WS_DIR}/pom.xml clean install -DskipTests
fi

mvn ${MAVEN_ARGS} -Dorg.slf4j.simpleLogger.defaultLogLevel=WARN org.owasp:dependency-check-maven:aggregate \
        -f ${WS_DIR}/pom.xml \
        -Dtop.parent.basedir="${WS_DIR}" \
        > ${RESULT_FILE} || die "Error running the Maven command"

grep -i "One or more dependencies were identified with known vulnerabilities" ${RESULT_FILE} \
    && die "CVE SCAN ERROR" || echo "CVE SCAN OK"
