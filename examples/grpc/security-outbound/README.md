# Helidon gRPC Security ABAC Example

An example gRPC outbound security

## Build and run

```bash
mvn -f ../pom.xml -pl common/security-outbound package
java -jar target/helidon-examples-grpc-security-outbound.jar
```

Exercise the example:
```bash
java -cp target/helidon-examples-grpc-security-outbound.jar \
    io.helidon.grpc.examples.security.outbound.SecureGreetClient
```
