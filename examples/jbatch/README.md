# Helidon + jBatch

Minimal Helidon MP + jBatch PoC.

## Build and run

With JDK11+
```bash
mvn package
java -jar target/helidon-jbatch-example.jar
```

## Exercise the application

```
curl -X GET http://localhost:8080/batch
```