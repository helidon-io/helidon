# Helidon Messaging with Kafka Examples

## Prerequisites
* Docker
* Java 11+

### Test Kafka server
To make examples easily runnable, 
small, pocket size and pre-configured testing Kafka server Docker image is available. 

* To run it locally: `./kafkaRun.sh`
  * Pre-configured topics:
    * `messaging-test-topic-1`
    * `messaging-test-topic-2`
  * Stop it with `Ctrl+c`
  
* Send messages manually with: `./kafkaProduce.sh [topic-name]`
* Consume messages manually with: `./kafkaConsume.sh [topic-name]`

## Helidon SE Reactive Messaging with Kafka Example
For demonstration of Helidon SE Messaging with Kafka connector, 
continue to [Kafka with WebSocket SE Example](kafka-websocket-se/README.md)

## Helidon MP Reactive Messaging with Kafka Example
For demonstration of Helidon MP Messaging with Kafka connector, 
continue to [Kafka with WebSocket MP Example](kafka-websocket-mp/README.md)


