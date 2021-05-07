#!/bin/bash -e
#
# Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

mvn ${MAVEN_ARGS} --version

mvn ${MAVEN_ARGS} -f ${WS_DIR}/pom.xml \
    clean install -e \
    -Dmaven.test.failure.ignore=true \
    -Pexamples,archetypes,spotbugs,javadoc,sources,tck,tests,pipeline

#
# test running from jar file, and then from module path
#
# The first integration test tests all MP features except for JPA/JTA
# with multiple JAX-RS applications including security
tests/integration/native-image/mp-1/test-runtime.sh
# The third integration test tests Helidon Quickstart MP
tests/integration/native-image/mp-3/test-runtime.sh

# Build site and agregated javadocs
mvn ${MAVEN_ARGS} -f ${WS_DIR}/pom.xml site
