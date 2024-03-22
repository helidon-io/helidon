
# Helidon MP Implicit gRPC Server Example

This examples shows a simple gRPC application written using Helidon MP.
It is implicit because in this example you don't write the
`main` class, instead you rely on the Microprofile gRPC Server main class.

The gRPC services to deploy will be discovered by CDI when the gRPC server starts.
The `StringService` is a POJO service implementation that is annotated with the
CDI qualifier `Grpc` so that it can be discovered.

Two additional services (`GreetService` and `EchoService`) that are not normally CDI
managed beans are manually added as CDI managed beans in the `AdditionalServices` class
so that they can be discovered.
  
This example can be run together with the [Basic gRPC Client example](../basic-client/README.md) 
which provides a microprofile gRPC client that uses the services deployed in this server.

## Build

```shell
mvn -f ../../pom.xml -pl common,microprofile/basic-server-implicit package
```

## Run

```shell
java -jar target/helidon-examples-grpc-microprofile-basic-implicit.jar
```

Then the services can be accessed on the gRPC endpoint `localhost:1408`. As noted above, the client in
[Basic gRPC Client example](../basic-client/README.md) can be used for this purpose.
