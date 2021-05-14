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
. $(dirname -- "${SCRIPT_PATH}")/../../../etc/scripts/includes/docker-env.sh "${SCRIPT_PATH}" '../../..'

# Local error handler
test_on_error() {
    CODE="${?}" && \
    set +x && \
    printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
        "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
    docker_stop "${DOCKER_CONT_NAME}" 'FLAG_C_RUN'
}

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup 'test_on_error'

print_help() {
    echo 'Usage: test.sh [-hcsjn] -d <database>'
    echo ''
    echo '  -h print this help and exit'
    echo '  -c start and stop Docker containers'
    echo '  -k keep docker container running (do not stop it)'
    echo '  -i <image> override Docker image name:version'
    echo '  -s execute simple tests (default when no -s, -j or -n is passed)'
    echo '  -e execute JSON extension tests'
    echo '  -j execute remote application tests in Java VM mode'
    echo '  -n execute remote application tests in native image mode'
    echo '  -d <database> select database'
    echo '     <database> :: mysql | pgsql'
    echo '  -t <directory> set tnsnames.ora directory and pass it to test process'
    echo '  -u <url> overwrite database URL'
    echo '  -a <user_name> overwrite admin user name'
    echo '  -p <password> overwrite admin user password'
    echo '  -x turn tests debugging on port 8787 on'
}

# Evaluate command line arguments
if [ "$#" -gt '0' ]; then
    while getopts 'hcksejnxd:i:t:u:a:p:' flag 2> /dev/null; do
        case "${flag}" in
            h) print_help && exit;;
            a) readonly FLAG_A=${OPTARG};;
            p) readonly FLAG_P=${OPTARG};;
            c) readonly FLAG_C='1';;
            k) readonly FLAG_K='1';;
            i) readonly FLAG_I=${OPTARG};;
            d) readonly FLAG_D=${OPTARG};;
            t) readonly FLAG_T=${OPTARG};;
            u) readonly FLAG_U=${OPTARG};;
            s) readonly FLAG_S='1';;
            e) readonly FLAG_E='1';;
            j) readonly FLAG_J='1';;
            n) readonly FLAG_N='1';;
            x) readonly DEBUG_ARGS='-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8787 -Xnoagent -Djava.compiler=NONE';;
        esac
    done
fi

# Load database setup
if [ -n "${FLAG_D}" ]; then
    case "${FLAG_D}" in
        mysql) . ${WS_DIR}/etc/scripts/includes/mysql.sh;;
        pgsql) . ${WS_DIR}/etc/scripts/includes/pgsql.sh;;
        oradb) . ${WS_DIR}/etc/scripts/includes/oradb.sh "${FLAG_I}";;
        ora21c) . ${WS_DIR}/etc/scripts/includes/oradb.sh "${FLAG_I}" && readonly PROF_21C='-P21c';;
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

# Set oracle.net.tns_admin for Oracle database
if [ -n "${FLAG_T}" ]; then
    readonly TNSNAMES_PROP="-Doracle.net.tns_admin=${FLAG_T}"
    echo "Using oracle.net.tns_admin: ${FLAG_T}"
fi

# Update database URL if passed
if [ -n "${FLAG_U}" ]; then
    readonly DB_URL_PROP="${FLAG_U}"
    echo "Database URL updated: ${DB_URL_PROP}"
else
    readonly DB_URL_PROP="${DB_URL}"
fi

# Owerwrite DBA user and password
if [ -n "${FLAG_A}" ]; then
    readonly DBA_USER_PROP="${FLAG_A}"
else
    readonly DBA_USER_PROP="${DBA_USER}"
fi
if [ -n "${FLAG_P}" ]; then
    readonly DBA_PASSWORD_PROP="${FLAG_P}"
else
    readonly DBA_PASSWORD_PROP="${DBA_PASSWORD}"
fi

# Turn simple tests on when no test was selected
[ -z "${FLAG_J}" -a -z "${FLAG_N}" -a -z "${FLAG_S}" -a -z "${FLAG_E}" ] && \
    readonly FLAG_S='1'

# Run simple JDBC tests
[ -n "${FLAG_S}" ] && \
    echo 'Simple JDBC tests' && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        mvn -P${DB_PROFILE} -pl common,jdbc \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user="${DB_USER}" \
            -Ddb.password="${DB_PASSWORD}" \
            -Ddba.user="${DBA_USER_PROP}" \
            -Ddba.password="${DBA_PASSWORD_PROP}" \
            -Ddb.url="${DB_URL_PROP}" \
            -Dmaven.failsafe.debug="${DEBUG_ARGS}" \
            ${TNSNAMES_PROP} \
            verify)

# Run oracle extension JDBC tests
[ -n "${FLAG_E}" ] && [ "${FLAG_D}" = "oradb" -o "${FLAG_D}" = "ora21c" -o "${FLAG_D}" = "mysql" -o "${FLAG_D}" = "pgsql" ] && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        echo 'JDBC tests build' && \
        mvn -P${DB_PROFILE} -pl common,jdbc \
            -Pdbcheck -DskipIT \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user="${DB_USER}" \
            -Ddb.password="${DB_PASSWORD}" \
            -Ddba.user="${DBA_USER_PROP}" \
            -Ddba.password="${DBA_PASSWORD_PROP}" \
            -Ddb.url="${DB_URL_PROP}" \
            -Dmaven.failsafe.debug="${DEBUG_ARGS}" \
            ${TNSNAMES_PROP} \
            verify && \
        echo 'JDBC Oracle extension tests' && \
        mvn -P${DB_PROFILE} ${PROF_21C} -pl jdbc-json \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user="${DB_USER}" \
            -Ddb.password="${DB_PASSWORD}" \
            -Ddba.user="${DBA_USER_PROP}" \
            -Ddba.password="${DBA_PASSWORD_PROP}" \
            -Ddb.url="${DB_URL_PROP}" \
            -Dmaven.failsafe.debug="${DEBUG_ARGS}" \
            ${TNSNAMES_PROP} \
            verify)

# Run remote application tests in Java VM mode
[ -n "${FLAG_J}" ] && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        mvn -P${DB_PROFILE} \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL_PROP}" \
            -pl appl verify)

# Run remote application tests in native image mode
[ -n "${FLAG_N}" ] && \
    (cd ${WS_DIR}/tests/integration/dbclient && \
        mvn -P${DB_PROFILE} -Pnative-image \
            -Dapp.config=${TEST_CONFIG} \
            -Ddb.user=${DB_USER} \
            -Ddb.password=${DB_PASSWORD} \
            -Ddb.url="${DB_URL_PROP}" \
            -pl appl verify)

# Stop docker Container
if [ -z "${FLAG_K}" ]; then
    docker_stop "${DOCKER_CONT_NAME}" 'FLAG_C_RUN'
fi
