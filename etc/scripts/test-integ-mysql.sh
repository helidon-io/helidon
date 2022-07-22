#!/bin/bash -e
#
# Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

# Load OCI-related functions. WS_DIR is already defined, so there is
# no need to pass arguments.
. $(dirname -- "${SCRIPT_PATH}")/includes/oci.sh

# Set Graal VM into JAVA_HOME and PATH (defined in includes/pipeline-env.sh)
graalvm

mvn ${MAVEN_ARGS} --version

# Install OCI shaded full jar, if necessary. This is an idempotent
# call.
install_oci_shaded_full_jar

# Temporary workaround until job stages will share maven repository
mvn ${MAVEN_ARGS} -f ${WS_DIR}/pom.xml \
    clean install -e \
    -DskipTests \
    -Ppipeline,oci-sdk-cdi

# Run tests in Java VM application
(cd tests/integration/jpa && \
  mvn ${MAVEN_ARGS} clean verify \
      -Dmaven.test.failure.ignore=true -Dmysql \
      -pl model,appl)

# Run tests in native image application
(cd tests/integration/jpa && \
  mvn ${MAVEN_ARGS} clean verify \
      -Dmaven.test.failure.ignore=true -Dmysql \
      -Pnative-image -Dnative-image -pl model,appl)
