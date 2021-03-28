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

java -Dlra.logging.enabled=false \
-Dmp.messaging.connector.helidon-kafka.bootstrap.servers=localhost:9092 \
-Dmp.messaging.connector.helidon-kafka.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer \
-Dmp.messaging.connector.helidon-kafka.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer \
-Dmp.messaging.connector.helidon-kafka.key.serializer=org.apache.kafka.common.serialization.StringSerializer \
-Dmp.messaging.connector.helidon-kafka.value.serializer=org.apache.kafka.common.serialization.StringSerializer \
-Doracle.ucp.jdbc.PoolDataSource.orderpdb.URL=jdbc:oracle:thin:@orderdb_tp?TNS_ADMIN=/Users/pparkins/Downloads/Wallet_ORDERDB \
-Doracle.ucp.jdbc.PoolDataSource.orderpdb.user=orderuser \
-Doracle.ucp.jdbc.PoolDataSource.orderpdb.password=Welcome12345 \
-Doracle.ucp.jdbc.PoolDataSource.orderpdb.connectionFactoryClassName=oracle.jdbc.pool.OracleDataSource \
-Dmp.messaging.connector.helidon-aq.acknowledge-mode=CLIENT_ACKNOWLEDGE \
-Dmp.messaging.connector.helidon-aq.data-source=orderpdb \
-jar /Users/pparkins/go/src/github.com/paulparkinson/helidon/lra/coordinator/target/lra-coordinator-helidon-0.0.1-SNAPSHOT.jar

#In order to enabled logging, simply remove -Dlra.logging.enabled=false and add the datasource information. For example...
#-Doracle.ucp.jdbc.PoolDataSource.coordinatordb.URL=myurl \
#-Doracle.ucp.jdbc.PoolDataSource.coordinatordb.user=myuser \
#-Doracle.ucp.jdbc.PoolDataSource.coordinatordb.password=mypassword \