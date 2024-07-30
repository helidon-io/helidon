# Helidon MP OpenAPI Example

This example shows a simple greeting application, similar to the one from the 
Helidon MP QuickStart, enhanced with OpenAPI support.

## Build and run

```shell
mvn package
java -jar target/helidon-examples-microprofile-openapi.jar
```

Try the endpoints:

```shell
curl -X GET http://localhost:8080/greet
#Output: {"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
#Output: {"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"message" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
#Output: {"message":"Hola Jose!"}

curl -X GET http://localhost:8080/openapi
#Output: [lengthy OpenAPI document]
```
The output describes not only then endpoints from `GreetResource` but
also one contributed by the `SimpleAPIModelReader`.


