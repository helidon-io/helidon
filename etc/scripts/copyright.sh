#!/bin/bash -e
#
# Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

readonly LOG_FILE=$(mktemp -t XXXcopyright-log)

readonly RESULT_FILE=$(mktemp -t XXXcopyright-result)

die() { echo "${1}" ; exit 1 ;}

which mvn

mvn ${MAVEN_ARGS} \
        -f ${WS_DIR}/pom.xml \
        -Dhelidon.enforcer.output.file="${RESULT_FILE}" \
        -Dhelidon.enforcer.rules=copyright \
        -Dhelidon.enforcer.failOnError=false \
        -Pcopyright \
        -N \
        validate > ${LOG_FILE} 2>&1 || (cat ${LOG_FILE} ; exit 1)

grep "^\[ERROR\]" ${RESULT_FILE} \
    && die "COPYRIGHT ERROR" || echo "COPYRIGHT OK"
