# Web Server Integration and HTTP Signatures

This example demonstrates Integration of WebServer
based application with Security component and HTTP Signatures.

## Contents

There are two examples with exactly the same behavior
1. builder - shows how to programmatically secure application
2. config - shows how to secure application with configuration
    1. see `src/main/resources/service1.yaml` and `src/main/resources/service2.conf` for configuration
3. Each consists of two services
    1. "public" service protected by basic authentication (for simplicity)
    2. "internal" service protected by a combination of basic authentication (for user propagation) and http signature
    (for service authentication)

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-webserver-signatures.jar
```

Try the endpoints:
```bash
curl -u "jack:password" http://localhost:8080/service1
curl -u "jill:password" http://localhost:8080/service1-rsa
curl -v -u "john:password" http://localhost:8080/service1
```
