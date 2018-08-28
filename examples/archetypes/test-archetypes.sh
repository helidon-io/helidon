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

# Current directory
MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)

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
