# Helidon gRPC Metrics Example

A basic example using metrics with gRPC server.

## Build and run

With JDK8+
```bash
mvn package
java -jar target/helidon-examples-grpc-metrics.jar
```

Try the metrics:
```bash
curl http://localhost:8080/metrics
```