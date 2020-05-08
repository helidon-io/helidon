
# Helidon Security Outbound Override Example

Example that propagates identity, and on one endpoint explicitly
sets the username and password.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-outbound-override.jar
```

Try the endpoints:
```bash
curl -u "jack:password" http://localhost:8080/propagate
curl -u "jack:password" http://localhost:8080/override
curl -u "jill:anotherPassword" http://localhost:8080/propagate
curl -u "jill:anotherPassword" http://localhost:8080/override
```
