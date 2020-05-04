# Helidon MP Hello World Implicit Example

This examples shows a simple application written using Helidon MP.
It is implicit because in this example you don't write the
`main` class, instead you rely on the Microprofile Server main class.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-hello-world-implicit.jar
```

Then try the endpoints:

```
curl -X GET http://localhost:7001/helloworld
curl -X GET http://localhost:7001/helloworld/another
```
