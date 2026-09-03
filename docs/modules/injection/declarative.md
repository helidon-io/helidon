<!--@frontmatter
description: "Helidon Declarative"
-->
# Declarative

## Overview

Helidon declarative programming model allows inversion of control style
programming with all the performance benefits of Helidon.

Our declarative approach has the following advantages:

- Uses Helidon imperative code to implement features (i.e. performance is
  same as "pure" imperative application)
- Generates all the necessary code at build-time, to avoid reflection and
  bytecode manipulation at runtime
- It is based on [Helidon Injection](injection.md#overview)
- Declarative features are in the same modules as Helidon features (i.e. does
  not require additional dependencies)

> [!NOTE]
> Helidon Declarative is an incubating feature. The APIs shown here are subject
> to change. These APIs will be finalized in a future release of Helidon.

## Usage

To create a declarative application, use the annotations provided in our
Helidon modules (details under [Features](#features)), and the maven plugin
described in [Injection: Startup](injection.md#startup) to generate the binding.

In addition, the following section must be added to the `build` of the Maven
`pom.xml` to enable annotation processors that generate the necessary code:

```xml [pom.xml]
<plugins>
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
      <annotationProcessorPaths>
        <path>
          <groupId>io.helidon.bundles</groupId>
          <artifactId>helidon-bundles-apt</artifactId>
          <version>${helidon.version}</version>
        </path>
      </annotationProcessorPaths>
    </configuration>
  </plugin>
</plugins>
```

When using declarative gRPC server endpoints or typed gRPC clients, add the
Helidon gRPC API dependency to the project’s compile classpath:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.grpc</groupId>
  <artifactId>helidon-grpc-api</artifactId>
</dependency>
```

## Features

The following features are currently implemented:

- [Configuration](#configuration)
- [HTTP Server Endpoint](#http-server-endpoint)
- [Typed HTTP Client](#typed-http-client)
- [gRPC Server Endpoint](#grpc-server-endpoint)
- [Typed gRPC Client](#typed-grpc-client)
- [Fault Tolerance](#fault-tolerance)
- [Scheduling](#scheduling)
- [Validation](#validation)
- [Security](#security)
- [Metrics](#metrics)
- [Tracing](#tracing)
- [WebSocket Server](#websocket-server)
- [WebSocket Client](#websocket-client)
- [GraphQL Server](#graphql-server)
- [WebServer CORS](#webserver-cors)
- [Health Checks](#health-checks)

A Helidon Declarative application should be started using the generated
application binding, to ensure no lookup and no reflection. The call to
`ServiceRegistryManager.start` ensures that all services with a defined
`RunLevel` are started, including Helidon WebServer, Scheduled services etc.

Example of a declarative main class

```java
@Service.GenerateBinding // generated binding to bypass discovery and runtime binding
public static class Main {
    public static void main(String[] args) {
        // configure logging
        LogConfig.configureRuntime();

        // start the "container"
        ServiceRegistryManager.start(ApplicationBinding.create());
    }
}
```

### Configuration

Configuration can be injected as a whole into any service, or a specific
configuration option can be injected using `@Configuration.Value`. Default
values can be defined using annotations in `@Default`

Services available for injection:

- [`io.helidon.config.Config`][io-helidon-confi]

Annotations:

- [`io.helidon.config.Configuration.Value`][io-helidon-confi-2] - define the
  configuration key to inject, on constructor parameter
- Annotations defined in [`io.helidon.common.Default`][io-helidon-commo] -
  define a default typed value, on the same constructor parameter

Example of usage can be seen below in HTTP Server Endpoint example.

### HTTP Server Endpoint

To create an HTTP endpoint, simply annotate a class with `@RestServer.Endpoint`,
and add at least one method annotated with one of the HTTP method annotations,
such as `@Http.GET`.

Services available for injection:

N/A

Supported method parameters (no annotation required):

- [`io.helidon.webserver.http.ServerRequest`][io-helidon-webse]
- [`io.helidon.webserver.http.ServerResponse`][io-helidon-webse-2]
- [`io.helidon.common.context.Context`][io-helidon-commo-2]
- `io.helidon.common.security.SecurityContext`
- `io.helidon.security.SecurityContext` - in case `helidon-security` module is
  on the classpath

If an endpoint method uses `ServerResponse` directly, generated response
metadata such as status, content type, and declarative response headers is
configured before the method is invoked. This is equivalent to imperative code
configuring the same `ServerResponse` with `status(...)`,
`headers().contentType(...)`, and `header(...)` before handling the response.

The metadata belongs to the current response and is not automatically cleared if
the method later calls `next()`, calls `reroute(...)`, or throws an exception;
later route or error handling can change it before sending the response. When
using `ServerResponse.outputStream()`, close the returned stream before leaving
the endpoint method.

For `void` endpoint methods, the generated handler only sends the response
automatically if the method did not already handle it. Methods that return a
value still send the returned entity.

Annotations on endpoint type:

- [`io.helidon.webserver.http.RestServer.Endpoint`][io-helidon-webse-3] -
  required annotation
- [`io.helidon.webserver.http.RestServer.Listener`][io-helidon-webse-4] - to
  define the named listener this should be served on (named port/socket)
- [`io.helidon.webserver.http.RestServer.Header`][io-helidon-webse-5] - header
  to return with each response from this endpoint
- [`io.helidon.webserver.http.RestServer.ComputedHeader`][io-helidon-webse-6] -
  computed header to return with each response from this endpoint
- [`io.helidon.http.Http.Produces`][io-helidon-http-4] - default media types
  produced by endpoint methods
- [`io.helidon.http.Http.Consumes`][io-helidon-http-5] - default media types
  consumed by endpoint methods with request entities
- [`io.helidon.http.Http.Path`][io-helidon-http] - path (context) this endpoint
  will be available on

Annotations on endpoint methods:

- [`io.helidon.webserver.http.RestServer.Header`][io-helidon-webse-5] - header
  to return with each response from this method
- [`io.helidon.webserver.http.RestServer.ComputedHeader`][io-helidon-webse-6] -
  computed header to return with each response from this method
- [`io.helidon.webserver.http.RestServer.Status`][io-helidon-webse-7] - status
  to return (if a custom one is required)
- [`io.helidon.http.Http.Path`][io-helidon-http] - path (context) this method
  will be available on (subpath of the endpoint path)
- [`io.helidon.http.Http.GET`][io-helidon-http-2] (and other methods) -
  definition of HTTP method this method will serve
- [`io.helidon.http.Http.HttpMethod`][io-helidon-http-3] - for custom HTTP
  method names (mutually exclusive with above)
- [`io.helidon.http.Http.Produces`][io-helidon-http-4] - what media type this
  method produces (return entity content type), replacing the endpoint default;
  an empty array clears it
- [`io.helidon.http.Http.Consumes`][io-helidon-http-5] - what media type this
  method accepts (request entity content type), replacing the endpoint default;
  an empty array clears it

Annotations on method parameters:

- [`io.helidon.http.Http.Entity`][io-helidon-http-6] - Request entity, a typed
  parameter is expected, will use HTTP media type modules to coerce into the
  correct type
- [`io.helidon.http.Http.HeaderParam`][io-helidon-http-7] - Typed HTTP request
  header value
- [`io.helidon.http.Http.CookieParam`][io-helidon-http-10] - Typed HTTP request
  cookie value
- [`io.helidon.http.Http.QueryParam`][io-helidon-http-8] - Typed HTTP query
  value
- [`io.helidon.http.Http.FormParam`][io-helidon-http-11] - Typed URL-encoded
  form value from the request entity
- [`io.helidon.http.Http.PathParam`][io-helidon-http-9] - Typed parameter from
  path template
- [`io.helidon.http.Http.RequestParams`][io-helidon-http-12] - Grouped request
  parameters in a record type

`Http.FormParam` is backed by the request entity and should be used with
`@Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)`. It cannot be
combined with `Http.Entity` on the same declarative method. Each endpoint method
parameter may have at most one supported request parameter annotation.

`Http.RequestParams` is supported only for record parameter types. This is a
restriction on the parameter types supported by declarative code generation.
Each record component must either be a supported typed parameter that does not
require an annotation, such as `ServerRequest`, or have one of these component
annotations: `Http.HeaderParam`, `Http.CookieParam`, `Http.QueryParam`,
`Http.FormParam`, `Http.PathParam`, or `Http.Entity`. Components must not
combine supported request parameter annotations. At most one `Http.Entity`
component is supported, and `Http.Entity` cannot be combined with
`Http.FormParam` components.

For declarative server endpoints, `Http.Entity` supports both a direct entity
type and `Optional<T>`. A direct entity is mandatory, and the request fails if
the entity is missing. An optional entity is `Optional.empty()` when the
request has no entity. This behavior applies both to endpoint method parameters
and to `Http.RequestParams` record components.

The named value annotations include `Http.HeaderParam`, `Http.CookieParam`,
`Http.QueryParam`, `Http.FormParam`, and `Http.PathParam`. They support scalar
values, `Optional<T>`, `List<T>`, and `Optional<List<T>>`. For server endpoints,
`List<T>` is mandatory and the request fails if the named value is missing.
`Optional<List<T>>` is optional and is empty when the named value is missing. If
the named value is present but has no values, the injected list is empty.
Cookies are an exception: cookies are not represented as `Parameters`, so a
named cookie cannot be present with no values. Cookie parameter names are
validated during declarative code generation. Server path parameters are
obtained from the routed request path.

Example of an HTTP Server Endpoint

```java
@RestServer.Endpoint // identifies this class as a server endpoint
@Http.Path("/greet") // serve this endpoint on /greet context root (path)
@Http.Produces(MediaTypes.APPLICATION_JSON_VALUE) // default response media type for all endpoint methods
@Service.Singleton   // a singleton service (single instance within a service registry)
static class GreetEndpoint {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private final String greeting;

    // inject app.greeting configuration value, use "Hello" if not configured
    GreetEndpoint(@Configuration.Value("app.greeting") @Default.Value("Hello") String greeting) {
        this.greeting = greeting;
    }

    @Http.GET   // HTTP GET endpoint
    public JsonObject getDefaultMessageHandler() {
        // build the JSON object (requires `helidon-http-media-jsonp` on classpath)
        return JSON.createObjectBuilder()
                .add("message", greeting + " World!")
                .build();
    }
}
```

Example of Grouped HTTP Server Parameters:

```java
@RestServer.Endpoint
@Http.Path("/messages")
@Service.Singleton
static class MessageEndpoint {
    @Http.GET
    @Http.Path("/{id}")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String message(@Http.RequestParams MessageParams params) {
        return params.id() + ":" + params.language() + ":" + params.theme();
    }

    @Http.POST
    @Http.Path("/form")
    @Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String submit(@Http.FormParam("message") String message,
                  @Http.CookieParam("session") String session) {
        return session + ":" + message;
    }
}
```

### Typed HTTP Client

To create a typed HTTP client, create an interface annotated with
`RestClient.Endpoint`, and at least one method annotated with one fo the HTTP
method annotations, such as `@Http.GET`. Methods can only have parameters
annotated with one of the `Http` qualifiers.

Annotations on endpoint type:

- [`io.helidon.webclient.api.RestClient.Endpoint`][io-helidon-webcl] - required
  annotation
- [`io.helidon.http.Http.Path`][io-helidon-http] - path (context) the server
  listens on
- [`io.helidon.webclient.api.RestClient.Header`][io-helidon-webcl-2] - header to
  include in every request to the server
- [`io.helidon.webclient.api.RestClient.ComputedHeader`][io-helidon-webcl-3] -
  header to compute and include in every request to the server
- [`io.helidon.http.Http.Produces`][io-helidon-http-4] - default media types
  produced by the server
- [`io.helidon.http.Http.Consumes`][io-helidon-http-5] - default media types
  consumed by the server for methods with request entities

Annotations on endpoint methods:

- [`io.helidon.webclient.api.RestClient.Header`][io-helidon-webcl-2] - header to
  include in every request to the server
- [`io.helidon.webclient.api.RestClient.ComputedHeader`][io-helidon-webcl-3] -
  header to compute and include in every request to the server
- [`io.helidon.http.Http.Path`][io-helidon-http] - path (context) the server
  serves this endpoint method on
- [`io.helidon.http.Http.GET`][io-helidon-http-2] (and other methods) -
  definition of HTTP method this method will invoke
- [`io.helidon.http.Http.HttpMethod`][io-helidon-http-3] - for custom HTTP
  method names (mutually exclusive with above)
- [`io.helidon.http.Http.Produces`][io-helidon-http-4] - what media type this
  method produces (content type of entity from the server), replacing the
  client type default; an empty array clears it
- [`io.helidon.http.Http.Consumes`][io-helidon-http-5] - what media type this
  method accepts (request entity content type), replacing the client type
  default; an empty array clears it

Annotations on method parameters:

- [`io.helidon.http.Http.Entity`][io-helidon-http-6] - Request entity, a typed
  parameter is expected, will use HTTP media type modules to write to the
  request
- [`io.helidon.http.Http.HeaderParam`][io-helidon-http-7] - Typed HTTP header
  value to send
- [`io.helidon.http.Http.CookieParam`][io-helidon-http-10] - Typed HTTP cookie
  value to send
- [`io.helidon.http.Http.QueryParam`][io-helidon-http-8] - Typed HTTP query
  value to send
- [`io.helidon.http.Http.FormParam`][io-helidon-http-11] - Typed URL-encoded
  form value to send in the request entity
- [`io.helidon.http.Http.PathParam`][io-helidon-http-9] - Typed parameter from
  path template to construct the request URI
- [`io.helidon.http.Http.RequestParams`][io-helidon-http-12] - Grouped request
  parameters in a record type

`Http.FormParam` is backed by the request entity and should be used with
`@Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)`. It cannot be
combined with `Http.Entity` on the same declarative method. Each client method
parameter may have at most one supported request parameter annotation.

`Http.RequestParams` is supported only for record parameter types. This is a
restriction on the parameter types supported by declarative code generation.
Each client record component must use one of these component annotations:
`Http.HeaderParam`, `Http.CookieParam`, `Http.QueryParam`, `Http.FormParam`,
`Http.PathParam`, or `Http.Entity`. Components must not combine supported
request parameter annotations. At most one `Http.Entity` component is
supported, and `Http.Entity` cannot be combined with `Http.FormParam`
components.

The `Http.HeaderParam`, `Http.CookieParam`, `Http.QueryParam`, and
`Http.FormParam` annotations support scalar values, `Optional<T>`, `List<T>`,
and `Optional<List<T>>`. Declarative clients send every value from `List<T>`,
and send `Optional<List<T>>` only when the optional is present. Declarative
client named-value parameters must not be `null`; use `Optional.empty()` to omit
optional values. List values must not contain `null` elements. The same
nullability rule applies to named-value components of `Http.RequestParams`
records, and the request-params argument itself must not be `null`. Cookie
parameter names are validated during declarative code generation. Declarative
clients send cookie parameters as a single `Cookie` header and do not escape
cookie values; each cookie parameter value must already be a valid cookie-octet
value. Cookies are not represented as `Parameters`, so a named cookie cannot be
present with no values. A mandatory client `List<T>` cookie parameter must
contain at least one value.

Declarative clients use `Http.PathParam` values as URI path text when
constructing the request URI. They do not encode each value as a single path
segment, so reserved path characters such as `/` keep their WebClient URI
meaning. Path parameter values must not be `null`.

Example of a Typed HTTP Client

```java
@RestClient.Endpoint("${greet-service.client.uri:http://localhost:8080}")
@RestClient.Header(name = HeaderNames.USER_AGENT_NAME, value = "my-client")
@Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
interface GreetClient {
    @Http.GET
    JsonObject getDefaultMessageHandler();
}
```

Example of Grouped HTTP Client Parameters:

```java
record MessageParams(
        @Http.PathParam("id") String id,
        @Http.QueryParam("lang") String language,
        @Http.CookieParam("theme") String theme) {
}

@RestClient.Endpoint("${message-service.client.uri:http://localhost:8080}")
@Http.Path("/messages")
interface MessageClient {
    @Http.GET
    @Http.Path("/{id}")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String message(@Http.RequestParams MessageParams params);

    @Http.POST
    @Http.Path("/form")
    @Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String submit(@Http.FormParam("message") String message,
                  @Http.CookieParam("session") String session);
}
```

### gRPC Server Endpoint

Declarative gRPC server endpoints are service registry services annotated with
`io.helidon.webserver.grpc.RpcServer.Endpoint` and
[`io.helidon.grpc.api.Grpc.GrpcService`][grpc-service]. The generated binding
contributes a `GrpcRouteRegistration` service that registers the endpoint with
the gRPC server feature.

Annotations on endpoint type:

- `io.helidon.webserver.grpc.RpcServer.Endpoint` - required annotation to
  generate a declarative gRPC server endpoint
- [`io.helidon.grpc.api.Grpc.GrpcService`][grpc-service] - required, non-blank
  gRPC service name; use the fully-qualified service name when the proto
  declares a package
- [`io.helidon.grpc.api.Grpc.ProtoDescriptor`][grpc-proto-descriptor] -
  generated protocol buffer class with a static `getDescriptor()` method
  returning `com.google.protobuf.Descriptors.FileDescriptor`
- `io.helidon.service.registry.Service.Singleton` - normal service registry
  scope for the endpoint implementation

`io.helidon.service.registry.Service.PerRequest` is not supported for
declarative gRPC endpoints because gRPC calls do not participate in the
WebServer HTTP request scope. Use
`io.helidon.service.registry.Service.Singleton` or
`io.helidon.service.registry.Service.PerLookup` instead.

The endpoint must declare exactly one proto descriptor source: either
`@Grpc.ProtoDescriptor` on the type or one [`@Grpc.Proto`][grpc-proto] method.
The referenced generated protocol buffer class must provide a public static
`getDescriptor()` method returning
`com.google.protobuf.Descriptors.FileDescriptor`. An `@Grpc.Proto` method may
be static or an endpoint instance method; it must be non-private, have no
parameters or checked exceptions, and return
`com.google.protobuf.Descriptors.FileDescriptor`.

Each request and response type must implement `com.google.protobuf.Message` and
declare public static no-argument `getDescriptor()` and `getDefaultInstance()`
methods returning `com.google.protobuf.Descriptors.Descriptor` and the message
type, respectively. At runtime, the generated registration verifies that these
descriptors match the input and output descriptors of the named proto method.

Annotations on endpoint methods:

- [`io.helidon.grpc.api.Grpc.Unary`][grpc-unary] - unary gRPC method
- [`io.helidon.grpc.api.Grpc.ServerStreaming`][grpc-server-streaming] -
  server-streaming gRPC method
- [`io.helidon.grpc.api.Grpc.ClientStreaming`][grpc-client-streaming] -
  client-streaming gRPC method
- [`io.helidon.grpc.api.Grpc.Bidirectional`][grpc-bidirectional] - bidirectional
  streaming gRPC method

Each endpoint method must declare exactly one gRPC method annotation and must
not declare checked exceptions. The Java annotation type must match the
streaming cardinality declared by the proto method.

Supported server method signatures:

- Unary: `Res method(Req)` or `void method(Req, StreamObserver<Res>)`
- Server streaming: `Stream<Res> method(Req)` or
  `void method(Req, StreamObserver<Res>)`
- Client streaming: `Res method(Stream<Req>)`
- Bidirectional streaming: `Stream<Res> method(Stream<Req>)` or
  `StreamObserver<Req> method(StreamObserver<Res>)`

Declarative streaming methods use resource-owning `Stream` instances with
transport backpressure and cancellation. Endpoint implementations consume
request streams, while the generated runtime owns and closes both request
streams supplied to an endpoint and response streams returned by an endpoint.
Endpoint implementations transfer ownership of response streams to the runtime
and must not close a response stream before returning it.

Generated registrations use the fully-qualified gRPC service name, including
the proto package when present. Route registration is enabled by default and
can be disabled with
`server.features.grpc-route-registration.enabled=false`. Annotate an endpoint
with `@RpcServer.Listener("admin")` to register it on a named listener. Custom
`GrpcRouteRegistration` implementations can return a named socket. If that
socket is not configured and `socketRequired()` returns `false`, the
registration falls back to the default socket. Generated declarative
registrations require the named listener when `@RpcServer.Listener` is used.

Validation annotations on declarative gRPC server methods are enforced by
generated entry point interceptors. To map a `ValidationException` to gRPC
`INVALID_ARGUMENT`, add `helidon-webserver-grpc-validation` to the server
runtime. The validation gRPC server service is discovered from the classpath and
enabled by default. Configure it under
`server.protocols.grpc.grpc-services.validation`; set
`server.protocols.grpc.grpc-services.validation.enabled=false` to disable the
status mapping.

Security annotations on declarative gRPC server methods require the gRPC
security module. Add `helidon-webserver-grpc-security` and configure normal
Helidon security. The gRPC security service is discovered from the classpath and
enabled by default; set
`server.protocols.grpc.grpc-services.security.enabled=false` to disable it.

### Typed gRPC Client

To create a typed gRPC client, create an interface annotated with
`RpcClient.Endpoint` and `Grpc.GrpcService`, and at least one method annotated
with one of the gRPC method annotations.

Annotations on endpoint type:

- [`io.helidon.webclient.grpc.RpcClient.Endpoint`][rpc-client-endpoint] -
  required annotation
- [`io.helidon.grpc.api.Grpc.GrpcService`][grpc-service] - required, non-blank
  gRPC service name; use the fully-qualified service name when the proto
  declares a package
- [`io.helidon.grpc.api.Grpc.ProtoDescriptor`][grpc-proto-descriptor] -
  generated protocol buffer class with a static `getDescriptor()` method
  returning `com.google.protobuf.Descriptors.FileDescriptor`

The endpoint must declare exactly one proto descriptor source: either
`@Grpc.ProtoDescriptor` on the type or one [`@Grpc.Proto`][grpc-proto] method.
The referenced generated protocol buffer class must provide a public static
`getDescriptor()` method returning
`com.google.protobuf.Descriptors.FileDescriptor`. An `@Grpc.Proto` method may
be static or a default interface method; it must be non-private, have no
parameters or checked exceptions, and return
`com.google.protobuf.Descriptors.FileDescriptor`.

Each request and response type must implement `com.google.protobuf.Message` and
declare public static no-argument `getDescriptor()` and `getDefaultInstance()`
methods returning `com.google.protobuf.Descriptors.Descriptor` and the message
type, respectively. At runtime, the generated client verifies that these
descriptors match the input and output descriptors of the named proto method.

Annotations on endpoint methods:

- [`io.helidon.grpc.api.Grpc.Unary`][grpc-unary] - unary gRPC method
- [`io.helidon.grpc.api.Grpc.ServerStreaming`][grpc-server-streaming] -
  server-streaming gRPC method
- [`io.helidon.grpc.api.Grpc.ClientStreaming`][grpc-client-streaming] -
  client-streaming gRPC method
- [`io.helidon.grpc.api.Grpc.Bidirectional`][grpc-bidirectional] - bidirectional
  streaming gRPC method

Each abstract non-default client method must declare exactly one gRPC method
annotation and must not declare checked exceptions.

Supported client method signatures:

- Unary: `Res method(Req)` or `void method(Req, StreamObserver<Res>)`
- Server streaming: `Stream<Res> method(Req)` or
  `void method(Req, StreamObserver<Res>)`
- Client streaming: `Res method(Stream<Req>)` or
  `StreamObserver<Req> method(StreamObserver<Res>)`
- Bidirectional streaming: `Stream<Res> method(Stream<Req>)` or
  `StreamObserver<Req> method(StreamObserver<Res>)`

Returned streams own the RPC and must be closed when the caller stops before
normal exhaustion. The client consumes and closes request streams on normal
completion, cancellation, or failure; a transferred request stream must not be
reused. The calling thread consumes a client-streaming request stream. If
producing elements can block, closing the stream must unblock production so an
early peer termination can return promptly. After a response arrives,
`GrpcClientProtocolConfig.nextRequestWaitTime()` bounds how long the call waits
for the caller to request it; expiration closes the call with gRPC status
`CANCELLED`.

To inject a typed gRPC client, use the annotated interface with the
`@RpcClient.Client` qualifier.

```java
@Service.Inject
MyService(@RpcClient.Client GreetingClient client) {
}
```

The `RpcClient.Endpoint.value()` defines the target URI for generated backing
clients and supports configuration expressions, such as
`${grpc.service.uri:http://localhost:8080}`. When declarative code generation
creates a new backing `GrpcClient`, it applies this URI after any client
configuration. Registry-provided clients keep their own base URI.

The base of configuration for a declarative gRPC client is the fully-qualified
name of the annotated interface. This key can be modified using the `configKey`
property of the `@RpcClient.Endpoint` annotation.

Configuration options under this key:

| Key      | Default | Description |
|----------|---------|-------------|
| `client` | none    | Configuration options of Helidon `GrpcClient`. If this node exists, the generated client creates a dedicated `GrpcClient` instance and does not use a registry-provided client. |

Client resolution order:

1. If `<configKey>.client` exists, create a dedicated `GrpcClient` from that
   configuration and apply `RpcClient.Endpoint.value()` as its base URI.
2. Otherwise, if `RpcClient.Endpoint.clientName()` is set and a matching named
   `GrpcClient` exists in the service registry, use that registry client.
3. Otherwise, if `RpcClient.Endpoint.clientName()` is set and no matching named
   `GrpcClient` exists, create a new `GrpcClient` using the endpoint URI from
   `RpcClient.Endpoint.value()`.
4. Otherwise, if an unnamed `GrpcClient` exists in the service registry, use
   that registry client.
5. Otherwise, create a new `GrpcClient` using the endpoint URI from
   `RpcClient.Endpoint.value()`.

### Fault Tolerance

Fault tolerance annotations allow adding features to methods on services. The
annotations can be added to any method that supports interception (i.e. methods
that are not private).

Method-level fault tolerance annotations can also be declared on methods
inherited from service contracts and typed HTTP client interfaces. Type-level
fault tolerance annotations on interfaces are not inherited by service methods.
Contracts provided by registry-managed service factories are not included.

Method Annotations:

- [`io.helidon.faulttolerance.Ft.Retry`][io-helidon-fault] - allow retries
- [`io.helidon.faulttolerance.Ft.Fallback`][io-helidon-fault-2] - fallback to
  another method that provides
- [`io.helidon.faulttolerance.Ft.Async`][io-helidon-fault-3] - invoke method
  asynchronously
- [`io.helidon.faulttolerance.Ft.Timeout`][io-helidon-fault-4] - invoke method
  with a timeout
- [`io.helidon.faulttolerance.Ft.Bulkhead`][io-helidon-fault-5] - use bulkhead
- [`io.helidon.faulttolerance.Ft.CircuitBreaker`][io-helidon-fault-6] - use
  circuit breaker

Example of Fault Tolerance Fallback

```java
@Service.Singleton
static class AlgorithmService {
    @Ft.Fallback(value = "fallbackAlgorithm", applyOn = IOException.class)
    String algorithm() throws IOException {
        // may throw an exception
        return "some-algorithm";
    }

    // method that would be called if #algorithm fails with an IOException
    String fallbackAlgorithm() {
        return "default";
    }
}
```

### Scheduling

Scheduling allows service methods to be invoked periodically.

Method annotations:

- [`io.helidon.scheduling.Scheduling.Cron`][io-helidon-sched] - execute with
  schedule defined by a CRON expression
- [`io.helidon.scheduling.Scheduling.FixedRate`][io-helidon-sched-2] - execute
  with a fixed interval

Example of a fixed rate scheduled method

```java
@Service.Singleton
static class CacheService {
    @Scheduling.FixedRate("PT5S")
    void checkCache() {
        // do something every 5 seconds
    }
}
```

The following annotation values can use configuration expressions:

- `Scheduling.Cron#value()`
- `Scheduling.Fixed#delayBy()`
- `Scheduling.FixedRate#value()`

Configuration expressions is a reference to a configuration key, with optional
default value:

`${config.key:default-value}`

### Validation

Validation provides an ability to validate service method parameters and return
types. This is achieved through constraint annotations and type validation.

To use validation, the proper dependency must be added to your `pom.xml`, and an
annotation processor must be configured to code generate the required classes.
The annotation processor is part of the bundle mentioned in Helidon Declarative
introduction above.

Helidon validation module:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.validation</groupId>
  <artifactId>helidon-validation</artifactId>
</dependency>
```

For declarative gRPC server endpoints, add the gRPC validation runtime module
when validation failures should be returned as gRPC `INVALID_ARGUMENT` status
responses:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-grpc-validation</artifactId>
</dependency>
```

The gRPC validation status mapper is enabled by default under
`server.protocols.grpc.grpc-services.validation`; set
`server.protocols.grpc.grpc-services.validation.enabled=false` to disable it.

#### Constraint Annotations

A "Constraint Annotation" is any annotation directly annotated with
`io.helidon.validation.Validation.Constraint`. Helidon Validation provides a set
of built-in validation constraints, though custom constraints can be created, or
existing constraints can be combined.

Existing constraints:

Constraints for any type:

- [`io.helidon.validation.Validation.NotNull`][io-helidon-valid] - must not be
  null
- [`io.helidon.validation.Validation.Null`][io-helidon-valid-2] - must be null

Constraints for `String` and `CharSequence`:

- [`io.helidon.validation.Validation.Email`][io-helidon-valid-3] - matches an
  e-mail structure (basic check only)
- [`io.helidon.validation.Validation.String.NotBlank`][io-helidon-valid-4] -
  must not be blank (empty or only white-space characters)
- [`io.helidon.validation.Validation.String.NotEmpty`][io-helidon-valid-5] -
  must not be empty (i.e. length is `0`)
- [`io.helidon.validation.Validation.String.Length`][io-helidon-valid-6] - check
  for maximal (and optionally minimal) length
- [`io.helidon.validation.Validation.String.Pattern`][io-helidon-valid-7] -
  check against a regular expression

Constraints for types that extend `java.lang.Number`. These constraints accept
any such type, though all types are eventually converted to a `BigDecimal` and
the checks are done against the result. `Byte` is always converted as an
unsigned number, i.e. its values are from `0` to `255` inclusive.

- [`io.helidon.validation.Validation.Number.Negative`][io-helidon-valid-8] - the
  value must be negative (`< 0`)
- [`io.helidon.validation.Validation.Number.NegativeOrZero`][io-helidon-valid-9]
  - the value must be negative or zero (`<= 0`)
- [`io.helidon.validation.Validation.Number.Positive`][io-helidon-valid-10] -
  the value must be positive (`> 0`)
- [`Validation.Number.PositiveOrZero`][io-helidon-valid-11]
  - the value must be positive or zero (`>= 0`)
- [`io.helidon.validation.Validation.Number.Min`][io-helidon-valid-12] - the
  value must be at least the specified minimal value (`>= min`), value is
  defined as a `String`
- [`io.helidon.validation.Validation.Number.Max`][io-helidon-valid-13] - the
  value must be at most the specified maximal value (`<= max`), value is defined
  as a `String`
- [`io.helidon.validation.Validation.Number.Digits`][io-helidon-valid-14] - the
  number must have at most the specified number of integer and fractional digits
- [`io.helidon.validation.Validation.Number.MultipleOf`][io-helidon-valid-15] -
  the value must be evenly divisible by the specified positive factor, which is
  defined as a `String`

Constraints for `Integer` data types. These constraints accept `int, long, byte,
char, short` and their boxed counterparts. `byte` is always converted as an
unsigned number, i.e. its values are from `0` to `255` inclusive. These are
convenience annotation that use `int` data type:

- [`io.helidon.validation.Validation.Integer.Min`][io-helidon-valid-16] - the
  value must be at least the specified minimal value (`>= min`)
- [`io.helidon.validation.Validation.Integer.Max`][io-helidon-valid-17] - the
  value must be at most the specified maximal value (`<= max`)
- [`io.helidon.validation.Validation.Integer.MultipleOf`][io-helidon-valid-18] -
  the value must be evenly divisible by the specified positive integer factor

Constraints for `Long` and `long` data types. No other type is supported:

- [`io.helidon.validation.Validation.Long.Min`][io-helidon-valid-19] - the value
  must be at least the specified minimal value (`>= min`)
- [`io.helidon.validation.Validation.Long.Max`][io-helidon-valid-20] - the value
  must be at most the specified maximal value (`<= max`)
- [`io.helidon.validation.Validation.Long.MultipleOf`][io-helidon-valid-21] -
  the value must be evenly divisible by the specified positive long factor

Constraints for `Boolean` and `boolean` data type. No other type is supported:

- [`io.helidon.validation.Validation.Boolean.True`][io-helidon-valid-22] - the
  value must be `true`
- [`io.helidon.validation.Validation.Boolean.False`][io-helidon-valid-23] - the
  value must be `false`

Constraints for collection and map data types:

- [`io.helidon.validation.Validation.Collection.Size`][io-helidon-valid-24] -
  the size of the collection or map must be between the minimal and maximal
  sizes

Constraints for calendar/time data types. Behavior depends on the specific type
- for example for `Year` data type, past is previous year, future is the next
year, and present is the current year, regardless of which month it is. When
using `Instant`, past is already the last millisecond.

- [`io.helidon.validation.Validation.Calendar.Future`][io-helidon-valid-25] -
  the value must be in the future
- [`Validation.Calendar.FutureOrPresent`][io-helidon-valid-26]
  - the value must be in the future or now
- [`io.helidon.validation.Validation.Calendar.Past`][io-helidon-valid-27] - the
  value must be in the past
- [`Validation.Calendar.PastOrPresent`][io-helidon-valid-28]
  - the value must be in the past or now

Supported types for calendar/time validations:

- `java.util.Date`
- `java.util.Calendar`
- `java.time.Instant`
- `java.time.LocalDate`
- `java.time.LocalDateTime`
- `java.time.LocalTime`
- `java.time.MonthDay`
- `java.time.OffsetDateTime`
- `java.time.OffsetTime`
- `java.time.Year`
- `java.time.YearMonth`
- `java.time.ZonedDateTime`
- `java.time.chrono.HijrahDate`
- `java.time.chrono.JapaneseDate`
- `java.time.chrono.MinguoDate`
- `java.time.chrono.ThaiBuddhistDate`

#### Type Validation

A type annotated with `@Validation.Validated` will have validation code
generated. Usage of that type can be marked with `@Validation.Valid` - if such
an annotation is present, and it is on a field of another validated type, or it
is a parameter, return type, or a type argument of a parameter/return type of a
service method, the object instance will be validated using a generated
interceptor. Type-use validation is supported on nested `Optional`,
`Collection`, `List`, `Set`, `Map` key/value types, array component types, and
wildcard bounds.

Cascaded validation is skipped when a value annotated with
`@Validation.Valid` is `null`. The annotation does not make the value required;
add `@Validation.NotNull` when `null` must be rejected.

#### Usage

Example of a validated type

```java
@Validation.Validated
record MyType(@Validation.String.Pattern(".*valid.*") @Validation.NotNull String validString,
              @Validation.Integer.Min(42) int validInt) {
}
```

Example of a validated method call using a validated type

```java
@Service.Singleton
static class ValidatedService {
    @Validation.String.NotBlank // validates the response
    String process(@Validation.Valid @Validation.NotNull MyType myType) {
        // result of the logic
        return "some result";
    }
}
```

Constraint annotations and `@Validation.Valid` can also be declared on
non-private instance methods of service interfaces. They are applied when the
service instance is obtained from the service registry. Constraints declared on
matching service interface and implementation methods are combined.
Implementation constraints do not replace or loosen constraints declared by the
service interface, even when both methods use the same constraint type with
different values. Service interfaces are discovered using the service registry
contract rules, including
`@Service.Contract`, implicit contracts when enabled, and
`@Service.ExternalContracts`. The same applies to instances returned by
registry-managed service factories, including `Supplier`,
`Service.ServicesFactory`, `Service.InjectionPointFactory`, and
`Service.QualifiedFactory` services. This can change runtime behavior for
services that already declared interface validation: invalid calls through
registry-obtained services may now fail with a `ValidationException`, while
directly constructed objects are not intercepted.

Example of a validated service contract

```java
@Service.Contract
interface ValidatedServiceContract {
    String process(@Validation.String.NotBlank String value);
}

@Service.Singleton
static class ContractValidatedService implements ValidatedServiceContract {
    @Override
    public String process(String value) {
        return value;
    }
}
```

A custom "compound" annotation can be created to simplify usage.

Example of a compound annotation

```java
@Validation.NotNull
@Validation.String.NotBlank
public @interface NonNullNotBlank {
}
```

A custom constraint annotation can be created (and act as a compound annotation
as well).

Example of a custom constraint annotation

```java
@Validation.NotNull // will add not-null constraint as well
@Validation.Constraint
public @interface CustomConstraint {
}
```

For each constraint annotation, there MUST be a service that validates it.

Example of constraint validation provider

<!--@mdc ::code-collapse -->
```java
@Service.Singleton
@Service.NamedByType(CustomConstraint.class)
static class CustomConstraintValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName typeName, Annotation constraintAnnotation) {
        // we could Validation the type here, but we don't need to - depends on constraint
        return new CustomValidator(constraintAnnotation);
    }

    private static class CustomValidator implements ConstraintValidator {
        private final Annotation annotation;

        private CustomValidator(Annotation annotation) {
            this.annotation = annotation;
        }

        @Override
        public ValidatorResponse check(ValidatorContext context, Object value) {
            if (value == null) {
                // we leave the `not-null` Validation to the "meta-annotation" on CustomConstraint
                return ValidatorResponse.create();
            }

            // if string, and the value is "good", it is OK
            if (value instanceof String str) {
                if (str.equals("good")) {
                    return ValidatorResponse.create();
                }
            }

            return ValidatorResponse.create(annotation, "Must be \"good\" string", value);
        }
    }
}
```
<!--@mdc :: -->

### Security

Security provides protection of WebServer endpoints.

Identity propagation (when using a WebClient) depends on configuration of the
client and configuration of security. We currently do not have a declarative way
of modifying client behavior.

Declarative gRPC server security annotations require
`helidon-webserver-grpc-security` on the server runtime classpath and an enabled
gRPC security service, for example
`server.protocols.grpc.grpc-services.security.enabled=true`, in addition to
normal Helidon security configuration.

Supported annotations:

- `io.helidon.security.annotations.Authenticated` - mark an endpoint or a method
  as requiring authentication
- `io.helidon.security.annotations.Authorized` - mark an endpoint or a method as
  requiring authorization; `explicit = true` is not supported for declarative
  gRPC
- `io.helidon.security.annotations.Audited` - mark an endpoint or a method as
  requiring audit logging
- `io.helidon.security.abac.role.RoleValidator.PermitAll` - annotated method
  does not require any authentication or authorization (even if endpoint does)
- `jakarta.annotation.security.PermitAll` - same as `RoleValidator.PermitAll`
- `jakarta.annotation.security.DenyAll` - annotated method will not be callable
  with any kind of authentication or authorization
- [`io.helidon.security.abac.role.RoleValidator.Roles`][io-helidon-secur] -
  provide a set of roles that can access a resource, implies authentication is
  required
- `jakarta.annotation.security.RolesAllowed` - same as above
  (`RoleValidator.Roles`)

### Metrics

Add support for the following meters:

- Counter
- Timer
- Gauge

Method annotations:

- [`io.helidon.metrics.api.Metrics.Counted`][io-helidon-metri] - adds a counter
  metric to the metric registry for method executions
- [`io.helidon.metrics.api.Metrics.Timed`][io-helidon-metri-2] - adds a timer
  metric to the metric registry for method executions
- [`io.helidon.metrics.api.Metrics.Gauge`][io-helidon-metri-3] - marks a method
  that returns a number as a gauge

Metrics method annotations can also be declared on methods inherited from
service contracts and typed HTTP client interfaces. Contracts provided by
registry-managed service factories are not included.

In addition, we can use
[`io.helidon.metrics.api.Metrics.Tag`][io-helidon-metri-4] annotation on a type,
method, or as a `tags` property of an annotation to add tags to the metric. Tags
from type definition will be added to all metrics on the type, tags on methods
on all metrics on the method, and tags in the metric annotation will only be
used by that metric.

The example below shows additional tags. The counter on method `counted` will
have the following tags: `service=Metered;method=counted` (and of course the
scope tag that is always added).

Example of a counted method with type tags and counter tags

```java
@Service.Singleton
@Metrics.Tag(key = "service", value = "Metered")
static class MeteredService {
    @Metrics.Counted(tags = @Metrics.Tag(key = "method", value = "counted"))
    void counted() {
        // whenever invoked through service interface, counter is incremented
    }
}
```

A gauge is a method that returns a `Number`, and is invoked by the metrics
implementation to obtain a value. Example below shows a definition of a `Gauge`.
Note that a `unit` is mandatory for gauges.

Example of a gauge

```java
@Service.Singleton
static class ServiceWithAGauge {
    private volatile int percentage = 0;

    @Metrics.Gauge(unit = "percent")
    int gauge() {
        return this.percentage;
    }
}
```

### Tracing

Add support for tracing of methods. This feature will add a new span for each
annotated method (or all methods on an annotated type).

Tracing annotations can also be declared on methods inherited from service
contracts and typed HTTP client interfaces. A type-level tracing annotation on a
contract interface applies only to methods declared by that contract. Contracts
provided by registry-managed service factories are not included.

Annotations:

- [`io.helidon.tracing.Tracing.Traced`][io-helidon-traci] - all methods on the
  annotated type are will be traced, or the annotated method will be traced
- [`io.helidon.tracing.Tracing.ParamTag`][io-helidon-traci-2] - the annotated
  method parameter will be added as a tag to the span

Notes on defaults:

- if a `kind` is defined to other value than `INTERNAL`, it will be used unless
  a `kind` other than `INTERNAL` is defined on a method annotation (i.e. it is
  not possible to have `SERVER` on type, and `INTERNAL` on method)
- span name defaults to `fully-qualified-class-name.method-name`

The following example shows annotation on a type. This would make all methods
traced with span kind of `SERVER`, and with a tag `service` with value
`TracedService`.

Example of traced type

```java
@Service.Singleton
@Tracing.Traced(tags = @Tracing.Tag(key = "service", value = "TracedService"),
                kind = Span.Kind.SERVER)
static class TracedService {
```

A traced method with an explicit span name, adding a tag with a constant value,
and a tag with a value from annotated parameter. The tag name defaults to
parameter name (`userAgent` in this case).

Annotated traced method

```java
@Http.GET
@Http.Path("/greet")
@Tracing.Traced(value = "explicit-name", tags = @Tracing.Tag(key = "custom", value = "customValue"))
String greet(@Http.HeaderParam("User-Agent") @Tracing.ParamTag String userAgent) {
    return "Hello!";
}
```

### WebSocket Server

To create a WebSocket endpoint, simply annotate a class with
`@WebSocketServer.Endpoint`, and add at least one method annotated with one of
the WebSocket method annotations, such as `@WebSocket.OnMessage`.

Services available for injection:

N/A

Supported method parameters (no annotation required):

- [`io.helidon.websocket.WsSession`][io-helidon-webso]
- `boolean` in a method annotated with `@WebSocket.OnMessage` - indicator of
  "last" message (if not present, the message will be combined before delivery)
- `java.lang.String` (`@WebSocket.OnMessage`) - the message delivered (text)
- `java.io.Reader` (`@WebSocket.OnMessage`) - the message delivered (text)
- [`io.helidon.common.buffers.BufferData`][io-helidon-commo-3]
  (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.nio.ByteBuffer` (`@WebSocket.OnMessage`) - the message delivered
  (binary)
- `java.io.InputStream` (`@WebSocket.OnMessage`) - the message delivered
  (binary)
- `io.helidon.http.HttpPrologue` (`@WebSocket.OnHttpUpgrade`) - the HTTP
  prologue (method, path, protocol version)
- `io.helidon.http.Headers` (`@WebSocket.OnHttpUpgrade`) - the request headers
- `int` (`@WebSocket.OnClose`) - the close code
- `java.lang.String` (`@WebSocket.OnClose`) - the close reason
- `java.lang.Throwable` (`@WebSocket.OnError`) - the throwable thrown

Annotations on endpoint type:

- [`WebSocketServer.Endpoint`][io-helidon-webse-8]
  - required annotation
- [`WebSocketServer.Listener`][io-helidon-webse-9]
  - to define the named listener this should be served on (named port/socket)
- [`io.helidon.http.Http.Path`][io-helidon-http] - path (context) this endpoint
  will be available on

Annotations on endpoint methods:

- [`io.helidon.websocket.WebSocket.OnMessage`][io-helidon-webso-2] - receives
  either a binary or a text message
- [`io.helidon.websocket.WebSocket.OnHttpUpgrade`][io-helidon-webso-3] - invoked
  during HTTP upgrade, the method may return `Headers` to be sent during the
  upgrade response
- [`io.helidon.websocket.WebSocket.OnOpen`][io-helidon-webso-4] - invoked when
  the WebSocket connection is established (after upgrade)
- [`io.helidon.websocket.WebSocket.OnClose`][io-helidon-webso-5] - invoked when
  the WebSocket connection is closed
- [`io.helidon.websocket.WebSocket.OnError`][io-helidon-webso-6] - invoked when
  an error occurs when invoking other methods

Annotations on method parameters:

- [`io.helidon.http.Http.PathParam`][io-helidon-http-9] - Typed parameter from
  path template

Example of a WebSocket Server Endpoint

```java
@WebSocketServer.Endpoint
@Http.Path("/websocket/echo")
@Service.Singleton
static class EchoEndpoint {
    @WebSocket.OnMessage
    void onMessage(WsSession session, String message) {
        session.send(message, true);
    }
}
```

### WebSocket Client

To create a WebSocket client endpoint, simply annotate a class with
`@WebSocketClient.Endpoint`, and add at least one method annotated with one of
the WebSocket method annotations, such as `@WebSocket.OnMessage`.

Services available for injection:

- a factory for the endpoint (generated), if endpoint is named `EchoEndpoint`,
  an `EchoEndpointFactory` will be generated with methods to connect to remote
  server

Supported method parameters (no annotation required):

- [`io.helidon.websocket.WsSession`][io-helidon-webso]
- `boolean` in a method annotated with `@WebSocket.OnMessage` - indicator of
  "last" message (if not present, the message will be combined before delivery)
- `java.lang.String` (`@WebSocket.OnMessage`) - the message delivered (text)
- `java.io.Reader` (`@WebSocket.OnMessage`) - the message delivered (text)
- [`io.helidon.common.buffers.BufferData`][io-helidon-commo-3]
  (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.nio.ByteBuffer` (`@WebSocket.OnMessage`) - the message delivered
  (binary)
- `java.io.InputStream` (`@WebSocket.OnMessage`) - the message delivered
  (binary)
- `int` (`@WebSocket.OnClose`) - the close code
- `java.lang.String` (`@WebSocket.OnClose`) - the close reason
- `java.lang.Throwable` (`@WebSocket.OnError`) - the throwable thrown

Annotations on endpoint type:

- [`io.helidon.webclient.websocket.WebSocketClient`][io-helidon-webcl-4] -
  required annotation
- [`io.helidon.http.Http.Path`][io-helidon-http] - path (context) this endpoint
  will be available on

Annotations on endpoint methods:

- [`io.helidon.websocket.WebSocket.OnMessage`][io-helidon-webso-2] - receives
  either a binary or a text message
- [`io.helidon.websocket.WebSocket.OnHttpUpgrade`][io-helidon-webso-3] - invoked
  during HTTP upgrade, the method may return `Headers` to be sent during the
  upgrade response
- [`io.helidon.websocket.WebSocket.OnOpen`][io-helidon-webso-4] - invoked when
  the WebSocket connection is established (after upgrade)
- [`io.helidon.websocket.WebSocket.OnClose`][io-helidon-webso-5] - invoked when
  the WebSocket connection is closed
- [`io.helidon.websocket.WebSocket.OnError`][io-helidon-webso-6] - invoked when
  an error occurs when invoking other methods

Annotations on method parameters:

- [`io.helidon.http.Http.PathParam`][io-helidon-http-9] - Typed parameter from
  path template defined by `@Http.Path` on the class

Example of a WebSocket Client Endpoint

```java
// will use `ws.connection` configuration key, and if not present, default to http://localhost:8080
@WebSocketClient.Endpoint("${ws.connection:http://localhost:8080}")
@Http.Path("/echo/{count}")
@Service.Singleton
static class EchoClient {
    @WebSocket.OnMessage
    void onMessage(WsSession session, String message, @Http.PathParam("count") int count) {
        // do something with the message
    }
}
```

Example of a component connecting the websocket

```java
@Service.Singleton
static class EchoClientUser {
    private final EchoClientFactory clientFactory;

    @Service.Inject
    EchoClientUser(EchoClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    void handle(int count) {
        // the clientFactory and the method we are invoking are code generated
        // this will start the websocket session (the method returns once the session is initiated)
        clientFactory.connect(count);
    }
}
```

### GraphQL Server

To create a GraphQL endpoint, annotate a class with
`@GraphQlServer.Endpoint`, add at least one method annotated with
`@GraphQl.Query`, and annotate Java schema types with `@GraphQl.Entity`.
Declarative GraphQL generates the SDL, GraphQL Java runtime wiring, and
WebServer registration at build time.

Supported method parameters (no annotation required):

- `graphql.schema.DataFetchingEnvironment`
- [`io.helidon.common.context.Context`][io-helidon-commo-2]
- [`io.helidon.graphql.server.ExecutionContext`][graphql-execution-context]
- [`io.helidon.security.SecurityContext`][security-context] - if
  `helidon-security` is on the classpath

Annotations on endpoint type:

- [`io.helidon.webserver.graphql.GraphQlServer.Endpoint`][graphql-endpoint] -
  required annotation
- [`io.helidon.webserver.graphql.GraphQlServer.Listener`][graphql-listener] -
  defines the named listener this should be served on
- [`io.helidon.webserver.graphql.GraphQlServer.Context`][graphql-context] -
  GraphQL web context, defaults to `/graphql`
- [`io.helidon.webserver.graphql.GraphQlServer.SchemaUri`][graphql-schema-uri] -
  schema endpoint URI under the GraphQL web context

Annotations on resolver methods:

- [`io.helidon.graphql.GraphQl.Query`][graphql-query] - top-level query field
- [`io.helidon.graphql.GraphQl.Mutation`][graphql-mutation] - top-level mutation
  field
- [`io.helidon.webserver.graphql.GraphQlServer.Field`][graphql-field] - child
  field resolver

Annotations on schema types, fields, and resolver parameters:

- [`io.helidon.graphql.GraphQl.Entity`][graphql-entity] - required marker for
  Java classes, records, interfaces, and enums that contribute SDL
- [`io.helidon.graphql.GraphQl.Argument`][graphql-argument] - GraphQL resolver
  argument name
- [`io.helidon.graphql.GraphQl.Name`][graphql-name] - GraphQL name override
- [`io.helidon.graphql.GraphQl.Description`][graphql-description] - GraphQL
  description
- [`io.helidon.graphql.GraphQl.DefaultValue`][graphql-default-value] - GraphQL
  default value literal for an argument
- [`io.helidon.graphql.GraphQl.NonNull`][graphql-non-null] - non-null schema
  element
- [`io.helidon.graphql.GraphQl.Ignore`][graphql-ignore] - exclude an output Java
  member from SDL
- [`io.helidon.graphql.GraphQl.Scalar`][graphql-scalar] - custom scalar Java
  type
- [`io.helidon.webserver.graphql.GraphQlServer.Source`][graphql-source] - child
  resolver source object

GraphQL resolver methods are Service Registry entry points. Standard
declarative entry-point features, including security annotations and validation
interceptors, can participate in resolver invocation. The generated WebServer
route is also wrapped as an HTTP entry point for route-level security.

Security annotations on the GraphQL endpoint type protect the generated
WebServer route before GraphQL execution starts. Authentication and
authorization failures in this phase use normal HTTP security responses, such
as `401` or `403`. Security annotations on resolver methods protect individual
GraphQL fields. Resolver-level failures are returned as GraphQL field errors in
the response body, so sibling fields can still complete and the HTTP response
commonly remains `200`.

See the [GraphQL Declarative API](../graphql.md#declarative-api) for
dependencies, startup, examples, custom scalars, and resolver parameter
extension points.

### WebServer CORS

CORS can be configured through Helidon Config, the root key is `cors`.

To add an explicit CORS (Cross-origin resource sharing) configuration to an
endpoint method, you may annotate it with one of the annotations in the `Cors`
class, such as `@Cors.Defaults`.

Annotations on endpoint method (must be an `OPTIONS` method):

- [`io.helidon.webserver.cors.Cors.Defaults`][io-helidon-webse-10] - support all
  methods, all origins, do not combine with other annotations from `Cors` class
- [`io.helidon.webserver.cors.Cors.AllowOrigins`][io-helidon-webse-11] -
  configure allowed origins, either as a exact string, or regular expression (if
  the value contains `\`, `*` or `{`, it is considered a regular expression)
- [`io.helidon.webserver.cors.Cors.AllowMethods`][io-helidon-webse-12] - set of
  allowed methods that the different origin script can use
- [`io.helidon.webserver.cors.Cors.AllowHeaders`][io-helidon-webse-13] - set of
  allowed headers sent from the different origin script
- [`io.helidon.webserver.cors.Cors.ExposeHeaders`][io-helidon-webse-14] - set of
  headers from response exposed to the different origin script
- [`io.helidon.webserver.cors.Cors.AllowCredentials`][io-helidon-webse-15] -
  whether to add credentials (such as Cookie) to requests from the different
  origin script
- [`io.helidon.webserver.cors.Cors.MaxAgeSeconds`][io-helidon-webse-16] -
  maximal number of seconds the pre-flight is considered valid

Example of a CORS protected endpoint

<!--@mdc ::code-callout -->
```java
@Service.Singleton
@Http.Path("/cors")
static class CorsEndpoint {
    @Http.OPTIONS
    @Cors.AllowOrigins("${app.cors.allow-origins:http://foos.bar,http://bars.foo}") // <1>
    @Cors.AllowHeaders({"X-foo", "X-bar"}) // <2>
    @Cors.AllowMethods({Method.DELETE_NAME, Method.PUT_NAME, "LIST"}) // <3>
    @Cors.MaxAgeSeconds(180) // <4>
    void options() {
    }
}
```
1. Configure origins that can be overridden using config key
   `app.cors.allow-origins` with the provided default values (comma separated)
2. Configure headers the script can send to this host
3. Configure allowed methods for CORS requests
4. Configure max age to be 3 minutes
<!--@mdc :: -->

### Health Checks

To add a declarative health check, create a service that implements
io.helidon.health.HealthCheck or produces an instance of it. The WebServer
health observer discovers all such services and uses them to contribute to the
health response. Because the lookup is performed only once, you must not use the
@Service.PerRequest scope. The recommended scope is @Service.Singleton.

[graphql-execution-context]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql.server/io/helidon/graphql/server/ExecutionContext.html
[security-context]: https://helidon.io/docs/v27/apidocs/io.helidon.security/io/helidon/security/SecurityContext.html
[graphql-endpoint]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.graphql/io/helidon/webserver/graphql/GraphQlServer.Endpoint.html
[graphql-listener]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.graphql/io/helidon/webserver/graphql/GraphQlServer.Listener.html
[graphql-context]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.graphql/io/helidon/webserver/graphql/GraphQlServer.Context.html
[graphql-schema-uri]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.graphql/io/helidon/webserver/graphql/GraphQlServer.SchemaUri.html
[graphql-query]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Query.html
[graphql-mutation]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Mutation.html
[graphql-field]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.graphql/io/helidon/webserver/graphql/GraphQlServer.Field.html
[graphql-entity]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Entity.html
[graphql-argument]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Argument.html
[graphql-name]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Name.html
[graphql-description]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Description.html
[graphql-default-value]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.DefaultValue.html
[graphql-non-null]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.NonNull.html
[graphql-ignore]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Ignore.html
[graphql-scalar]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql/io/helidon/graphql/GraphQl.Scalar.html
[graphql-source]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.graphql/io/helidon/webserver/graphql/GraphQlServer.Source.html
[grpc-service]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.GrpcService.html
[grpc-proto-descriptor]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.ProtoDescriptor.html
[grpc-proto]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.Proto.html
[grpc-unary]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.Unary.html
[grpc-server-streaming]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.ServerStreaming.html
[grpc-client-streaming]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.ClientStreaming.html
[grpc-bidirectional]: https://helidon.io/docs/v27/apidocs/io.helidon.grpc.api/io/helidon/grpc/api/Grpc.Bidirectional.html
[rpc-client-endpoint]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.grpc/io/helidon/webclient/grpc/RpcClient.Endpoint.html
[io-helidon-confi]: https://helidon.io/docs/v27/apidocs/io.helidon.config/io/helidon/config/Config.html
[io-helidon-confi-2]: https://helidon.io/docs/v27/apidocs/io.helidon.config/io/helidon/config/Configuration.html
[io-helidon-commo]: https://helidon.io/docs/v27/apidocs/io.helidon.common/io/helidon/common/Default.html
[io-helidon-webse]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerRequest.html
[io-helidon-webse-2]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerResponse.html
[io-helidon-commo-2]: https://helidon.io/docs/v27/apidocs/io.helidon.common.context/io/helidon/common/context/Context.html
[io-helidon-webse-3]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Endpoint.html
[io-helidon-webse-4]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Listener.html
[io-helidon-webse-5]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Header.html
[io-helidon-webse-6]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.ComputedHeader.html
[io-helidon-http]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.Path.html
[io-helidon-webse-7]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Status.html
[io-helidon-http-2]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.GET.html
[io-helidon-http-3]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.HttpMethod.html
[io-helidon-http-4]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.Produces.html
[io-helidon-http-5]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.Consumes.html
[io-helidon-http-6]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.Entity.html
[io-helidon-http-7]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.HeaderParam.html
[io-helidon-http-8]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.QueryParam.html
[io-helidon-http-9]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.PathParam.html
[io-helidon-http-10]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.CookieParam.html
[io-helidon-http-11]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.FormParam.html
[io-helidon-http-12]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/Http.RequestParams.html
[io-helidon-webcl]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Endpoint.html
[io-helidon-webcl-2]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Header.html
[io-helidon-webcl-3]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.ComputedHeader.html
[io-helidon-fault]: https://helidon.io/docs/v27/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Retry.html
[io-helidon-fault-2]: https://helidon.io/docs/v27/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Fallback.html
[io-helidon-fault-3]: https://helidon.io/docs/v27/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Async.html
[io-helidon-fault-4]: https://helidon.io/docs/v27/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Timeout.html
[io-helidon-fault-5]: https://helidon.io/docs/v27/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Bulkhead.html
[io-helidon-fault-6]: https://helidon.io/docs/v27/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.CircuitBreaker.html
[io-helidon-sched]: https://helidon.io/docs/v27/apidocs/io.helidon.scheduling/io/helidon/scheduling/Scheduling.Cron.html
[io-helidon-sched-2]: https://helidon.io/docs/v27/apidocs/io.helidon.scheduling/io/helidon/scheduling/Scheduling.FixedRate.html
[io-helidon-valid]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.NotNull.html
[io-helidon-valid-2]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Null.html
[io-helidon-valid-3]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Email.html
[io-helidon-valid-4]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.NotBlank.html
[io-helidon-valid-5]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.NotEmpty.html
[io-helidon-valid-6]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Length.html
[io-helidon-valid-7]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Pattern.html
[io-helidon-valid-8]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Negative.html
[io-helidon-valid-9]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.NegativeOrZero.html
[io-helidon-valid-10]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Positive.html
[io-helidon-valid-11]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.PositiveOrZero.html
[io-helidon-valid-12]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Min.html
[io-helidon-valid-13]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Max.html
[io-helidon-valid-14]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Digits.html
[io-helidon-valid-15]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.MultipleOf.html
[io-helidon-valid-16]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.Min.html
[io-helidon-valid-17]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.Max.html
[io-helidon-valid-18]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.MultipleOf.html
[io-helidon-valid-19]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.Min.html
[io-helidon-valid-20]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.Max.html
[io-helidon-valid-21]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.MultipleOf.html
[io-helidon-valid-22]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Boolean.True.html
[io-helidon-valid-23]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Boolean.False.html
[io-helidon-valid-24]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Collection.Size.html
[io-helidon-valid-25]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.Future.html
[io-helidon-valid-26]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.FutureOrPresent.html
[io-helidon-valid-27]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.Past.html
[io-helidon-valid-28]: https://helidon.io/docs/v27/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.PastOrPresent.html
[io-helidon-secur]: https://helidon.io/docs/v27/apidocs/io.helidon.security.abac.role/io/helidon/security/abac/role/RoleValidator.Roles.html
[io-helidon-metri]: https://helidon.io/docs/v27/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Counted.html
[io-helidon-metri-2]: https://helidon.io/docs/v27/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Timed.html
[io-helidon-metri-3]: https://helidon.io/docs/v27/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Gauge.html
[io-helidon-metri-4]: https://helidon.io/docs/v27/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Tag.html
[io-helidon-traci]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing/io/helidon/tracing/Tracing.Traced.html
[io-helidon-traci-2]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing/io/helidon/tracing/Tracing.ParamTag.html
[io-helidon-webso]: https://helidon.io/docs/v27/apidocs/io.helidon.websocket/io/helidon/websocket/WsSession.html
[io-helidon-commo-3]: https://helidon.io/docs/v27/apidocs/io.helidon.common.buffers/io/helidon/common/buffers/BufferData.html
[io-helidon-webse-8]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.websocket/io/helidon/webserver/websocket/WebSocketServer.Endpoint.html
[io-helidon-webse-9]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.websocket/io/helidon/webserver/websocket/WebSocketServer.Listener.html
[io-helidon-webso-2]: https://helidon.io/docs/v27/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnMessage.html
[io-helidon-webso-3]: https://helidon.io/docs/v27/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnHttpUpgrade.html
[io-helidon-webso-4]: https://helidon.io/docs/v27/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnOpen.html
[io-helidon-webso-5]: https://helidon.io/docs/v27/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnClose.html
[io-helidon-webso-6]: https://helidon.io/docs/v27/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnError.html
[io-helidon-webcl-4]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.websocket/io/helidon/webclient/websocket/WebSocketClient.Endpoint.html
[io-helidon-webse-10]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.Defaults.html
[io-helidon-webse-11]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowOrigins.html
[io-helidon-webse-12]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowMethods.html
[io-helidon-webse-13]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowHeaders.html
[io-helidon-webse-14]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.ExposeHeaders.html
[io-helidon-webse-15]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowCredentials.html
[io-helidon-webse-16]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.MaxAgeSeconds.html
