#Long Running Actions Examples

###This readme contains the instructions to run the 3 LRA examples
1. Rest Microservice LRA Participants
2. Kafka Microservice LRA Participants
3. Oracle AQ (Advanced Queuing) Microservice LRA Participants

###The saga scenario is the same for all 3 examples:
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
- Run `./startLRACoordinatorRestExample.sh`

###Step 2 Start two servers participants
- Open another terminal and run `java -jar lra-rest-order-participant/target/lra-rest-order-participant.jar`
- Open another terminal and run `java -jar lra-rest-inventory-participant/target/lra-rest-inventory-participant.jar`

###Step 3 Call the order service and notice success scenario 
- Run `curl http://localhost:8091/order/placeOrder`
- Notice application and Helidon LRA debug messages indicating close called on the coordinator and complete called on the participants.
* Note, for convenience, inventory is not actually reduced in this example

###Step 4 Reduce the inventory level on the inventory service to 0 
- Run `curl http://localhost:8092/inventory/removeInventory`
- `curl http://localhost:8092/inventory/addInventory` can be used to add inventory back

###Step 5 Call the order service and notice failure scenario (cancel called on the coordinator and compensate called on the participants)
-Run `curl http://localhost:8091/order/placeOrder`


##Kafka Participants Example

###Step 1 Install Kafka
There are numerous ways to do this and an example docker image that is used in the Helidon Kafka messaging examples.
If nothing else this procedure can be followed...
- Download and unzip Kafka from http://kafka.apache.org/downloads.html
- Run `bin/zookeeper-server-start.sh config/zookeeper.properties`
- Run `bin/kafka-server-start.sh config/server.properties`

###Step 2 Create the Kafka topics for application and LRA protocol communication for the two services...
- Run `./createKafkaTopics.sh <KAFKA_LOCATION>` providing the Kafka install directory.
- For example `./createKafkaQueues.sh ~/Downloads/kafka_2.13-2.7.0`

###Step 2 Start the LRA Coordinator
- Run `./startLRACoordinatorKafkaExample.sh`

###Step 3 Start two servers participants
- Open another terminal and run `java -jar lra-kafka-order-participant/target/lra-kafka-order-participant.jar`
- Open another terminal and run `java -jar lra-kafka-inventory-participant/target/lra-kafka-inventory-participant.jar`

###Step 4 Send a message (using kafka-console-producer.sh) to the order service and notice success scenario (close called on the coordinator and complete called on the participants)
- Run ` <KAFKA_LOCATION>/bin/kafka-console-producer.sh --topic frontend-events --bootstrap-server localhost:9092
- Provide any value and hit `enter` to send message.
- Notice application and Helidon LRA debug messages indicating close called on the coordinator and complete called on the participants.
* Note, for convenience, inventory is not actually reduced in this example

###Step 5 Reduce the inventory level on the inventory service to 0 
- Run `curl http://localhost:8095/inventory/removeInventory`
- `curl http://localhost:8095/inventory/addInventory` can be used to add inventory back

###Step 6 Call the order service and notice failure scenario (cancel called on the coordinator and compensate called on the participants)
-Run `curl http://localhost:8091/order/placeOrder`

#AQ Participants Example


##AQ without propagation (single DB)

##Install Oracle 
There are numerous ways to do this. See other examples. todo give pointer(s)

##AQ with propagation
curl localhost:8091/setupLRA




Different participant types can be involved in the same LRA, however, 
examples currently only exhibit/describe  LRAs with participants of all one type (ie all REST, all Kafka, or all AQ)


#Mixed Participant Types
It is also possible to modify the samples to have a mix of different participant types.
All participants are already configured to start on different ports (8080, 8091, 8092, 8094, 8095, 8097, 8098) so there should be no conflict, etc. 
In order to do so the coordinator simply needs to be provided the appropriate configurations by combining the values specified in the relevant startLRACoordinator*.sh 