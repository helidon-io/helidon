#!/bin/bash -e
#
# Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
    echo "Usage: `basename ${0}` [OPTIONS] --version=XXX"
    echo ""
    echo "Create archetypes from the quickstart examples."
    echo "Parameter:"
    echo "--version=XXX current Helidon version"
    echo ""
    echo "Options:"
    echo "  --maven-args=XXX arguments for the maven command to execute post generation (default is install)"
    echo "  --help print the usage and exit"
    echo ""
}

# parse command line arguments
for ((i=1;i<=${#*};i++))
{
    arg="${!i}"
    case "${arg}" in
    "--version="*)
        readonly HELIDON_VERSION="${arg#*=}"
        ;;
    "--maven-args="*)
        readonly MAVEN_ARGS="${arg#*=}"
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

echo ""
MISSING_PARAMS=false
if [ -z "${HELIDON_VERSION}" ] ; then
    echo "ERROR: Missing required parameter --version"
    MISSING_PARAMS=true
fi
if ${MISSING_PARAMS} ; then
    usage
    exit 1
fi

if [ -z "${MAVEN_ARGS}" ] ; then
    readonly MAVEN_ARGS="install"
fi

if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "$0")"
else
  readonly SCRIPT_PATH="${0}"
fi
readonly MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)
readonly EXAMPLE_DIR=$(cd "${MY_DIR}/.." ; pwd -P)

${MY_DIR}/create-archetype.sh \
    --clean \
    --projectdir="${EXAMPLE_DIR}/helidon-quickstart-se" \
    --groupid="io.helidon.examples" \
    --artifactid="helidon-quickstart-se" \
    --version="${HELIDON_VERSION}" \
    --package="io.helidon.examples.quickstart.se" \
    --name="Helidon Quickstart SE Example" \
    --archetype-name="Helidon Quickstart SE Archetype" \
    --archetype-description="Quickstart archetype for Helidon SE" \
    --archetype-groupid=io.helidon.archetypes \
    --maven-args="${MAVEN_ARGS}"

${MY_DIR}/create-archetype.sh \
    --clean \
    --projectdir="${EXAMPLE_DIR}/helidon-quickstart-mp" \
    --groupid="io.helidon.examples" \
    --artifactid="helidon-quickstart-mp" \
    --version="${HELIDON_VERSION}" \
    --package="io.helidon.examples.quickstart.mp" \
    --name="Helidon Quickstart MP Example" \
    --archetype-name="Helidon Quickstart MP Archetype" \
    --archetype-description="Quickstart archetype for Helidon MP" \
    --archetype-groupid=io.helidon.archetypes \
    --maven-args="${MAVEN_ARGS}"