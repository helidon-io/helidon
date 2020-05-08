
# Helidon SE OpenAPI Example

This example shows a simple greeting application, similar to the one from the 
Helidon SE QuickStart, enhanced with OpenAPI support.

Most of the OpenAPI document in this example comes from a static file packaged
with the application.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-openapi.jar
```

Try the endpoints:

```
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}

curl -X GET http://localhost:8080/openapi
[lengthy OpenAPI document]
```

The output describes not only then endpoints in `GreetService` as described in
the static file but also an endpoint contributed by the `SimpleAPIModelReader`.
