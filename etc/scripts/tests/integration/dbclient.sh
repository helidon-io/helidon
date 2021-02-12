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
if [ -h "${0}" ] ; then
    readonly SCRIPT_PATH="$(readlink "${0}")"
else
    readonly SCRIPT_PATH="${0}"
fi

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../../../.. ; pwd -P)

print_help() {
    echo 'Usage: dbclient.sh [-h] [-c] -d <database> [-j] [-n] [-p]'
    echo ''
    echo '  -h print this help and exit'
    echo '  -d <database> select database'
    echo '  -c start and stop Docker containers'
    echo '  -s execute simple tests (default when no -s, -j or -n is passed)'
    echo '  -j execute remote application tests in Java VM mode'
    echo '  -n execute remote application tests in native image mode'
    echo '  -p toggle pipeline environment mode'
}

on_error() {
    CODE="${?}" && \
    set +x && \
    printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
        "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
    docker_stop "${DOCKER_CONT_NAME}" 'FLAG_C_RUN'
}

# Evaluate command line arguments
if [ "$#" -gt '0' ]; then
    while getopts 'hcsjnpd:' flag 2> /dev/null; do
        case "${flag}" in
            h) print_help && exit;;
            c) readonly FLAG_C='1';;
            d) readonly FLAG_D=${OPTARG};;
            s) readonly FLAG_S='1';;
            j) readonly FLAG_J='1';;
            n) readonly FLAG_N='1';;
            p) readonly FLAG_P='1';;
        esac
    done
fi

# Load includes
. ${WS_DIR}/etc/scripts/tests/integration/include/env.sh
. ${WS_DIR}/etc/scripts/tests/integration/include/docker.sh
if [ -n "${FLAG_P}" ]; then
    . ${WS_DIR}/etc/scripts/tests/integration/include/pipeline.sh
fi

# Load database setup
if [ -n "${FLAG_D}" ]; then
    case "${FLAG_D}" in
        mysql) . ${WS_DIR}/etc/scripts/tests/integration/mysql.conf;;
        pgsql) . ${WS_DIR}/etc/scripts/tests/integration/pgsql.conf;;
        *)     echo 'ERROR: Unknown database name, exitting.' && exit 1;;
    esac
else
    echo 'ERROR: No database was selected, exitting.'
    exit 1
fi

# Start docker Container
if [ -n "${FLAG_C}" ]; then
    readonly DOCKER_CONT_NAME="helidon-tests-dbclient-${FLAG_D}"
    docker_start "${DOCKER_IMG}" \
                 "${DOCKER_CONT_NAME}" \
                 "${DB_HOST}:${DB_PORT}:${DB_PORT}" \
                 "${DOCKER_ENV}" \
                 'FLAG_C_RUN' \
                 'FLAG_C'
fi

# Turn simple tests on when no test was selected
[ -z "${FLAG_J}" -a -z "${FLAG_N}" -a -z "${FLAG_S}" ] && \
    readonly FLAG_S='1'

# Run simple JDBC tests
[ -n "${FLAG_S}" ] && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        echo mvn -P${DB_PROFILE} -pl common,jdbc \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
             verify && \
        mvn -P${DB_PROFILE} -pl common,jdbc \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            verify)

# Run remote application tests in Java VM mode
[ -n "${FLAG_J}" ] && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        echo mvn -P${DB_PROFILE} \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            -pl appl verify && \
        mvn -P${DB_PROFILE} \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            -pl appl verify)

# Run remote application tests in native image mode
[ -n "${FLAG_N}" ] && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        echo mvn -P${DB_PROFILE} -Pnative-image \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            -pl appl verify && \
        mvn -P${DB_PROFILE} -Pnative-image \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL}" \
            -pl appl verify)

# Stop docker Container
docker_stop "${DOCKER_CONT_NAME}" 'FLAG_C_RUN'
