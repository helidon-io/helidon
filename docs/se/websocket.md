<!--@frontmatter
description: "Helidon WebSocket Support"
navigation:
  icon: i-lucide-repeat-2
-->
# WebSocket

## Maven Coordinates

To enable WebSocket, add the following dependency to your project’s `pom.xml`
(see [Managing Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-websocket</artifactId>
</dependency>
```

## Example

This section describes the implementation of a simple application that uses a
REST resource to push messages into a shared queue and a programmatic WebSocket
endpoint to download messages from the queue, one at a time, over a connection.
The example will show how REST and WebSocket connections can be seamlessly
combined into a Helidon application.

The complete Helidon SE example is available [here][here]. Let us start by
looking at `MessageQueueService`:

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

This class exposes a REST resource where messages can be posted. Upon receiving
a message, it simply pushes it into a shared queue and returns 204 (No Content).

Messages pushed into the queue can be obtained by opening a WebSocket connection
served by `MessageBoardEndpoint`:

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

This is an example of a programmatic endpoint that extends `WsListener`. The
method `onMessage` will be invoked for every message. In this example, when the
special `send` message is received, it empties the shared queue sending messages
one at a time over the WebSocket connection.

In Helidon SE, REST and WebSocket classes need to be manually registered into
the web server. This is accomplished via a `Routing` builder:

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

This code snippet registers `MessageBoardEndpoint` at `/websocket/board` and
associates.

## Client Frame Size

The WebSocket client accepts frame payloads up to 1 MiB by default. Configure
`protocol-config.max-frame-size` in `WsClient` configuration using a byte count
or a size such as `2 MiB`, or use `WsClientProtocolConfig.Builder.maxFrameSize(Size)`.
The size must be between 1 byte and `Integer.MAX_VALUE` bytes, inclusive.
Oversized frames are rejected before their payload is buffered, and the client
closes the connection with WebSocket close code `1009`.

This per-frame limit is independent of `max-buffered-message-size`, which
limits whole-message buffering. Listeners that consume fragments, readers,
or input streams are still subject to the frame limit. The server's per-frame
setting remains `server.protocols.websocket.max-frame-length`.

## Security

When a WebSocket upgrade request includes an `Origin` header, Helidon validates
it. By default, Helidon requires the `Origin` authority to match the request
`Host` header and rejects cross-host requests. If you configure
`server.protocols.websocket.origins`, the configured values act as an explicit
allowlist instead. Requests without an `Origin` header are allowed so
non-browser clients continue to work.

For unexpected listener exceptions, automatically generated WebSocket CLOSE
frames use status `1011` and the reason `Internal server error`. The original
exception is logged on the server; frame-processing failures are also passed to
`WsListener.onError`. Exception messages, including null or lengthy messages,
are not used as automatic close reasons.

Applications can still provide an explicit reason using
`WsSession.close(code, reason)`. A deliberate `WsCloseException` during frame
processing retains its configured close code and reason.

## Reference

- [Helidon WebSocket Javadoc][helidon-websocke]

[here]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/websocket
[helidon-websocke]: https://helidon.io/docs/v4/apidocs/io.helidon.webserver.websocket/module-summary.html
