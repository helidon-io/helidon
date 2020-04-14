#!/bin/bash -e
#
# Copyright (c) 2020 Oracle and/or its affiliates.
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

# This file is called from /etc/scripts/build.sh

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

# cd the my dir, so we can start the application with correct current directory
cd "${MY_DIR}"

# build the binary
mvn clean package -DskipTests

# Attempt to run this example as a java -jar
# This is a self-testing application

java -jar target/helidon-tests-native-image-mp-1.jar

# Attempt to run this example as a java with module path

java --module-path target/helidon-tests-native-image-mp-1.jar:target/libs \
     -m helidon.tests.nimage.mp/io.helidon.tests.integration.nativeimage.mp1.Mp1Main
