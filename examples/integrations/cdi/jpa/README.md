# JPA Integration Example

With Java:
```shell
mvn package
java -jar target/helidon-integrations-examples-jpa.jar
```

Try the endpoint:
```shell
curl -X POST -H "Content-Type: text/plain" http://localhost:8080/foo -d 'bar'
curl http://localhost:8080/foo
```
