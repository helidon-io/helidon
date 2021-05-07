#!/bin/bash -e
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

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Load pipeline environment setup and define WS_DIR
. $(dirname -- "${SCRIPT_PATH}")/includes/pipeline-env.sh "${SCRIPT_PATH}" '../..'

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup

# Set Graal VM into JAVA_HOME and PATH (defined in includes/pipeline-env.sh)
graalvm

print_help() {
    echo 'Usage: test-integ-example.sh [-hjn] -d <database>'
    echo ''
    echo '  -h print this help and exit'
    echo '  -j execute remote application tests in Java VM mode (default)'
    echo '  -n execute remote application tests in native image mode'
    echo '  -d <database> select database'
    echo '     <database> :: mysql | pgsql'
}

# Evaluate command line arguments
if [ "$#" -gt '0' ]; then
    while getopts 'hjnd:' flag 2> /dev/null; do
        case "${flag}" in
            h) print_help && exit;;
            d) readonly FLAG_D=${OPTARG};;
            j) readonly FLAG_J='1';;
            n) readonly FLAG_N='1';;
        esac
    done
fi

# Load database setup
if [ -n "${FLAG_D}" ]; then
    case "${FLAG_D}" in
        mysql) . ${WS_DIR}/etc/scripts/includes/mysql.sh;;
        pgsql) . ${WS_DIR}/etc/scripts/includes/pgsql.sh;;
        *)     echo 'ERROR: Unknown database name, exitting.' && exit 1;;
    esac
else
    echo 'ERROR: No database was selected, exitting.'
    exit 1
fi

# Turn simple tests on when no test was selected
[ -z "${FLAG_J}" -a -z "${FLAG_N}" ] && \
    readonly FLAG_J='1'

# Run remote application tests in Java VM mode
[ -n "${FLAG_J}" ] && \
    (cd ${WS_DIR}/tests/integration/tools/example && \
        echo mvn -P${DB_PROFILE} \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            verify && \
        mvn -P${DB_PROFILE} \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            verify)

# Run remote application tests in native image mode
[ -n "${FLAG_N}" ] && \
    (cd ${WS_DIR}/tests/integration/tools/example && \
        echo mvn -P${DB_PROFILE} -Pnative-image \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            verify && \
        mvn -P${DB_PROFILE} -Pnative-image \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            verify)
