#!/bin/bash
#
# Copyright (c) 2025 Oracle and/or its affiliates.
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

java -XX:CPUFeatures=generic -XX:CRaCEngine=warp \
    -XX:CRaCCheckpointTo=./cr -jar ./helidon-tests-integration-crac.jar &
PID=$!

# Wait until the connection is opened
until curl --output /dev/null --silent --fail "${BASE_URI}/health"; do
    sleep 0.1;
done

# Create extra pokemon
curl -H "Content-Type: application/json" --request POST --data '{"id":400, "type":1, "name":"CRaCasaur"}' "${BASE_URI}/pokemon"
# Warm-up the server by executing 200 requests against it
siege -c 2 -r 200 -b "${BASE_URI}/pokemons/400"


# Trigger the checkpoint
jcmd ./helidon-tests-integration-crac.jar JDK.checkpoint

# Wait until the process completes, returning success
# (wait would return exit code 137)
wait $PID || true