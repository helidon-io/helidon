#!/bin/bash -e
#
# Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

trap 'echo "ERROR: Error occurred at ${BASH_SOURCE}:${LINENO} command: ${BASH_COMMAND}"' ERR
set -eo pipefail

usage(){
    echo ""
    echo "Usage: `basename ${0}` [OPTIONS]"
    echo ""
    echo "Build and push Helidon docker images."
    echo ""
    echo "Options:"
    echo "  --push"
    echo "  --help print the usage and exit"
    echo ""
}

# parse command line arguments
for ((i=1;i<=${#*};i++))
{
    arg="${!i}"
    case "${arg}" in
    "--push")
        readonly PUSH="true"
        ;;
    "--help")
        usage
        exit 0
        ;;
    *)
        echo ""
        echo "ERROR: Unknown option: ${arg}"
        usage
        exit 1
        ;;
    esac
}

if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "$0")"
else
  readonly SCRIPT_PATH="${0}"
fi
readonly MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)

readonly GRAALVM_VERSION=20.0.0

docker build -f ${MY_DIR}/Dockerfile.jdk11-graalvm -t helidon/jdk11-graalvm:${GRAALVM_VERSION} ${MY_DIR}
docker build -f ${MY_DIR}/Dockerfile.jdk11-graalvm-maven -t helidon/jdk11-graalvm-maven:${GRAALVM_VERSION} ${MY_DIR}

if [ "${PUSH}" = "true" ] ; then
    docker push helidon/jdk11-graalvm:${GRAALVM_VERSION}
    docker push helidon/jdk11-graalvm-maven:${GRAALVM_VERSION}
fi
