#!/bin/bash -e
#
# Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

function cleanup() {
  kill "${NATIVE_PID}" || echo "Process not found"
}

trap cleanup EXIT

MODULE_DIR="examples/quickstarts/helidon-quickstart-se"
NATIVE_IMAGE="helidon-quickstart-se"

cd "$MODULE_DIR"

# start the native image
./target/${NATIVE_IMAGE} > native-output.txt 2>&1 &
NATIVE_PID=$!

sleep 1

# and now run the tests
echo '************************************'
echo '**  Running tests:                **'
echo '************************************'
echo '* /greet                           *'
echo '************************************'
curl -i -f http://localhost:8080/greet
echo
echo '************************************'
echo '* /greet/unit-tests                *'
echo '************************************'
echo
curl -i -f http://localhost:8080/greet/unit-tests
echo
echo '************************************'
echo '* /health                          *'
echo '************************************'
curl -i -f http://localhost:8080/health
echo
echo '************************************'
echo '* /health/ready                    *'
echo '************************************'
curl -i -f http://localhost:8080/health/ready
echo
echo '************************************'
echo '* /health/live                     *'
echo '************************************'
curl -i -f http://localhost:8080/health/live
echo
echo '************************************'
echo '* /metrics (Prometheus)            *'
echo '************************************'
curl -i -f http://localhost:8080/metrics
echo
echo '************************************'
echo '* /metrics (JSON)                  *'
echo '************************************'
curl -i -f -H "Accept: application/json" http://localhost:8080/metrics
echo

echo '************************************'
echo '**  Server output:                **'
echo '************************************'
cat ./native-output.txt
