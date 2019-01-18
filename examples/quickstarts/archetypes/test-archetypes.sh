#!/bin/bash
#
# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

set -eo pipefail

# Path to this script
if [ -h "${0}" ] ; then
  SCRIPT_PATH="$(readlink "$0")"
else
  SCRIPT_PATH="${0}"
fi

# Directory this script resides in
MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)
# Current directory
CURRENT_DIR=${PWD}

# cd to an innocuous directory since the archetypes will create
# a project here when we test it. See issue #64
TARGET_DIR=${MY_DIR}/target
mkdir -p ${TARGET_DIR}
cd ${TARGET_DIR}

${MY_DIR}/create-archetypes.sh

MVN_VERSION=$(mvn \
    -q \
    -f ${MY_DIR}/../pom.xml \
    -Dexec.executable="echo" \
    -Dexec.args="\${project.version}" \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

# invoke the quickstart-se archetype
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=${MVN_VERSION} \
    -DgroupId=io.helidon.examples \
    -DartifactId=quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se

# build the generated project
mvn -f ${PWD}/quickstart-se/pom.xml install

# invoke the quickstart-mp archetype
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=${MVN_VERSION} \
    -DgroupId=io.helidon.examples \
    -DartifactId=quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp

# build the generated project
mvn -f ${PWD}/quickstart-mp/pom.xml install

# Paranoia. Don't want to delete /!
if [ ! -z "${TARGET_DIR}" ]; then
    rm -rf ${TARGET_DIR}/
fi

# cd back to original directory 
cd ${CURRENT_DIR}
