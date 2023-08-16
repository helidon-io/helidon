Testing
----
There are two annotations that can be used for WebServer tests.
`@ServerTest` is an integration test annotation, that starts server (opens ports) and provides client injection pre-configured for
the server port(s).
`@RoutingTest` is a unit test annotation, that does not start server and does not open ports, but provides direct client (with
same API as the usual network client) to test routing.

Extensions can exist that enhance the features for the module `helidon-webserver-testing-junit5` to support additional
protocols. Known are listed here.

The following table lists supported types of parameters for `@SetUpRoute` annotated methods. Such methods MUST be static,
and may have any name. `@SetUpRoute` annotation has `value` with socket name (to customize setup for a different socket).

- Parameter type - supported class of a parameter
- Annotation - which annotation(s) support this parameter
- Modules - which extension modules support this signature

| Parameter Type             | Annotation                    | Modules   | Notes                                             |
|----------------------------|-------------------------------|-----------|---------------------------------------------------|
| `HttpRouting.Builder`      | `@ServerTest`, `@RoutingTest` |           |                                                   |
| `HttpRules`                | `@ServerTest`, `@RoutingTest` |           | Same as `HttpRouting.Builder`, only routing setup |
| `Router.RouterBuilder<?>`  | `@ServerTest`, `@RoutingTest` |           |                                                   |
| `SocketListener.Builder`   | `@ServerTest`                 |           |                                                   |
| `WebSocketRouting.Builder` | `@ServerTest`, `@RoutingTest` | websocket |                                                   |

In addition, a static method annotated with `@SetUpServer` can be defined for `@ServerTest`, that has a single parameter
of `WebServer.Builder`.

The following table lists injectable types (through constructor or method injection).

- Type - type that can be injected
- Socket? - if checked, you can use `@Socket` annotation to obtain value specific to that named socket
- Annotation - which annotation(s) support this injection
- Modules - which extension modules support this injection
- Notes - additional details

| Type               | Socket? | Annotation     | Modules   | Notes                                                                 |
|--------------------|---------|----------------|-----------|-----------------------------------------------------------------------|
| `WebServer`        |         | `@ServerTest`  |           | Server instance (already started)                                     |
| `URI`              | x       | `@ServerTest`  |           | URI pointing to a port of the webserver                               |
| `SocketHttpClient` | x       | `@ServerTest`  |           | Client that allows sending of anything, to test bad request and such. |
| `Http1Client`      | x       | `@ServerTest`  |           |                                                                       |
| `DirectClient`     | x       | `@RoutingTest` |           | Implements `Http1Client` API                                          |
| `WsClient`         | x       | `@ServerTest`  | websocket |                                                                       |
| `DirectWsClient`   | x       | `@RoutingTest` | websocket | Implements `WsClient` API                                             | 
