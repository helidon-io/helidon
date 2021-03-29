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
-Doracle.ucp.jdbc.PoolDataSource.lrapdb.URL=jdbc:oracle:thin:@localhost:1521:XE \
-Doracle.ucp.jdbc.PoolDataSource.lrapdb.user=frank \
-Doracle.ucp.jdbc.PoolDataSource.lrapdb.password=frank \
-Doracle.ucp.jdbc.PoolDataSource.lrapdb.connectionFactoryClassName=oracle.jdbc.pool.OracleDataSource \
-Dmp.messaging.connector.helidon-aq.acknowledge-mode=CLIENT_ACKNOWLEDGE \
-Dmp.messaging.connector.helidon-aq.data-source=lrapdb \
-jar /Users/pparkins/go/src/github.com/paulparkinson/helidon/lra/coordinator/target/lra-coordinator-helidon-0.0.1-SNAPSHOT.jar

#In order to enabled logging, simply remove -Dlra.logging.enabled=false and add the datasource information. For example...
#-Doracle.ucp.jdbc.PoolDataSource.coordinatordb.URL=myurl \
#-Doracle.ucp.jdbc.PoolDataSource.coordinatordb.user=myuser \
#-Doracle.ucp.jdbc.PoolDataSource.coordinatordb.password=mypassword \