# Helidon gRPC MP Example

This examples shows a simple application written using Helidon gRPC MP API:

- StringService: a gRPC service implementation that uses MP
- StringServiceClient: an interface from which a client proxy can be created to call StringService remote methods
- StringServiceTest: a sample test that starts a server and tests the client and server components
- application.yaml: configuration for server and client channels

## Build and run

```shell
mvn package
java -jar target/helidon-examples-microprofile-grpc.jar 
```