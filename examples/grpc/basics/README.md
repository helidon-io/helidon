# Helidon gRPC Example

A basic example gRPC server.

## Build and run

```bash
mvn -f ../pom.xml -pl common,basics package
java -jar target/helidon-examples-grpc-basics.jar
```

Exercise the example:
```bash
java -cp target/helidon-examples-grpc-basics.jar \
    io.helidon.grpc.examples.basics.HealthClient
```

The HealthClient will give this output:
```bash
GreetService response -> status: SERVING

FooService StatusRuntimeException.getMessage() -> NOT_FOUND: Service 'FooService' does not exist or does not have a registered health check

```
