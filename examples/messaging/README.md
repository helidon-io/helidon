# Helidon Messaging Examples

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

### Test JMS server
* Start ActiveMQ server locally: 
```bash
docker run --name='activemq' -p 61616:61616 -p 8161:8161 rmohr/activemq
```

### Test Oracle database
* Start ActiveMQ server locally: 
```bash
cd ./docker/oracle-aq-18-xe
./buildAndRun.sh
```

For stopping Oracle database container use:
```bash
cd ./docker/oracle-aq-18-xe
./stopAndClean.sh
```

## Helidon SE Reactive Messaging with Kafka Example
For demonstration of Helidon SE Messaging with Kafka connector, 
continue to [Kafka with WebSocket SE Example](kafka-websocket-se/README.md)

## Helidon MP Reactive Messaging with Kafka Example
For demonstration of Helidon MP Messaging with Kafka connector, 
continue to [Kafka with WebSocket MP Example](kafka-websocket-mp/README.md)

## Helidon MP Reactive Messaging with JMS Example
For demonstration of Helidon MP Messaging with JMS connector, 
continue to [JMS with WebSocket MP Example](jms-websocket-mp/README.md)

## Helidon MP Reactive Messaging with Oracle AQ Example
For demonstration of Helidon MP Messaging with Oracle Advance Queueing connector, 
continue to [Oracle AQ with WebSocket MP Example](oracle-aq-websocket-mp/README.md)


