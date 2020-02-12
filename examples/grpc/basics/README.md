# Helidon gRPC Example

A basic example gRPC server.

## Build and run

With JDK8+
```bash
mvn package
java -jar target/helidon-examples-grpc-basics.jar
```

Exercise the example:
```bash
java -cp target/helidon-examples-grpc-basics.jar \
    io.helidon.grpc.examples.basics.HealthClient
```