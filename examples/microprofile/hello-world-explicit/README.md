
# Helidon MP Hello World Explicit Example

This examples shows a simple application written using Helidon MP.
It is explicit because in this example you write the `main` class
and explicitly start the microprofile server.

## Build

```
mvn package
```

## Run

```
mvn exec:java
```

Then try the endpoints:

```
curl -X GET http://localhost:7001/helloworld
curl -X GET http://localhost:7001/helloworld/another
```
