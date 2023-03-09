# helidon-tests-integration-yaml-parsing

Sample Helidon SE project to make sure that we can build and run using an older release of SnakeYAML in case users need to fall back.

Note that the static OpenAPI document packaged into the application JAR file intentionally _does not_ describe the API for this service.
It contains a much richer definition to exercise YAML parsing a bit more.

## Build and run

With JDK17+
```bash
mvn package
java -jar target/helidon-tests-integration-yaml-parsing.jar
```

## Try OpenAPI

```
curl -s -X GET http://localhost:8080/openapi

