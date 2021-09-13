#!/bin/bash -ex
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
. $(dirname -- "${SCRIPT_PATH}")/includes/pipeline-env.sh "${SCRIPT_PATH}" '../..'

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup

if [ -z "${GRAALVM_HOME}" ]; then
    echo "ERROR: GRAALVM_HOME is not set";
    exit 1
fi

if [ ! -x "${GRAALVM_HOME}/bin/native-image" ]; then
    echo "ERROR: ${GRAALVM_HOME}/bin/native-image does not exist or is not executable";
    exit 1
fi

mvn ${MAVEN_ARGS} --version
echo "GRAALVM_HOME=${GRAALVM_HOME}";
${GRAALVM_HOME}/bin/native-image --version;

# Temporary workaround until job stages will share maven repository
mvn ${MAVEN_ARGS} -f ${WS_DIR}/pom.xml \
    install -e \
    -Dmaven.test.skip=true \
    -DskipTests \
    -Ppipeline

# Run native image tests
cd ${WS_DIR}/tests/integration/native-image

# Prime build all native-image tests
mvn ${MAVEN_ARGS} clean install 

# Build native images
# mp-2 is too big, waiting for more memory
readonly native_image_tests="se-1 mp-1 mp-3"
for native_test in ${native_image_tests}; do
    cd ${WS_DIR}/tests/integration/native-image/${native_test}
    mvn ${MAVEN_ARGS} clean package -Pnative-image 
done

# Run this one because it has no pre-reqs and self-tests
# Uses relative path to read configuration
cd ${WS_DIR}/tests/integration/native-image/mp-1
${WS_DIR}/tests/integration/native-image/mp-1/target/helidon-tests-native-image-mp-1
