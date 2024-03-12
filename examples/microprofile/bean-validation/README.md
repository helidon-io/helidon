# Helidon MP Bean Validation Example

This example implements a simple Hello World REST service using MicroProfile demonstrating Bean Validation.

## Usage

To be able to use bean validation add the following dependency:

```xml
<dependency>
    <groupId>io.helidon.microprofile.bean-validation</groupId>
    <artifactId>helidon-microprofile-bean-validation</artifactId>
</dependency>
```

## Build and run

```shell
mvn package
java -jar target/helidon-examples-microprofile-bean-validation.jar
```

## Exercise the application

```shell
curl -X GET http://localhost:8080/greet
#{"message":"Hello World!"}

curl -X GET -I http://localhost:8080/greet/null
```
```text

HTTP/1.1 400 Bad Request
Content-Type: application/json
transfer-encoding: chunked
connection: keep-alive
```
