# Helidon gRPC Metrics Example

A basic example using metrics with gRPC server.

## Build and run

With JDK8+
```bash
mvn package
java -jar target/helidon-examples-grpc-metrics.jar
```

Exercise the example:
```bash
java -cp target/helidon-examples-grpc-metrics.jar \
    io.helidon.grpc.examples.metrics.SecureStringClient
```