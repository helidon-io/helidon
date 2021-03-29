#!/bin/bash
#
# Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

#
# Wait for Kafka to start and create test topics:
# topic messaging-test-topic-1 and topic messaging-test-topic-2
#

ZOOKEEPER_URL=localhost:2181
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh --if-not-exists --zookeeper $ZOOKEEPER_URL"

while sleep 2; do
  brokers=$(echo dump | nc localhost 2181 | grep brokers | wc -l)
  echo "Checking if Kafka is up: ${brokers}"
  if [[ "$brokers" -gt "0" ]]; then
    echo "KAFKA IS UP !!!"

    echo "Creating test topics"
#frontend topics (request and replies from frontend to Order Service)
    bash $KAFKA_TOPICS --create --topic frontend
    bash $KAFKA_TOPICS --create --topic frontend-reply
#order service topics
    bash $KAFKA_TOPICS --create --topic order
    bash $KAFKA_TOPICS --create --topic order-lracomplete
    bash $KAFKA_TOPICS --create --topic order-lracomplete-reply
    bash $KAFKA_TOPICS --create --topic order-lracompensate
    bash $KAFKA_TOPICS --create --topic order-lracompensate-reply
    bash $KAFKA_TOPICS --create --topic order-lrastatus
    bash $KAFKA_TOPICS --create --topic order-lrastatus-reply
    bash $KAFKA_TOPICS --create --topic order-lraafterlra
    bash $KAFKA_TOPICS --create --topic order-lraafterlra-reply
    bash $KAFKA_TOPICS --create --topic order-lraforget
    bash $KAFKA_TOPICS --create --topic order-lraforget-reply
    bash $KAFKA_TOPICS --create --topic order-lraleave
    bash $KAFKA_TOPICS --create --topic order-lraleave-reply
#inventory service topics
    bash $KAFKA_TOPICS --create --topic inventory
    bash $KAFKA_TOPICS --create --topic inventory-lracomplete
    bash $KAFKA_TOPICS --create --topic inventory-lracomplete-reply
    bash $KAFKA_TOPICS --create --topic inventory-lracompensate
    bash $KAFKA_TOPICS --create --topic inventory-lracompensate-reply
    bash $KAFKA_TOPICS --create --topic inventory-lrastatus
    bash $KAFKA_TOPICS --create --topic inventory-lrastatus-reply
    bash $KAFKA_TOPICS --create --topic inventory-lraafterlra
    bash $KAFKA_TOPICS --create --topic inventory-lraafterlra-reply
    bash $KAFKA_TOPICS --create --topic inventory-lraforget
    bash $KAFKA_TOPICS --create --topic inventory-lraforget-reply
    bash $KAFKA_TOPICS --create --topic inventory-lraleave
    bash $KAFKA_TOPICS --create --topic inventory-lraleave-reply
    echo
    echo "Example topics messaging-test-topic-1 and messaging-test-topic-2 created"
    echo
    echo "================== Kafka is ready, stop it with Ctrl+C =================="
    exit 0
  fi
done
