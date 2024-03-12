# Helidon gRPC Security Example

An example gRPC server using basic auth security.

## Build and run

```shell
mvn  -f ../pom.xml -pl common,security package
java -jar target/helidon-examples-grpc-security.jar
```

# Exercise the example:
```shell
java -cp target/helidon-examples-grpc-security.jar \
    io.helidon.grpc.examples.security.SecureGreetClient
java -cp target/helidon-examples-grpc-security.jar \
    io.helidon.grpc.examples.security.SecureStringClient
```

# Sample client output:
SecureGreetClient:
```shell
message: "Hello Aleks!"

greeting: "Hey"

message: "Hey Aleks!"
```

SecureStringClient:
```shell
Response from Lower method call is 'abcde'
```
