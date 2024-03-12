# Helidon gRPC Standalone client

An example gRPC client. Can be used with the [basics](../basics/README.md) example that would act as a server. 

This example is created to test native image on pure client side, so it does not have a server side
implemented.

## Build and run

```shell
mvn -f ../pom.xml -pl common,client-standalone package
java -jar target/helidon-examples-grpc-client-standalone.jar
```

The client invokes the string service on the server, and should print out:
```text
Text 'lower case original' to upper case is 'LOWER CASE ORIGINAL'
```
