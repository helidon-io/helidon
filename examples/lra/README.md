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

#Long Running Actions Examples

###This readme contains the instructions to run the 3 LRA examples
1. Rest Microservice LRA Participants
2. Kafka Microservice LRA Participants
3. Oracle AQ (Advanced Queuing) Microservice LRA Participants

###The scenario is the same for all 3 examples:
1. The LRA coordinator service is configured and started. 
   * In the examples `-Dlra.logging.enabled=false` is set so that a datasource is not required.
   * Logging should be turned on in production and simply requires specifying a datasource (see documentation and start scripts for examples)
2. The Order and Inventory services are started.
3. A call is made to the order service to place an order. 
4. This Order service method is annotated with `@LRA(value = LRA.Type.REQUIRES_NEW)` thus starting an LRA and joining this Order microservice to it.
5. This Order service method in turn calls the Inventory service to reserve inventory.
6. This Inventory service method is annotated with `@LRA(value = LRA.Type.MANDATORY)` and therefore is joined to the LRA started by the Order service that called it.
7. By default the inventory does exist and so a message indicating this success is returned to the Order service.
8. In the case of the Rest example this inventory status is returned to the calling method of the Order service.
9. In the case of the messaging examples (Kafka and Oracle AQ) this inventory status is returned to a method marked `@LRA(value = LRA.Type.MANDATORY)` thus including it in the LRA.
10. As the inventory exists, there is a successful return from the Order service and an implicit `close` call is made on the coordinator.
11. The coordinator as a result calls `complete` and `afterLRA` methods on the participants (Order and Inventory service).
11. A call is then made to the Inventory service to remove inventory (set the inventory value to 0).
12. The above procedure of placing an order is repeated, however, this time there is inadequate inventory.
10. As the inventory does not exist, an exception is thrown from the Order service and an implicit `cancel` call is made on the coordinator.
11. The coordinator as a result calls `compensate` and `afterLRA` methods on the participants (Order and Inventory service). 

##REST Participants Example

###Step 1 Start the LRA Coordinator
Run `./startLRACoordinatorRestExample.sh`

###Step 2 Start two servers participants
Open another terminal and run `java -jar lra-rest-order-participant/target/lra-rest-order-participant.jar`
Open another terminal and run `java -jar lra-rest-inventory-participant/target/lra-rest-inventory-participant.jar`

###Step 3 Call the order service and notice success scenario (close called on the coordinator and complete called on the participants)
Run `curl http://localhost:8091/order/placeOrder`
* Note, for convenience, inventory is not actually reduced in this example

###Step 4 Reduce the inventory level on the inventory service to 0 
Run `curl http://localhost:8092/inventory/removeInventory`
* `curl http://localhost:8092/inventory/addInventory` can be used to add inventory back

###Step 5 Call the order service and notice failure scenario (cancel called on the coordinator and compensate called on the participants)
Run `curl http://localhost:8091/order/placeOrder`

##Kafka Participants Example

###Step 1 Install Kafka
There are numerous ways to do this. See other examples.  todo give pointer(s)

##Create topics for application as well as LRA protocol communication for the two services...
bin/kafka-topics.sh --create --topic requiresnew-service1-incoming-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic requiresnew-service1-outgoing-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic mandatory-service1-incoming-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic mandatory-service1-outgoing-events --bootstrap-server localhost:9092

order-events
bin/kafka-topics.sh --create --topic inventory-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic frontend-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic frontend-reply-events --bootstrap-server localhost:9092

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

bin/kafka-topics.sh --create --topic mandatory-service2-incoming-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic mandatory-service2-outgoing-events --bootstrap-server localhost:9092

bin/kafka-topics.sh --create --topic complete-service2-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic complete-service2-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic compensate-service2-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic compensate-service2-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic status-service2-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic status-service2-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic afterlra-service2-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic afterlra-service2-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic forget-service2-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic forget-service2-events-reply --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic leave-service2-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic leave-service2-events-reply --bootstrap-server localhost:9092

bin/kafka-console-producer.sh --topic requiresnew-incoming-events --bootstrap-server localhost:9092
bin/kafka-console-producer.sh --topic order-events --bootstrap-server localhost:9092
bin/kafka-console-producer.sh --topic frontend-events --bootstrap-server localhost:9092

java -jar lra-kafka-participant/target/lra-kafka-participant.jar
java -Dserver.port=8092 -jar lra-kafka-participant2/target/lra-kafka-participant2.jar


#AQ Participants...


##AQ without propagation (single DB)

##Install Oracle 
There are numerous ways to do this. See other examples. todo give pointer(s)

##AQ with propagation
curl localhost:8091/setupLRA




Different participant types can be involved in the same LRA, however, 
examples currently only exhibit/describe  LRAs with participants of all one type (ie all REST, all Kafka, or all AQ)