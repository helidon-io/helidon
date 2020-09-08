# Helidon MP Hello World Explicit Example

This examples shows a simple application written using Helidon MP.
It is explicit because in this example you write the `main` class
and explicitly start the microprofile server.

```bash
mvn package
java -jar target/helidon-examples-microprofile-hello-world-explicit.jar
```

Then try the endpoints:

```
curl -X GET http://localhost:7001/helloworld
curl -X GET http://localhost:7001/helloworld/earth
```

By default the server will use a dynamic port, see the messages displayed
when the application starts.
