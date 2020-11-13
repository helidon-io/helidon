#!/bin/bash
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

if [[ "$(docker images -q helidon-test-kafka 2>/dev/null)" == "" ]]; then
  # helidon:test-kafka not found, build it
  docker build ./docker/kafka -t helidon-test-kafka -f ./docker/kafka/Dockerfile.kafka
fi

if [ ! "$(docker ps -q -f name=helidon_kafka)" ]; then
  if [ "$(docker ps -aq -f status=exited -f name=helidon_kafka)" ]; then
    # Clean up exited container
    docker rm helidon_kafka
  fi
  # Run test Kafka in new container, stop it by pressing Ctrl+C
  docker run -it --name helidon_kafka --network="host" helidon-test-kafka
fi
# Clean up exited container
docker rm helidon_kafka
