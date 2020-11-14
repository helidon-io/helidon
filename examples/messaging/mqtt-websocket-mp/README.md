# Helidon Messaging with MQTT Example

## Prerequisites
* Java 11+
* Docker
* MQTT broker running on `localhost:9001`

## Start local Eclipse Mosquitto broker
`docker run -it -p 1883:1883 -p 9001:9001 eclipse-mosquitto`

## Build & Run
1. `mvn clean install`
2. `java -jar mqtt-websocket-mp.jar`
3. Visit http://localhost:7001

