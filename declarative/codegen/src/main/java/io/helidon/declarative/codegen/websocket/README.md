Declarative WebSocket Server Code Generator
-------------

# Service

A websocket listener is a service annotated with `WebSocketServer.Endpoint` listening on a specific
`@Http.Path`, possibly with an explicit `WebSocketServer.Endpoint` to use a named listener (i.e. socket).

The service may have annotated methods as follows:

| Annotation            | Params                                                                                  | Description                                                  |
|-----------------------|-----------------------------------------------------------------------------------------|--------------------------------------------------------------|
| `WebSocket.OnOpen`    | `WsSession`, `@Http.PathParam` parameter(s)                                             | invoked when a new session is started (max. one per service) |
| `WebSocket.OnClose`   | `WsSession`, `@Http.PathParam` parameter(s)                                             | invoked when a session is closed                             |
| `WebSocket.OnError`   | `WsSession`, `Throwable`                                                                | invoked on error in message processing                       |
| `WebSocket.OnMessage` | `WsSession`, `String` or `Reader` or `String` + `boolean`                               | invoked for String message                                   |
| `WebSocket.OnMessage` | `WsSession`, `BufferData` or `InputStream` or `ByteBuffer`, or `BufferData` + `boolean` | invoked for binary message                                   |

# Generated code

The code will be generated into two classes:

1. A `WsRouteRegistration` type that is always a singleton service, used by the WebServer to add the WsRoute to the configured
   listener
2. A `WsListener` type - not a service, used from the route registration to ensure one instance per session

## WsRouteRegistration

A type named `EndpointName__WsRouteRegistration` that implements `io.helidon.webserver.websocket.WsRouteRegistration`.
Method `socket` is only generated if it is not `@default`.
Method `socketRequired` is only generated if the response is `true`.

## WsListener

A type named `EndpointName__WsListener` that implements `io.helidon.websocket.WsListener` and extends
`io.helidon.declarative.tests.websocket.WsListenerBase` (for string and binary onMessage operations that need to combine multiple
values together, or require a stream or reader).

The listener reads all `@Http.PathParam` in `onHttpUpgrade` method, and invokes endpoint methods as needed.
Only methods that are used will be generated.