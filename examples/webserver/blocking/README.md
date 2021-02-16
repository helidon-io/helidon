# WebServer Blocking API Example

This example demonstrates how to use the Helidon SE WebServer blocking
APIs, with support for project Loom (https://jdk.java.net/loom/) and
its virtual threads.

There are three servers in this example:

1. `SleepingServer` that has an endpoint that sleeps for 50ms
2. `BlockingServer` using blocking APIs and invoking the sleeping service
3. `ReactiveServer` using reactive APIs and invoking the sleeping service

## Build

```
mvn package
```

## Run

First, start the server (on explicit ports, by default uses random ports):

```
java -Dservers.blocking.port=8081 -Dservers.reactive.port=8082 -jar target/helidon-examples-webserver-blocking.jar
```

Invoke the blocking endpoint

```
curl -i http://localhost:8081/call
```

Invoke the reactive endpoint

```
curl -i http://localhost:8082/call
```

## JMeter
There is a JMeter script for unix based system `run-tests.sh` that can be invoked with either `reactive` or `blocking` parameter to run appropriate tests. 
Ports expected are as in the run command above - port `8081` for blocking service, `8082` for reactive.

Invoke blocking tests:

```
./run-tests.sh blocking
```