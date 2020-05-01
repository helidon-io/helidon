# WebServer Jersey Application Example

An example of **Jersey** integration into the **Web Server**.

This is just a simple Hello World example. A user can start the application using the `WebServerJerseyMain` class
and `GET` the `Hello World!` response by accessing `http://localhost:8080/jersey/hello`.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-webserver-jersey.jar
```

Make an HTTP request to application:
```bash
curl http://localhost:8080/jersey/hello
```
