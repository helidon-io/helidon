Helidon Declarative
----

_This documentation is for developers of Helidon, or for developers of additional features for Helidon Declarative_

A declarative programming model for Helidon.

This document describes the implemented APIs and their integration with code generation and the Service Registry.
For application setup and usage, see the [Helidon Declarative guide](../docs/modules/injection/declarative.md).
Individual features retain their documented preview status and configuration limitations.

_Declarative_: a programming model where we declare intention by annotating elements, to achieve functionality that would
otherwise require significant programming effort

Rules for Helidon Declarative:

1. Required APIs belong in the corresponding Helidon feature module (annotations,
   support for generated code, new APIs)
2. Annotations use nested namespace classes, such as `@Http.Path`, where possible. Existing APIs can also provide
   programmatic entry points, as `Metrics` and `Tx` do; security retains its existing standalone annotations.
3. The code generation for the features listed in the declarative codegen module is in `declarative/codegen`, with packages
   for each feature. It depends on shared codegen infrastructure, not the feature runtime modules, and triggers on feature
   annotations. Foundational generators, such as Service Registry, builders, JSON binding, and Data, live in their own modules.
4. It is forbidden to use reflection in any declarative feature; if reflection seems to be needed, replace it with code-generated
   type (example: fault tolerance fallback needs to invoke a fallback method, as this would require reflection, there is a
   generated type such as `GreetEndpoint_failingFallback__Fallback` that is named with the unique identification of the method it
   is generated for, and has the required code generated, the interceptor for fallback then looks it up at runtime to correctly
   handle invocation)
5. If there is a good reason the user may want to use a custom named service implementation, provide a way to inject it (see Retry
   generated code for named retries, such as `GreetEndpoint_retriable__Retry.java`)
6. Integrate feature services and their lifecycle with the Service Registry. Configuration and annotation overrides are
   feature-specific; there is no universal configuration override for every annotation property.

A few codegen features that are available:

- `io.helidon.codegen.spi.TypeMapperProvider` to map a `TypeInfo` created by codegen to a new `TypeInfo` - this is useful to add
  annotations, remove annotations etc. before code generation starts
- `io.helidon.codegen.spi.ElementMapperProvider` to map a `TypedElementInfo` - similar as above, but on the level of methods and
  fields
- `io.helidon.codegen.spi.AnnotationMapperProvider` to map annotations (i.e. we could map `@Autowired` to `@Service.Inject`)
- `io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider` (or the "hard-core"
  `io.helidon.codegen.spi.CodegenExtensionProvider`) to create extensions to build custom generated types - example can be found
  in `io.helidon.declarative.codegen.scheduling.SchedulingExtensionProvider`

# Feature namespace classes

These are the implemented annotation namespaces and supporting APIs in this repository. Health and security integrate
through existing service contracts and annotations rather than dedicated namespace classes.

| Feature | Namespace classes or APIs | Purpose |
|---------|---------------------------|---------|
| HTTP | `io.helidon.http.Http`, `io.helidon.webserver.http.RestServer`, `io.helidon.webclient.api.RestClient` | Shared HTTP declarations, server endpoints, and typed clients |
| Configuration | `io.helidon.config.Configuration`, `io.helidon.common.Default` | Configuration injection and typed defaults |
| Metrics | `io.helidon.metrics.api.Metrics` | Counters, timers, gauges, and tags |
| Fault Tolerance | `io.helidon.faulttolerance.Ft` | Method fault-tolerance policies |
| gRPC | `io.helidon.grpc.api.Grpc`, `io.helidon.webserver.grpc.RpcServer`, `io.helidon.webclient.grpc.RpcClient` | Shared RPC declarations, server endpoints, and typed clients |
| WebSocket | `io.helidon.websocket.WebSocket`, `io.helidon.webserver.websocket.WebSocketServer`, `io.helidon.webclient.websocket.WebSocketClient` | Callbacks, server endpoints, and client factories |
| Security | `io.helidon.security.annotations`, `io.helidon.security.abac.role.RoleValidator`, Jakarta security annotations | Authentication, authorization, roles, and auditing |
| Messaging | `io.helidon.messaging.Messaging` | Channel consumers, processors, headers, and failure policies |
| Scheduling | `io.helidon.scheduling.Scheduling` | Cron and fixed-rate method execution |
| Health | `io.helidon.health.HealthCheck`, `io.helidon.health.spi.HealthCheckProvider` | Registry-discovered health checks |
| OpenAPI | `io.helidon.openapi.OpenApi` | Generated API document metadata |
| Builders | `io.helidon.builder.api.Prototype`, `io.helidon.builder.api.Option`, `io.helidon.builder.api.RuntimeType` | Generated builders and configuration APIs |
| Tracing | `io.helidon.tracing.Tracing` | Method spans and tags |
| CORS | `io.helidon.webserver.cors.Cors` | Endpoint and preflight CORS policies |
| GraphQL | `io.helidon.graphql.GraphQl`, `io.helidon.webserver.graphql.GraphQlServer` | Schema model, queries, mutations, and field resolvers |
| Data | `io.helidon.data.Data`, `io.helidon.data.jdbc.Jdbc` | Repository declarations and JDBC statement mapping |
| Transactions | `io.helidon.transaction.Tx` | Transactional method execution |
| Service Registry | `io.helidon.service.registry.Service`, `io.helidon.service.registry.Interception`, `io.helidon.service.registry.Event` | Injection, lifecycle, interception, and events |
| Validation | `io.helidon.validation.Validation` | Type and invocation constraints |
| JSON Binding | `io.helidon.json.binding.Json` | Generated serialization and deserialization |
| JSON Schema | `io.helidon.json.schema.JsonSchema` | Generated schemas and schema constraints |

