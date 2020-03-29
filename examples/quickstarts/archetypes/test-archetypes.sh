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

# Path to this script
if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "$0")"
else
  readonly SCRIPT_PATH="${0}"
fi

# Directory this script resides in
readonly MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)

# Current directory
readonly CURRENT_DIR=${PWD}

readonly MVN_VERSION=$(mvn \
    -q \
    -f ${MY_DIR}/../pom.xml \
    -Dexec.executable="echo" \
    -Dexec.args="\${project.version}" \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

${MY_DIR}/create-archetypes.sh \
    --version=${MVN_VERSION} \
    --maven-args="install"

# cd to an innocuous directory since the archetypes will create
# a project here when we test it. See issue #64
TARGET_DIR=${MY_DIR}/target
cd ${TARGET_DIR}

rm -rf ${TARGET_DIR}/test-* || true

# invoke the helidon-quickstart-se archetype
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=${MVN_VERSION} \
    -DgroupId=com.examples \
    -DartifactId=test-helidon-quickstart-se \
    -Dpackage=com.examples.test.helidon.se

# build the generated project
mvn -f ${PWD}/test-helidon-quickstart-se/pom.xml install

# invoke the helidon-quickstart-mp archetype
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=${MVN_VERSION} \
    -DgroupId=com.examples \
    -DartifactId=test-helidon-quickstart-mp \
    -Dpackage=com.examples.test.helidon.mp

# build the generated project
mvn -f ${PWD}/test-helidon-quickstart-mp/pom.xml install

# cd back to original directory 
cd ${CURRENT_DIR}
