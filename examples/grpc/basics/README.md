# Helidon gRPC Example

A basic example gRPC server.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-grpc-basics.jar
```

Exercise the example:
```bash
java -cp target/helidon-examples-grpc-basics.jar \
    io.helidon.grpc.examples.basics.HealthClient
```

The HealthClient will report a SERVING status for the
first check, and a NOT_FOUND status for a non-existent
service.