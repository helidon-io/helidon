# Helidon gRPC Security Example

An example gRPC server using basic auth security.

## Build and run

```bash
mvn -f ../pom.xml -pl common/security package
java -jar target/helidon-examples-grpc-security.jar
```

Exercise the example:
```bash
java -cp target/helidon-examples-grpc-security.jar \
    io.helidon.grpc.examples.security.SecureGreetClient
java -cp target/helidon-examples-grpc-security.jar \
    io.helidon.grpc.examples.security.SecureStringClient
```
