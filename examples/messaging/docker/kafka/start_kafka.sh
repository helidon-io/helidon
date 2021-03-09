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
# Start Zookeeper, wait for it to come up and start Kafka.
#

# Allow ruok
echo "4lw.commands.whitelist=*" >>/opt/kafka/config/zookeeper.properties

# Start Zookeeper
/opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties &

while sleep 2; do
  isOk=$(echo ruok | nc localhost 2181)
  echo "Checking if Zookeeper is up: ${isOk}"
  if [ "${isOk}" = "imok" ]; then
     echo "ZOOKEEPER IS UP !!!"
    break
  fi
done

# Create test topics when Kafka is ready
/opt/kafka/init_topics.sh &

# Start Kafka
/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
state=$?
if [ $state -ne 0 ]; then
  echo "Kafka stopped."
  exit $state
fi

# Keep Kafka up till Ctrl+C
read ;
