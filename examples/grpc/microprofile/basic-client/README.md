
# Helidon MP gRPC Client Example

This examples shows a simple gRPC client application written using Helidon MP.

This example should be run together with the [Basic Implicit gRPC Server example](../basic-server-implicit/README.md) 
which provides the server side implementation of the `StringService` used by this client. The server should be started
before running the client.

This example shows how a client can be written without needing to write any client side gRPC code. The gRPC service that
the client will use is represented by an annotated interface `io.helidon.microprofile.grpc.example.client.StringService` 
which defines all of the methods available on the service deployed on the server.

## Build

```
mvn package
```

## Run

Ensure that the server in [Basic Implicit gRPC Server example](../basic-server-implicit/README.md) is started.
Then in this module run:
```
mvn exec:exec
```
