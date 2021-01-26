# Standalone WebClient Example

This example demonstrates how to use the Helidon SE WebClient from a
standalone Java program to connect to a server.

## Build

```
mvn package
```

## Run

First, start the server:

```
java -jar target/helidon-examples-webclient-standalone.jar
```

Note the port number that it displays. For example:

```
WEB server is up! http://localhost:PORT/greet
```

Then run the client, passing the port number. It will connect
to the server:

```
java -cp "target/classes:target/libs/*" io.helidon.examples.webclient.blocking.ClientMain PORT
```

