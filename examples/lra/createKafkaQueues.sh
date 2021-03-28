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

if [[ $1 == "" ]]
then
  echo Required argument Kafka install directory
  echo Usage example : ./createKafkaQueues.sh ~/Downloads/kafka_2.13-2.7.0
  exit
fi

#frontend topics (request and replies from frontend to Order Service)
$1/bin/kafka-topics.sh --create --topic frontend --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic frontend-reply --bootstrap-server localhost:9092
#order service topics
$1/bin/kafka-topics.sh --create --topic order --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lracomplete --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lracomplete-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lracompensate --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lracompensate-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lrastatus --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lrastatus-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lraafterlra --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lraafterlra-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lraforget --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lraforget-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lraleave --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic order-lraleave-reply --bootstrap-server localhost:9092
#inventory service topics
$1/bin/kafka-topics.sh --create --topic inventory --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lracomplete --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lracomplete-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lracompensate --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lracompensate-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lrastatus --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lrastatus-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lraafterlra --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lraafterlra-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lraforget --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lraforget-reply --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lraleave --bootstrap-server localhost:9092
$1/bin/kafka-topics.sh --create --topic inventory-lraleave-reply --bootstrap-server localhost:9092
