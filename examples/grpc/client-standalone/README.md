# Helidon gRPC Standalone client

An example gRPC client. Can be used with the `basics` example that would act as a server. 

This example is created to test native image on pure client side, so it does not have a server side
implemented.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-grpc-client-standalone.jar
```

The client invokes the string service on the server, and should print out:
```
Text 'abcde' to upper is 'ABCDE'
```
