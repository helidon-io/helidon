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

With JDK11+
```bash
mvn package
java -jar target/helidon-quickstart-mp.jar
```

## Exercise the application

```
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/null
should not be null
```