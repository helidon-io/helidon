# JPA Integration Example

With Java 8+:
```bash
mvn package
java -jar target/helidon-integrations-examples-jpa.jar
```

Try the endpoint:
```bash
curl -X POST -H "Content-Type: text/plain" http://localhost:8080/foo -d 'bar'
curl http://localhost:8080/foo
```
