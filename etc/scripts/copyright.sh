#!/bin/bash -e
#
# Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
        "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

# Path to this script
if [ -h "${0}" ] ; then
    readonly SCRIPT_PATH="$(readlink "${0}")"
else
    readonly SCRIPT_PATH="${0}"
fi

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)

readonly RESULT_FILE=$(mktemp -t XXXcopyright-result)

source ${WS_DIR}/etc/scripts/pipeline-env.sh

die(){ echo "${1}" ; exit 1 ;}

mvn ${MAVEN_ARGS} -q org.glassfish.copyright:glassfish-copyright-maven-plugin:copyright \
        -f ${WS_DIR}/pom.xml \
        -Dcopyright.exclude="${WS_DIR}/etc/copyright-exclude.txt" \
        -Dcopyright.template="${WS_DIR}/etc/copyright.txt" \
        -Dcopyright.scm="git" \
        -Pexamples,docs,ossrh-releases,tests > ${RESULT_FILE} || die "Error running the Maven command"

grep -i "copyright" ${RESULT_FILE} \
    && die "COPYRIGHT ERROR" || echo "COPYRIGHT OK"
