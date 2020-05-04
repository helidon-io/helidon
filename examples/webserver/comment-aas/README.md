# Comments As a Service

This application allows users to add or read short comments related to a single topic.
 Topic can be anything including blog post, newspaper article, and others.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-webserver-comment-aas.jar
```

Try the application:

```bash
curl http://localhost:8080/comments/java -d "I use Helidon!"
curl http://localhost:8080/comments/java -d "I use vertx"
curl http://localhost:8080/comments/java -d "I use spring"
curl http://localhost:8080/comments/java
```
