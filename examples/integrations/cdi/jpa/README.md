# JPA Integration Example

With Java 8+:
```bash
mvn package
java -jar helidon-integrations-examples-jpa
```

Try the endpoint:
```bash
curl -X POST -d 'bar' http://localhost:8080/foo
curl http://localhost:8080/foo
```
