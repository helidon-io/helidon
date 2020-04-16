# Helidon Reactive Messaging Example

 Example showing
 * [Microprofile Reactive Messaging](https://github.com/eclipse/microprofile-reactive-messaging) 
 with [Microprofile Reactive Stream Operators](https://github.com/eclipse/microprofile-reactive-streams-operators) 
 connected to [Server-Sent Events](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/sse.html).

## Build and run

With JDK11+
```bash
mvn package
java -jar target/helidon-examples-microprofile-messaging-sse.jar
```

Then try in the browser:

http://localhost:7001