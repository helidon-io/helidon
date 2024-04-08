# Helidon gRPC Security ABAC Example

An example gRPC outbound security

## Build and run

```shell
mvn -f ../pom.xml -pl common,security-outbound package
java -jar target/helidon-examples-grpc-security-outbound.jar
```

Exercise the example:
```shell
java -cp target/helidon-examples-grpc-security-outbound.jar \
    io.helidon.grpc.examples.security.outbound.SecureGreetClient
```

Sample output of the client:
```shell
bob
Greeting set to: MERHABA
bob
```
