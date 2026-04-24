# WebSocket Introduction

## Overview

Helidon integrates with [Tyrus](https://projects.eclipse.org/projects/ee4j.tyrus) to provide support for the [Jakarta WebSocket API](https://jakarta.ee/specifications/websocket/2.1/jakarta-websocket-spec-2.1.html). The WebSocket API enables Java applications to participate in WebSocket interactions as both servers and clients. The server API supports two flavors: annotated and programmatic endpoints.

Annotated endpoints, as suggested by their name, use Java annotations to provide the necessary meta-data to define WebSocket handlers; programmatic endpoints implement API interfaces and are annotation free. Annotated endpoints tend to be more flexible since they allow different method signatures depending on the application needs, whereas programmatic endpoints must implement an interface and are, therefore, bounded to its definition.

Helidon SE support is based on the `WebSocketRouting` class which enables Helidon application to configure routing for both annotated and programmatic WebSocket endpoints.

## Maven Coordinates

To enable WebSocket, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-websocket</artifactId>
</dependency>
```

## Example

This section describes the implementation of a simple application that uses a REST resource to push messages into a shared queue and a programmatic WebSocket endpoint to download messages from the queue, one at a time, over a connection. The example will show how REST and WebSocket connections can be seamlessly combined into a Helidon application.

The complete Helidon SE example is available [here](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/websocket). Let us start by looking at `MessageQueueService`:

```java
record MessageQueueService(Queue<String> messageQueue) implements HttpService {
    @Override
    public void routing(HttpRules routingRules) {
        routingRules.post("/board", (req, res) -> {
            messageQueue.add(req.content().as(String.class));
            res.status(204).send();
        });
    }
}
```

This class exposes a REST resource where messages can be posted. Upon receiving a message, it simply pushes it into a shared queue and returns 204 (No Content).

Messages pushed into the queue can be obtained by opening a WebSocket connection served by `MessageBoardEndpoint`:

```java
record MessageBoardEndpoint(Queue<String> messageQueue) implements WsListener {
    @Override
    public void onMessage(WsSession session, String text, boolean last) {
        // Send all messages in the queue
        if (text.equals("send")) {
            while (!messageQueue.isEmpty()) {
                session.send(messageQueue.poll(), last);
            }
        }
    }
}
```

This is an example of a programmatic endpoint that extends `WsListener`. The method `onMessage` will be invoked for every message. In this example, when the special `send` message is received, it empties the shared queue sending messages one at a time over the WebSocket connection.

In Helidon SE, REST and WebSocket classes need to be manually registered into the web server. This is accomplished via a `Routing` builder:

```java
StaticContentService staticContent = StaticContentService.builder("/WEB")
        .welcomeFileName("index.html")
        .build();
Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
server.routing(it -> it
                .register("/web", staticContent)
                .register("/rest", new MessageQueueService(messageQueue)))
        .addRouting(WsRouting.builder()
                            .endpoint("/websocket/board", new MessageBoardEndpoint(messageQueue)));
```

This code snippet registers `MessageBoardEndpoint` at `/websocket/board` and associates.

## Reference

- [Helidon WebSocket JavaDoc](/apidocs/io.helidon.webserver.websocket/module-summary.html)
