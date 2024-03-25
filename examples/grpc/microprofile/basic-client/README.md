
# Helidon MP gRPC Client Example

This examples shows a simple gRPC client application written using Helidon MP.

This example should be run together with the [Basic Implicit gRPC Server example](../basic-server-implicit/README.md) 
which provides the server side implementation of the `StringService` used by this client. The server should be started
before running the client.

This example shows how a client can be written without needing to write any client side gRPC code. The gRPC service that
the client will use is represented by an annotated interface `io.helidon.microprofile.grpc.example.client.StringService` 
which defines all of the methods available on the service deployed on the server.

## Build
```shell
mvn -f ../../pom.xml -pl common,microprofile/basic-client package
```

## Run
Ensure that the server in [Basic Implicit gRPC Server example](../basic-server-implicit/README.md) is started.
Then in this module run:
```shell
java -jar target/helidon-examples-grpc-microprofile-client.jar 
```
Sample output from the client:
```text
...
Unary Lower response: 'abcd'
Response from blocking Split: 'A'
Response from blocking Split: 'B'
Response from blocking Split: 'C'
Response from blocking Split: 'D'
Response from blocking Split: 'E'
Join response: 'A B C D'
Response from Echo: 'A'
Response from Echo: 'B'
Response from Echo: 'C'
Response from Echo: 'D'
...
```
