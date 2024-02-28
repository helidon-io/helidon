#!/bin/bash -e
#
# Copyright (c) 2024 Oracle and/or its affiliates.
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

# Build helidon examples, cloning the examples repository if needed

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Load pipeline environment setup and define WS_DIR
. $(dirname -- "${SCRIPT_PATH}")/includes/pipeline-env.sh "${SCRIPT_PATH}" '../..'

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup

# If needed we clone the helidon-examples repo into a subdirectory of the helidon repository
readonly HELIDON_EXAMPLES_PATH=${WS_DIR}/helidon-examples
if [ ! -d "${HELIDON_EXAMPLES_PATH}" ]; then
  git clone git@github.com:helidon-io/helidon-examples.git "${HELIDON_EXAMPLES_PATH}"
  cd "${HELIDON_EXAMPLES_PATH}"
  git checkout dev-3.x
fi

mvn ${MAVEN_ARGS} -f ${HELIDON_EXAMPLES_PATH}/pom.xml clean install
