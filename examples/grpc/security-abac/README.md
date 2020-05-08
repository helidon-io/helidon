# Helidon gRPC Security ABAC Example

An example gRPC server for attribute based access control.

## Build and run

```bash
mvn -f ../pom.xml -pl common/security-abac package
java -jar target/helidon-examples-grpc-security-abac.jar
```

Take a look at the metrics:
```bash
curl http://localhost:8080/metrics
```
