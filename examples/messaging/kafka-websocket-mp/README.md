# Helidon MP Reactive Messaging with Kafka Example

## Prerequisites
* Docker
* Java 11+ 
* [Kafka bootstrap server](../README.md) running on `localhost:9092`

## Build & Run
```shell
mvn clean install
java -jar target/kafka-websocket-mp.jar
```
Visit http://localhost:7001