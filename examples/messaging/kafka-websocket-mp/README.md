# Helidon MP Reactive Messaging with Kafka Example

## Prerequisites
* Docker
* Java 21+ 
* [Kafka bootstrap server](../README.md) running on `localhost:9092`

## Build & Run
```shell
#1.
mvn clean package
#2.
java -jar target/kafka-websocket-mp.jar
```
3. Visit http://localhost:7001