## Integrations

These integrations are maintained in separate repositories, with their own runtime and code-generation modules.

| Feature | Namespace class | Repository |
|---------|-----------------|------------|
| LangChain4j | `io.helidon.extensions.langchain4j.Ai` | [Helidon Extensions](https://github.com/helidon-io/helidon-extensions) |
| MCP | `io.helidon.extensions.mcp.server.Mcp` | [Helidon MCP](https://github.com/helidon-io/helidon-mcp) |

# How to build a feature

The following Helidon features can be used to create a new declarative feature

1. Interceptors - metrics, tracing, logging etc.
2. Injection (service factory) - for any feature where we expect the user to inject a specific service that the feature provides (
   AI, declarative REST client etc.)
3. Code generation - for any feature that needs additional code to minimize runtime lookups and handling; ideally we should have
   injection points that can be bound at build time (as opposed to runtime registry lookups) - see
   `Interception.ElementInterceptor` for generating code specific to a single method

# Declarative Codegen Module

`declarative/codegen` contains extensions for HTTP server and client, gRPC server and client, WebSocket server and client,
GraphQL server, OpenAPI, scheduling, fault tolerance, validation, metrics, tracing, CORS, and messaging.
The [module descriptor](codegen/src/main/java/module-info.java) registers the extension providers and parameter-codegen SPIs.
Shared code-generation models are in `declarative/codegen-model`.

Other generators remain with their owning features: `service/codegen`, `builder/codegen`, `json/codegen`,
`json/schema/codegen`, and the provider-specific generators under `data/`.
Runtime-only integrations, such as health-check discovery and transaction
interceptors, do not require a dedicated extension in this module.

There are a few types in the top level package:

- `RunLevels` - shared startup levels for our features; any `@Service.RunLevel` annotation
  generated MUST use a value from this type
- `DeclarativeTypes` - `TypeName` constants for common types that are not defined in
  `io.helidon.service.codegen.ServiceCodegenTypes`

See feature codegen details in the [Codegen Readme](codegen/README.md).

# Entry points

An entry point connects an external invocation to application code. HTTP, gRPC, GraphQL, and messaging expose
`HttpEntryPoint`, `GrpcEntryPoint`, `GraphQlEntryPoint`, and `MessagingEntryPoint`, respectively, with interceptor
contracts for their invocation chains. Generated registrations supply method metadata and direct invokers, allowing runtime
integrations such as security and context propagation to wrap the invocation without reflective method lookup.

# Features

The following sections describe the feature declarations and their implementation. The namespace tables also include
foundational APIs and integrations whose generators or runtime services live outside `declarative/codegen`.

## HTTP Server Endpoints

Defines a Server HTTP Service.
Each method is a route with its own path (see declaration below).
The method has a choice of using the Helidon Core approach - getting
`ServerRequest` and `ServerResponse` as parameters and doing everything
manually, or by using qualified parameters to obtain the desired information
from the request, and returning an object that will be sent as the response
entity.
To a certain degree, these approaches can be combined (i.e. we can get `ServerRequest` and return an entity, or get annotated
parameters and `ServerResponse`).

### Declaration

Annotations on type:

- `@RestServer.Endpoint` - required annotation on a type representing the endpoint
- `@RestServer.Listener` - optional annotation that allows selecting a specific listener (socket) to use
- `@RestServer.Header` - a header to be sent with every response from the server, defined either on the endpoint class, or on a
  method (repeatable)
- `@RestServer.ComputedHeader` - a header to be sent with every response computed from a service
- `@Http.Produces` - default media types produced by endpoint methods, individually replaceable on a method
- `@Http.Consumes` - default media types consumed by endpoint methods with request entities, individually replaceable on a method
- `@Http.Path` - the path this endpoint will be available on

Annotations on method(s), may be defined on the endpoint type, or on an interface the endpoint type implements:

- `@RestServer.Status` - define HTTP status to return from a method when the default is not good
- `@Http.GET`, `@Http.POST` etc., or `@Http.HttpMethod("LIST")` - mutually exclusive, to define the HTTP method that the endpoint
  method will be available on
- `@Http.Produces` - the media type produced by this method (returned in the `Content-Type` header), also used when matching the
  `Accept` header of the request; replaces the endpoint type default (an empty array clears it)
- `@Http.Consumes` - the media type expected by this method, when the request has an entity, matched against `Content-Type` of
  the request; replaces the endpoint type default (an empty array clears it)
- `@Http.Path` - the path this method will be available on, nested within the endpoint path, may contain path parameters (same as
  we can do when setting up routing)

Parameters defined by type:

- `ServerRequest` - Helidon WebServer request
- `ServerResponse` - Helidon WebServer response
- `io.helidon.common.context.Context` - server request context
- `io.helidon.common.security.SecurityContext` and `io.helidon.security.SecurityContext` - request security context when
  security is configured
- additional types supported through `io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider`

Parameters defined by qualifiers (may be an `Optional`, supports `Mappers`):

- `@Http.HeaderParam` - a named header from the request
- `@Http.QueryParam` - a named query parameter
- `@Http.PathParam` - a named parameter from the definition of `@Http.Path`
- `@Http.CookieParam` - a named cookie from the request
- `@Http.FormParam` - a named URL-encoded form parameter
- `@Http.Entity` - the HTTP request entity
- `@Http.RequestParams` - a record grouping request parameters, with annotations on its components; server records can
  also contain supported parameters identified by type

Named headers, query parameters, cookies, and form parameters also support `List<T>` and `Optional<List<T>>`.
Form parameters require `application/x-www-form-urlencoded` consumption and cannot be combined with an entity parameter,
including one inside a request-parameter record.

### Configuration

Server and listener options use normal WebServer configuration. Endpoint routing annotations define the generated paths,
listener selection, headers, and media types; they do not have a general configuration-override mechanism.
In particular, `@RestServer.Listener` supplies a literal listener name.

### Implementation

A `__HttpFeature` class is code generated for each `@RestServer.Endpoint`.
This type registers handlers through `HttpEntryPoint.EntryPoints`, which invokes registry-provided entry point interceptors.
The feature is an `HttpFeature` picked up by the WebServer starter service.
In case a `Http.Produces` or `Http.Consumes` is defined on an endpoint type or method, the route tests the
Accept/Content-Type headers respectively, and only invokes the method if both match. Method annotations replace endpoint type
defaults; an empty method annotation clears the corresponding default. Type-level `Http.Consumes` applies only to endpoint
methods with request entities.

For each qualified parameter, the parameter is obtained from the request using generated code that uses constants wherever
possible (for header names, header values, media types etc.).

When a method accepts `ServerResponse`, generated response metadata is applied before invocation. A `void` method's
response is sent automatically only if the method did not already handle it; a returned value is sent as the response entity.

## HTTP Declarative Client

Defines a Client HTTP API.
Each method is a representation of a server side route.
The declarative client shares annotations from the `io.helidon.http.Http` with server endpoint declaration, so the same interface
can be used to define both server-side and client-side API.

### Declaration

Declaration must be done on an interface.

Annotations on type:

- `@RestClient.Endpoint` - required annotation to generate a typed rest client
- `@RestClient.Header` - a header to be sent with every request (repeatable)
- `@RestClient.ComputedHeader` - a header to be sent with every request computed from a service
- `@Http.Produces` - default media types produced by the server, individually replaceable on a method
- `@Http.Consumes` - default media types consumed by the server for methods with request entities, individually replaceable on a method
- `@Http.Path` - base path of every request from this client

Annotations on the interface method(s):

- `@Http.Path` - path of this method (sub-path of the path defined on the type)
- `@RestClient.Header` - a header to be sent with every request (repeatable)
- `@RestClient.ComputedHeader` - a header to be sent with every request computed from a service
- `@Http.GET`, `@Http.POST` etc., or `@Http.HttpMethod("LIST")` - mutually exclusive, to define the HTTP method that the client will
  invoke
- `@Http.Produces` - the media type produced by the server (client response); replaces the client type default (an empty array clears it)
- `@Http.Consumes` - the media type expected by the server (client request); replaces the client type default (an empty array clears it)

Parameters defined by qualifiers (may be an `Optional`, supports `Mappers`):

- `@Http.HeaderParam` - a named header for the request
- `@Http.QueryParam` - a named query parameter
- `@Http.PathParam` - a named parameter for the definition of `@Http.Path`
- `@Http.CookieParam` - a named cookie for the request
- `@Http.FormParam` - a named URL-encoded form parameter
- `@Http.Entity` - the HTTP request entity
- `@Http.RequestParams` - a record grouping annotated request-parameter components

Named headers, query parameters, cookies, and form parameters also support `List<T>` and `Optional<List<T>>`.
Form parameters require `application/x-www-form-urlencoded` consumption and cannot be combined with an entity parameter,
including one inside a request-parameter record.

To use a declarative REST client, inject the annotated interface into your code, using the `@RestClient.Client` qualifier
for the injection point:

```java

@Service.Inject
MyClass(@RestClient.Client MyRestClient restClient) {
}
```

To create an error handler, create a service that implements `io.helidon.webclient.api.RestClient.ErrorHandler`

### Configuration

`@RestClient.Endpoint.value()` supplies the target URI and supports configuration expressions, such as
`${my-client.uri:http://localhost:8080}`. The generated client obtains a `WebClient` from the registry, qualified by
`clientName` when supplied, and creates a default client if none is available. It supplies the resolved endpoint URI for
each request. Configure a backing registry service when custom WebClient settings are required.

The generator currently does not consume the annotation's `configKey` property or read `uri` and `client` children under
that key.

### Implementation

A class named `AnnotatedInterfaceName__DeclarativeClient` will be generated for types annotated with `@RestClient.Endpoint`.
This class will implement all of the interface methods, and it will use a configured instance of Helidon WebClient to invoke all
requests.

The implementation uses constants wherever possible (header names, header values, media types etc.).

## gRPC Declarative Server

Defines a declarative gRPC server endpoint. Each method represents a gRPC method on the named service.

### Declaration

Declaration must be done on a service registry service.

Annotations on type:

- `@RpcServer.Endpoint` - required annotation to generate a declarative gRPC server endpoint
- `@Grpc.GrpcService` - required, non-blank gRPC service name; use the fully-qualified service name when the proto declares a package.
- `@Grpc.ProtoDescriptor` - generated protocol buffer class with a static `getDescriptor()` method returning
  `Descriptors.FileDescriptor`
- `@Service.Singleton` - typical service registry scope for the endpoint implementation

`@Service.PerRequest` is not supported for declarative gRPC endpoints because gRPC calls do not participate in the
WebServer HTTP request scope. Use `@Service.Singleton` or `@Service.PerLookup` instead.

The endpoint must declare exactly one proto descriptor source: either `@Grpc.ProtoDescriptor` on the type or one
`@Grpc.Proto` method. The referenced generated protocol buffer class must provide a public static `getDescriptor()`
method returning `Descriptors.FileDescriptor`. An `@Grpc.Proto` method may be static or an endpoint instance method;
it must be non-private, have no parameters or checked exceptions, and return `Descriptors.FileDescriptor`.
Each request and response type must implement `com.google.protobuf.Message` and declare public static no-argument
`getDescriptor()` and `getDefaultInstance()` methods returning `Descriptors.Descriptor` and the message type,
respectively. At runtime, the generated registration verifies that these descriptors match the input and output
descriptors of the named proto method.

Annotations on endpoint methods:

- `@Grpc.Unary` - unary gRPC method
- `@Grpc.ServerStreaming` - server-streaming gRPC method
- `@Grpc.ClientStreaming` - client-streaming gRPC method
- `@Grpc.Bidirectional` - bidirectional streaming gRPC method

Supported server method signatures:

- Unary: `Res method(Req)`, `Optional<Res> method(Req)`, or `void method(Req, StreamObserver<Res>)`
- Server streaming: `Stream<Res> method(Req)` or `void method(Req, StreamObserver<Res>)`
- Client streaming: `Res method(Stream<Req>)`
- Bidirectional streaming: `Stream<Res> method(Stream<Req>)` or
  `StreamObserver<Req> method(StreamObserver<Res>)`

Use `Optional<Res>` for unary lookups where an absent result means the requested resource was not found:

```java
@Grpc.Unary("GetBook")
Optional<Book> getBook(GetBookRequest request) {
    return books.findById(request.getId()).map(this::toProto);
}
```

A present value produces one response. An empty optional fails the RPC with `NOT_FOUND` and no response message.
The wrapped `Res` must match the proto method's output type. This signature is server-only; the proto definition and
client signatures still use `Res`, and clients receive a status error for an absent result.
If absence should be successful, return a protobuf response that represents it, such as a message with an optional field
or `google.protobuf.Empty`. A present default protobuf instance is also a valid response.
Returning `null` from either direct-return unary signature fails with `INTERNAL` and a diagnostic identifying the method.
Use the observer signature when custom statuses or trailers are needed.

Declarative streaming methods use resource-owning `Stream` instances with transport backpressure and cancellation.
Endpoint implementations consume request streams, while the generated runtime owns and closes both request streams
supplied to an endpoint and response streams returned by an endpoint. Endpoint implementations transfer ownership of
response streams to the runtime and must not close a response stream before returning it.

### Configuration

The generated `GrpcRouteRegistration` is registered by default. It can be disabled with:

- `server.features.grpc-route-registration.enabled=false`

Annotate a server endpoint with `@RpcServer.Listener("admin")` to register it on a named listener.

Security annotations require the `helidon-webserver-grpc-security` runtime module and normal Helidon security
configuration. The gRPC security service is discovered from the classpath and enabled by default; disable it with
`grpc.grpc-services.security.enabled=false`.

Validation annotations require the generated validation interceptor. To map `ValidationException` to gRPC
`INVALID_ARGUMENT`, add `helidon-webserver-grpc-validation`. The status mapper is discovered from the classpath and
enabled by default. It is configured under `grpc.grpc-services.validation`.

### Implementation

A class named `AnnotatedTypeName__GrpcRegistration` will be generated for types annotated with `@RpcServer.Endpoint`.
This class registers the generated `GrpcServiceDescriptor` using the fully-qualified gRPC service name.

## gRPC Declarative Client

Defines a typed gRPC client API. Each method represents a gRPC method on the named service.

### Declaration

Declaration must be done on an interface.

Annotations on type:

- `@RpcClient.Endpoint` - required annotation to generate a typed gRPC client
- `@Grpc.GrpcService` - required, non-blank gRPC service name; use the fully-qualified service name when the proto
  declares a package.
- `@Grpc.ProtoDescriptor` - generated protocol buffer class with a static `getDescriptor()` method returning
  `Descriptors.FileDescriptor`

The endpoint must declare exactly one proto descriptor source: either `@Grpc.ProtoDescriptor` on the type or one
`@Grpc.Proto` method. The referenced generated protocol buffer class must provide a public static `getDescriptor()`
method returning `Descriptors.FileDescriptor`. An `@Grpc.Proto` method may be static or a default interface method;
it must be non-private, have no parameters or checked exceptions, and return `Descriptors.FileDescriptor`.
Each request and response type must implement `com.google.protobuf.Message` and declare public static no-argument
`getDescriptor()` and `getDefaultInstance()` methods returning `Descriptors.Descriptor` and the message type,
respectively. At runtime, the generated client verifies that these descriptors match the input and output descriptors
of the named proto method.

Annotations on the interface method(s):

- `@Grpc.Unary` - unary gRPC method
- `@Grpc.ServerStreaming` - server-streaming gRPC method
- `@Grpc.ClientStreaming` - client-streaming gRPC method
- `@Grpc.Bidirectional` - bidirectional streaming gRPC method

Supported client method signatures:

- Unary: `Res method(Req)` or `void method(Req, StreamObserver<Res>)`
- Server streaming: `Stream<Res> method(Req)` or `void method(Req, StreamObserver<Res>)`
- Client streaming: `Res method(Stream<Req>)` or `StreamObserver<Req> method(StreamObserver<Res>)`
- Bidirectional streaming: `Stream<Res> method(Stream<Req>)` or
  `StreamObserver<Req> method(StreamObserver<Res>)`

Returned streams own the RPC and must be closed when the caller stops before normal exhaustion. The client consumes and
closes request streams on normal completion, cancellation, or failure; a transferred request stream must not be reused.
The calling thread consumes a client-streaming request stream. If producing elements can block, closing the stream must
unblock production so an early peer termination can return promptly.

To use a declarative gRPC client, inject the annotated interface using the `@RpcClient.Client` qualifier:

```java
@Service.Inject
MyClass(@RpcClient.Client MyGrpcClient grpcClient) {
}
```

### Configuration

The `@RpcClient.Endpoint.value()` property defines the target URI for generated backing clients and supports
configuration expressions, such as `http://localhost:${test.server.port}`. Registry-provided clients keep their own
base URI.

The base of configuration for a declarative gRPC client is the fully qualified name of the annotated interface. This key
can be modified using `configKey` property of the `@RpcClient.Endpoint` annotation.

There is one key that can be defined under this key:

- `client` - configuration options of Helidon `GrpcClient`

Client resolution order:

1. If `<configKey>.client` exists, create a dedicated `GrpcClient` from that configuration and apply
   `@RpcClient.Endpoint.value()` as its base URI.
2. Otherwise, if `@RpcClient.Endpoint.clientName()` is defined and exists in the registry, use that named `GrpcClient`.
3. Otherwise, if `@RpcClient.Endpoint.clientName()` is defined and no matching named `GrpcClient` exists, create a new
   `GrpcClient` using `@RpcClient.Endpoint.value()`.
4. Otherwise, if an unnamed `GrpcClient` exists in the registry, use it.
5. Otherwise, create a new `GrpcClient` using `@RpcClient.Endpoint.value()`.

### Implementation

A class named `AnnotatedInterfaceName__GrpcClient` will be generated for types annotated with `@RpcClient.Endpoint`.
This class will implement all of the interface methods, and it will use a configured or registry-provided instance of
Helidon `GrpcClient` to invoke all requests.

## Scheduling

Annotated method(s) of a service will be invoked with the schedule defined by the annotation.
When the registry is shut down, all the scheduled tasks are closed.

### Declaration

Annotations (mutually exclusive):

- `@Scheduling.Cron` - on a method
- `@Scheduling.FixedRate` - on a method

Parameters:

- `io.helidon.scheduling.CronInvocation` for `@Scheduling.Cron`, not required
- `io.helidon.scheduling.FixedRateInvocation` for `@Scheduling.FixedRate`, not required

Scopes:

- The service with annotated method(s) can be a `Singleton` or `PerLookup` scope.
- The generated `ScheduledStarter` is a `Singleton`

### Configuration

Cron expressions and fixed-rate intervals and initial delays support configuration expressions. For example,
`@Scheduling.FixedRate(value = "${jobs.refresh.interval:PT10S}", delayBy = "${jobs.refresh.delay:PT0S}")` resolves those
properties when creating the task. There is no implicit per-method configuration subtree or `configKey` annotation property.

### Implementation

For each class with at least one annotated method, a `__ScheduledStarter` class is generated with
`@Service.RunLevel(io.helidon.declarative.codegen.RunLevels.SCHEDULING)`.
If a `@Weight` is defined on the service, the generated starter has the same weight (this allows ordering of triggering of
scheduled tasks)
The class has a `@Service.PostConstruct` method that creates the tasks, and a
`@Service.PreDestroy` method that closes them.

## Fault Tolerance

Support for fault-tolerance features. In most cases, fault tolerance is an interceptor, that makes sure the method is invoked as
expected.

### Declaration

Annotations:

- `Ft.Fallback` - defines a method to fallback to in case an exception is thrown
- `Ft.Async` - runs the method asynchronously in an executor service
- `Ft.Retry` - retries the method in case an exception is thrown
- `Ft.Timeout` - a timeout exception is thrown in case the method takes longer than defined
- `Ft.Bulkhead` - limits parallel execution of the method
- `Ft.CircuitBreaker` - "breaks" the circuit in case exceptions are thrown, automatically throwing an exception, until the method
  starts returning without exception again

Parameters:
Fault tolerance preserves invocation arguments. A fallback method must be non-private, have the same return type and
parameter types as the intercepted method, and may append a `Throwable` parameter.

### Configuration

For the cases where named instances are supported, you can create a custom service instance named according to the name from
annotation.
Configuration override for annotation values is currently not supported.

### Implementation

#### Fallback

An element interceptor class `ClassName_methodName__Fallback` is generated for each `@Ft.Fallback` annotated method.
The generated type implements the fallback functionality without the use of reflection. The `fallback` method decides whether to
re-throw the exception (if it should be skipped), or calls the fallback method (if it should be applied).

#### Async

An element interceptor class `ClassName_methodName__Async` is generated for each `@Ft.Async` annotated method.
The `Async` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If
not available, a new async is produced.
The `ExecutorService` used with produced async can be named - in this case the generated code tries to get a named executor from
the service registry and use it. If not available, no executor is explicitly configured.

#### Retry

An element interceptor class `ClassName_methodName__Retry` is generated for each `@Ft.Retry` annotated method.
The `Retry` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If
not available, a new retry instance is produced from annotation properties.

#### Timeout

An element interceptor class `ClassName_methodName__Timeout` is generated for each `@Ft.Timeout` annotated method.
The `Timeout` instance can be named - in this case the generated code tries to get a named instance from the registry and use it.
If not available, a new timeout instance is produced from annotation properties.

#### Bulkhead

An element interceptor class `ClassName_methodName__Bulkhead` is generated for each `@Ft.Bulkhead` annotated method.
The `Bulkhead` instance can be named - in this case the generated code tries to get a named instance from the registry and use it.
If not available, a new bulkhead instance is produced from annotation properties.

#### Circuit Breaker

An element interceptor class `ClassName_methodName__CircuitBreaker` is generated for each `@Ft.CircuitBreaker` annotated method.
The `CircuitBreaker` instance can be named - in this case the generated code tries to get a named instance from the registry and
use it. If not available, a new circuit breaker instance is produced from annotation properties.

## Declarative Validation

Validation provides capabilities to validate types and method parameters/return values.
Both features require code-generation.

Type validation code-generation of type validators is triggered by the presence of the `@Validation.Validated` annotation on a type.

Method validation code-generation of interceptors is triggered by the presence of an annotation "meta-annotated"
with `@Validation.Constraint` on a service method, its implemented service-contract method, its parameter, or type
arguments of its parameters or return type.
In addition the `@Validation.Valid` annotation also triggers code-generation, and can be used to validate against a type validator
mentioned above.

### Declaration

Annotations:
- All built-in constraint annotations from `io.helidon.validation.Validation` to trigger validation using an interceptor
- `@Validation.Valid` - to trigger validation against a generated type validator
- `@Validation.Validated` - on a type to generate type validator for a type

### Configuration
There is no global configuration for generated validation interceptors.

For HTTP endpoints, `helidon-webserver-validation` maps `ValidationException` to HTTP 400. This feature is enabled by
default and can be disabled with `server.features.validation.enabled=false`.

For declarative gRPC server endpoints, `helidon-webserver-grpc-validation` adds a server-side status mapper. It is
discovered from the classpath and enabled by default, is configured under `grpc.grpc-services.validation`, and can be
disabled with:

- `grpc.grpc-services.validation.enabled=false`

### Implementation

Each constraint has a dedicated validator provider service, with (default-weight - 30) weight. 
The providers are qualified with `@Service.NamedByType(ConstraintAnnotation.class)` for the constraint they implement.

This allows our users to override the implementation using their custom services.

The validation process works as follows:
1. an interceptor is generated for any method that has constraint annotations or valid annotation
2. the interceptor obtains all constraint validation instances and type validators needed to validate a call
3. the interceptor validates the call and throws a `ValidationException` if validation fails

The type validation works as follows:
1. a type validator is generated for any type that has a `@Validation.Validated` annotation
2. the type validator obtains all constraint validation instances and type validators needed to validate an instance
3. the type validator validates the type and returns response with possible constraint violations

Important types:
- `Validation` - a container class for validation annotations and built-in constraint annotations
- `ValidationException` - thrown when validation fails in an interceptor
- `Validator` - programmatic API to validate instances and their properties (only for validated types), can be obtained from service registry
- `ConstraintValidatorProvider` - service registry service that validates a single constraint annotation type
- `ConstraintValidator` - created for each annotated element using the type of the element and the constraint annotation
- `validators` package contains built-in constraint validator providers

Supported concepts:
- selected service constructors and non-private injected instance fields can be validated by generated service interception
- any service method annotated with a constraint annotation will be intercepted and validated
- any service method that has parameters with at least one constraint annotation will be intercepted and validated
- any service method that implements a non-private service-contract method with constraint annotations or `@Validation.Valid`
    will be intercepted and validated
- services returned by registry-managed factories such as `Supplier`, `Service.ServicesFactory`,
    `Service.InjectionPointFactory`, and `Service.QualifiedFactory` follow the same service-contract validation rules
- `@Validation.Valid` identifies what to validate using a generated type validator
- annotations on method parameter and return types are supported on nested `Optional`, `Collection`, `List`, `Set`, `Map`
    key/value types, array component types, and wildcard bounds - i.e. `List<@Validation.Valid MyType>` will trigger an
    interceptor and will be validated
- types annotated with `@Validation.Validated` will be validated when used in a method or constructor that uses
    `@Validation.Valid` on the typed parameter
- same rules as for generation of interceptors apply for type validation: constraints are honored, including getters,
    fields, and type arguments
- a user may create a "compound annotation" - an annotation that has one or more constraint annotations, these will be honored as
    if the element was directly annotated with the constraints
- a user may create a custom constraint annotation (annotation meta-annotated with `@Validation.Constraint`), such annotations
    may also be meta-annotated with additional constraints; a custom constraint annotation requires a custom validator provider

## Configuration and Service Registry

`@Configuration.Value` injects a configuration value into a service; annotations in `io.helidon.common.Default` supply
typed defaults. `Config` itself is also available for injection. Configuration factories can expose configured feature
instances as services. See [Injection](../docs/modules/injection/injection.md) for scopes, factories, lifecycle, events,
and generated application bindings.

The Service Registry generator and runtime provide the shared infrastructure for declarative features. Start the registry
with the generated application binding to activate services with a run level and to manage their shutdown.

## WebSocket Server and Client

`@WebSocketServer.Endpoint` marks a server endpoint, with `@Http.Path` for its path and `@WebSocketServer.Listener` for
listener selection. `@WebSocketClient.Endpoint` marks a client endpoint class. Both use callbacks in
`io.helidon.websocket.WebSocket`: `@OnMessage`, `@OnOpen`, `@OnClose`, `@OnError`, and `@OnHttpUpgrade`.

Callback parameters expose the session, message, close status, or error, as appropriate. `@Http.PathParam` supplies typed
path parameters. Message handlers can receive text or binary messages, fragments with a trailing `boolean` indicator,
or streaming `Reader`/`InputStream` input. See the [WebSocket declarations](../docs/modules/injection/declarative.md#websocket-server)
for supported signatures and buffering limits.

Server code generation creates `__WsListener` and `__WsRegistration` classes. Client code generation creates a
`__WsListener` and an injectable factory, named `<Endpoint>Factory` by default, whose `connect` methods initiate sessions.
The client endpoint URI supports configuration expressions. Runtime protocol options belong to the WebSocket server
or `WsClient` configuration.

## GraphQL Server

`@GraphQlServer.Endpoint` marks a service containing `@GraphQl.Query` and `@GraphQl.Mutation` methods.
`@GraphQlServer.Field` and `@GraphQlServer.Source` define child resolvers. `@GraphQl.Entity` marks schema model types;
annotations such as `@GraphQl.Argument`, `@GraphQl.Name`, `@GraphQl.NonNull`, and `@GraphQl.Description` refine the schema.

The generator produces a `__GraphQlFeature` with schema definitions and direct resolver invocations.
`@GraphQlServer.Listener`, `@GraphQlServer.Context`, and `@GraphQlServer.SchemaUri` control endpoint registration.
Queries, mutations, and child resolvers are implemented; `@GraphQl.Subscription` is reserved and ignored, and there is no
declarative `GraphQlClient` API. See the [GraphQL guide](../docs/modules/graphql.md#declarative-api) for supported types,
resolver parameters, security, and configuration.

## OpenAPI

`OpenApi` annotations describe documents, operations, parameters, request bodies, responses, and security schemes.
Annotate an HTTP endpoint with `@OpenApi.Endpoint` to opt into generation, or use endpoint-applicable OpenAPI metadata on
the endpoint, its methods, or their parameters. `@OpenApi.Document` and `@OpenApi.Info` provide document metadata.
`@JsonSchema.Schema` supplies schemas for application model types.

Code generation creates `OpenApiDocumentSource` implementations from endpoint metadata and Java signatures.
The runtime `OpenApiFeature` combines these sources according to its generated-document configuration and serves the
document. Static documents, generated documents, and merging are supported. See the
[OpenAPI guide](../docs/modules/openapi/openapi.md) for annotation placement, configuration-expression support, document
selection, and OpenAPI version modules.

## Security

Security uses the existing `io.helidon.security.annotations` annotations, including `@Authenticated`, `@Authorized`, and
`@Audited`, together with role annotations such as `@RoleValidator.Roles` and Jakarta security annotations.
There is no `Secured` namespace class.

Endpoint code generation supplies annotation metadata to the protocol's runtime security integration. HTTP endpoints use
the WebServer security feature, GraphQL has resolver security integration, and gRPC uses `helidon-webserver-grpc-security`.
Security providers and policies are configured through the corresponding runtime modules. See the
[declarative security guide](../docs/modules/injection/declarative.md#security) and the protocol-specific documentation.

## Metrics and Tracing

`@Metrics.Counted` and `@Metrics.Timed` generate method interceptors. `@Metrics.Gauge` registers a method as a gauge;
`@Metrics.Tag` supplies tags at type, method, or meter level. Gauges are registered through a generated startup service.

`@Tracing.Traced` creates spans around service methods; `@Tracing.Tag` and `@Tracing.ParamTag` provide fixed and
parameter-derived tags. Generated interceptors use the metrics and tracing runtimes and their registry services.
Method annotations on service contracts and typed HTTP client interfaces are supported; factory-provided contracts are
not included in this generation. See [Metrics](../docs/modules/injection/declarative.md#metrics) and
[Tracing](../docs/modules/injection/declarative.md#tracing) for annotation inheritance and defaults.

## CORS

`Cors` annotations configure CORS for HTTP endpoints. They can appear on an endpoint type or its HTTP `OPTIONS` methods.
`@Cors.Defaults` selects defaults; `@Cors.AllowOrigins`, `@Cors.AllowMethods`, `@Cors.AllowHeaders`,
`@Cors.ExposeHeaders`, `@Cors.AllowCredentials`, and `@Cors.MaxAgeSeconds` specify individual options.

The generator creates `CorsPathConfig` services consumed by the runtime CORS feature. String-valued sets support
configuration expressions. See [CORS](../docs/modules/cors.md) for runtime configuration and the
[declarative annotations](../docs/modules/injection/declarative.md#webserver-cors) for usage.

## Messaging

`@Messaging.ReceiveFrom` declares an incoming channel on a service method; `@Messaging.SendTo` routes a processor's result
to another channel. Methods can consume payloads, `Message<T>` envelopes, or `MessageBatch<T>` batches.
`@Messaging.Entity` identifies a payload parameter and `@Messaging.HeaderParam` reads message headers.
Applications publish through an injected `@Service.Named("channel") Emitter<T>`.

Generated consumer registrations and emitter factories connect application code to the runtime graph. The Service Registry
starts and stops the graph. Configuration under `messaging.connector`, `messaging.incoming`, and `messaging.outgoing`
connects logical channels to transport connectors. `@Messaging.OnFailure` supplies default incoming connector delivery
failure policy; it does not retry local emitter calls. See the [Messaging README](../messaging/README.md) for supported
signatures, configuration, delivery semantics, and the connector SPI.

## Health Checks

Register `HealthCheck` or `HealthCheckProvider` services with the Service Registry. The WebServer health observer
discovers them and includes their results in health responses. Use a lifecycle appropriate for a service retained by the
observer, typically `@Service.Singleton`, rather than `@Service.PerRequest`.
There is no dedicated health annotation namespace or declarative code generator. See the
[health guide](../docs/modules/health.md).

## Data and Transactions

`@Data.Repository` marks repository interfaces whose implementations are generated by the selected Data provider.
JDBC repositories use `@Jdbc.Client`, `@Jdbc.Statement`, and related `Jdbc` annotations for client selection, SQL,
execution, and result mapping. Jakarta Persistence repositories use the Jakarta Persistence model and their provider's
repository support. The generators live under `data/`, and the runtime exposes repositories and configured data services
through the Service Registry. See [Helidon Data](../data/README.md) and the
[Data JDBC declarative guide](../docs/modules/data-jdbc/declarative.md).

`@Tx.Required`, `@Tx.New`, `@Tx.Mandatory`, `@Tx.Never`, `@Tx.Supported`, and `@Tx.Unsupported` select transaction
behavior for service types or methods. Service Registry interception delegates to the transaction runtime and its
`TxSupport` integration; this does not require a transaction-specific generator in `declarative/codegen`.

## Builders, JSON Binding, and JSON Schema

Builders use `Prototype`, `Option`, and `RuntimeType` annotations to generate immutable APIs, builders, and configuration
support from blueprints. Their generator is in `builder/codegen`; see the [builder guide](../docs/modules/builder.md).

`@Json.Entity` triggers JSON binding generation, with additional `Json` annotations controlling property mapping and
custom serialization. `@JsonSchema.Schema` triggers JSON Schema generation; the generated schemas also support
declarative OpenAPI model descriptions. These generators live in `json/codegen` and `json/schema/codegen`.
See the [JSON documentation](../docs/modules/json/README.md) and [JSON Schema guide](../docs/modules/json/schema.md).
