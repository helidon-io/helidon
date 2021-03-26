/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

Any number of services can be started by just passing different -Dserver.port=8091  values
and then provide the flow in the front end service
Examples...
 1. call REQUIRESNEW on restMS1 (localhost:8091) which should in turn call MANDATORY on restMS2 (localhost:8092)
For Messaging some changes to microprofile-config.properties are required.
 2. call REQUIRESNEW on aqMessagingMS1 (localhost:8091) which should in turn call MANDATORY on restMS2 (localhost:8092)
 1. call REQUIRESNEW on restMS1 (localhost:8091) which should in turn call MANDATORY on restMS2 (localhost:8092)




**Kafka

bin/kafka-topics.sh --create --topic order-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic inventory-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic complete-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic complete-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic compensate-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic compensate-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic status-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic status-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic afterlra-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic afterlra-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic forget-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic forget-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic leave-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic leave-events-reply --bootstrap-server localhost:9092

bin/kafka-console-producer.sh --topic order-events --bootstrap-server localhost:9092

**AQ without propagation (single DB)

**AQ with propagation
curl localhost:8091/setupLRA


define edn_user=dev12c_soainfra 
define topic=EDN_AQJMS_CUST_TOPIC 
define topic_table=EDN_AQJMS_CUST_TOPIC_TBL

  
begin 
  DBMS_AQADM.stop_queue(queue_name => ‘&edn_user..&topic’); 
  DBMS_AQADM.drop_queue(queue_name => ‘&edn_user..&topic’); 
  DBMS_AQADM.drop_queue_table(queue_table => ‘&edn_user..&topic_table’); 
end; 
/ 
begin 
  dbms_aqadm.create_queue_table(queue_table => ‘&edn_user..&topic_table’, 
                                queue_payload_type => ‘SYS.AQ$_JMS_MESSAGE’, 
                                multiple_consumers => true); 
  dbms_aqadm.create_queue(queue_name => ‘&edn_user..&topic’, 
                          queue_table => ‘&edn_user..&topic_table’, 
                          max_retries => 256); 
  dbms_aqadm.start_queue(queue_name        =>       ‘&edn_user..&topic’); 
end; 
/ 
commit;