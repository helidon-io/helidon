# Helidon MP Basic OpenAPI Example

This example shows a simple greeting application, similar to the one from the 
Helidon MP QuickStart, enhanced with OpenAPI support.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-openapi-basic.jar
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
The output describes not only then endpoints from `GreetResource` but
also one contributed by the `SimpleAPIModelReader`.


