#!/bin/bash

#
# Copyright (c) 2026 Oracle and/or its affiliates.
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

set -euo pipefail

SERVER_LOG_DIR="/logs/server"
CLIENT_LOG_DIR="/logs/client"
SERVER_LOG_FILE="${SERVER_LOG_DIR}/helidon-http3.log"
CLIENT_LOG_FILE="${CLIENT_LOG_DIR}/helidon-client.log"
APP_JAR="/opt/helidon/app.jar"

mkdir -p "${SERVER_LOG_DIR}" "${CLIENT_LOG_DIR}"

log() {
    local message=$1
    echo "[helidon-qir] ${message}"
}

unsupported() {
    local message=$1
    log "${message}"
    exit 127
}

if [[ "${HELIDON_QUIC_INTEROP_SKIP_SETUP:-0}" != "1" && -x /setup.sh ]]; then
    /setup.sh
fi

role="${ROLE:-}"
testcase="${TESTCASE:-}"

case "${role}" in
    server)
        ;;
    client)
        exec > >(tee -a "${CLIENT_LOG_FILE}") 2>&1
        unsupported "Client role is not implemented in the Helidon HTTP/3 interop image."
        ;;
    "")
        log "ROLE must be set."
        exit 1
        ;;
    *)
        unsupported "Unsupported ROLE: ${role}"
        ;;
esac

case "${testcase}" in
    http3)
        ;;
    "")
        log "TESTCASE must be set."
        exit 1
        ;;
    *)
        exec > >(tee -a "${SERVER_LOG_FILE}") 2>&1
        unsupported "Unsupported testcase for the Helidon HTTP/3 interop image: ${testcase}"
        ;;
esac

if [[ ! -f "${APP_JAR}" ]]; then
    log "Missing application jar: ${APP_JAR}"
    exit 1
fi

if [[ -n "${QLOGDIR:-}" ]]; then
    mkdir -p "${QLOGDIR}"
fi

exec > >(tee -a "${SERVER_LOG_FILE}") 2>&1

log "Starting Helidon HTTP/3 interop server."
log "ROLE=${role} TESTCASE=${testcase}"

exec java ${JAVA_OPTS:-} \
    -jar "${APP_JAR}"
