
# Helidon MP Implicit gRPC Server Example

This examples shows a simple gRPC application written using Helidon MP.
It is implicit because in this example you don't write the
`main` class, instead you rely on the Microprofile gRPC Server main class.

The gRPC services to deploy will be discovered by CDI when the gRPC server starts.
The `StringService` is a POJO service implementation that is annotated with the
CDI qualifier `GrpcService` so that it can be discovered.

Two additional services (`GreetService` and `EchoService`) that are not normally CDI
managed beans are manually added as CDI managed beans in the `AdditionalServices` class
so that they can be discovered.
  
This example can be run together with the [Basic gRPC Client example](../basic-client/README.md) 
which provides a microprofile gRPC client that uses the services deployed in this server.

## Build

```
mvn package
```

## Run

```
mvn exec:java
```

Then the services can be accessed on the gRPC endpoint `localhost:1408`
