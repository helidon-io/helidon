# Security integration with Jersey

This example demonstrates integration with Jersey (JAX-RS implementation).

## Contents

There are three examples with exactly the same behavior
1. builder - shows how to secure application using security built by hand
2. config - shows how to secure application with configuration
    1. see `src/main/resources/application.yaml`
3. programmatic - shows how to secure application using manual invocation of authentication

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-jersey.jar
```

Try the endpoints:
```bash
curl http://localhost:8080/rest
curl -v http://localhost:8080/rest/protected
curl -u "jack:password" http://localhost:8080/rest/protected
curl -u "jack:password" http://localhost:8080/rest/protected
curl -v -u "john:password" http://localhost:8080/rest/protected
```
