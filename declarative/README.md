Helidon Declarative
----

_This documentation is for developers of Helidon, or for developers of additional features for Helidon Declarative_

A declarative programming model for Helidon SE.

_Declarative_: a programming model where we declare intention by annotating elements, to achieve functionality that would otherwise require significant programming effort


Rules for Helidon Declarative:
1. Required APIs will be part of the existing SE module (Annotations, support for generated code, new APIs)
2. Annotations will use the "nested" approach we have started with builders, i.e. `@Http.Path`; the class that annotations are nested in should not be used in any other way (i.e. it should not have methods); find an alternative name if an existing class would be the best fit, or deprecate existing methods and move them elsewhere (See `FaultTolerance` vs. `Ft`)
3. The code generation for all declarative features that are part of Helidon will be done in `declarative/codegen`, this module can have packages for each feature; as this module does not depend on any other feature module, and only triggers based on feature annotations, it is safe to collect the code generation classes together
4. It is forbidden to use reflection in any declarative feature; if reflection seems to be needed, replace it with code-generated type (example: fault tolerance fallback needs to invoke a fallback method, as this would require reflection, there is a generated type such as `GreetEndpoint_failingFallback__Fallback` that is named with the unique identification of the method it is generated for, and has the required code generated, the interceptor for fallback then looks it up at runtime to correctly handle invocation)
5. If there is a good reason the user may want to override configuration in annotations with config, generate the code appropriately (the configuration key should be <fully qualified class name>.<(method | field) name>.<type>, i.e. `io.helidon.examples.decalarative.HttpEndpoint.retriable.retry`), or an explicit key configured on the annotation
6. If there is a good reason the user may want to use a custom named service implementation, provide a way to inject it (see Retry generated code for named retries, such as `GreetEndpoint_retriable__Retry.java`)
7. All features must be configured through service registry. 


A few codegen features that are available:
- `io.helidon.codegen.spi.TypeMapperProvider` to map a `TypeInfo` created by codegen to a new `TypeInfo` - this is useful to add annotations, remove annotations etc. before code generation starts
- `io.helidon.codegen.spi.ElementMapperProvider` to map a `TypedElementInfo` - similar as above, but on the level of methods and fields
- `io.helidon.codegen.spi.AnnotationMapperProvider` to map annotations (i.e. we could map `@Autowired` to `@Service.Inject`)
- `io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider` (or the "hard-core" `io.helidon.codegen.spi.CodegenExtensionProvider`) to create extensions to build custom generated types - example can be found in `io.helidon.declarative.codegen.scheduling.SchedulingExtensionProvider`

# How to build a feature

The following Helidon features can be used to create a new declarative feature 

TODO: metrics, tracing - maybe allow update of service code
1. Interceptors - metrics, tracing, logging etc.
2. Injection (service factory) - for any feature where we expect the user to inject a specific service that the feature provides (AI, declarative rest client etc.)
3. Code generation - for any feature that needs additional code to minimize runtime lookups and handling; ideally we should have injection points that can be bound at build time (as opposed to runtime registry lookups)


# Declarative Codegen Module

All feature codegens belong to this module.

There are a few types in the top level package:
- `RunLevels` - all run levels used by our features, to have a single place where we can see it, any `@RunLevel` annotation generated MUST use a value from this type
- `DeclarativeTypes` - `TypeName` constants for common types that are not defined in `io.helidon.service.codegen.ServiceCodegenTypes`

See feature codegen details in the [Codegen Readme](codegen/README.md).

# Entry points

Each method that is invoked for an external trigger is considered an entry point (HTTP method, grpc method etc.).
There is a set of tools to create entry point interceptors (one of the main reasons is to support security, running within a context etc.).

# Features

Definition of Helidon declarative features.

## HTTP Server Endpoints

Defines a Server HTTP Service.
Each method is a route with its own path (see declaration below).
The method has a choice of using the "core" Helidon SE approach - getting `ServerRequest` and `ServerResponse` as parameters and doing everything manually, or by using qualified parameters to obtain the desired information from the request, and returning an object that will be sent as the response entity.
To a certain degree, these approaches can be combined (i.e. we can get `ServerRequest` and return an entity, or get annotated parameters and `ServerResponse`).

### Declaration

Annotations on type:
- `@RestServer.Endpoint` - required annotation on a type representing the endpoint
- `@RestServer.Listener` - optional annotation that allows selecting a specific listener (socket) to use
- `@RestServer.Header` - a header to be sent with every response from the server, defined either on the endpoint class, or on a method (repeatable)
- `@RestServer.ComputedHeader` - a header to be sent with every response computed from a service
- `@Http.Path` - the path this endpoint will be available on

Annotations on method(s), may be defined on the endpoint type, or on an interface the endpoint type implements:
- `@RestServer.Status` - define HTTP status to return from a method when the default is not good
- `@Http.GET`, `@Http.POST` etc., or `@Http.Method("LIST")` - mutually exclusive, to define the HTTP method that the endpoint method will be available on
- `@Http.Produces` - the media type produced by this method (returned in the `Content-Type` header), also used when matching the `Accept` header of the request
- `@Http.Consumes` - the media type expected by this method, when the request has an entity 
- `@Http.Path` - the path this method will be available on, nested within the endpoint path, may contain path parameters (same as we can do when setting up routing)


Parameters defined by type:
- `ServerRequest` - SE webserver request
- `ServerResponse` - SE webserver response
- `io.helidon.common.context.Context` - server request context
- other parameters as supported by code generators for other features (i.e. `SecurityContext` will be supported as soon as security feature is implemented) - see `io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider`

Parameters defined by qualifiers (may be an `Optional`, supports `Mappers`):
- `@Http.HeaderParam` - a named header from the request 
- `@Http.QueryParam` - a named query parameter
- `@Http.PathParam` - a named parameter from the definition of `@Http.Path`
- `@Http.Entity` - the HTTP request entity

### Configuration

TODO: we must have Listener name configurable per rest server endpoint
There are currently no configurable options for HTTP endpoints.

### Implementation

A `__HttpFeature` class is code generated for each `@RestServer.Endpoint`. 
This type creates entry point interceptors for each method. 
The feature is a "usual" `HttpFeature` picked up by WebServer starter service.
In case a `Http.Produces` or `Http.Consumes` is defined on a method, the route tests the Content-Type/Accept headers respectively, and only invokes the method if both match.

For each qualified parameter, the parameter is obtained from the request using generated code that uses constants wherever possible (for header names, header values, media types etc.).

## HTTP Declarative Client

Defines a Client HTTP API.
Each method is a representation of a server side route.
The declarative client shares annotations from the `io.helidon.http.Http` with server endpoint declaration, so the same interface can be used to define both server-side and client-side API.

### Declaration

Declaration must be done on an interface.

Annotations on type:
- `@RestClient.Endpoint` - required annotation to generate a typed rest client
- `@RestClient.Header` - a header to be sent with every request (repeatable)
- `@RestClient.ComputedHeader` - a header to be sent with every request computed from a service
- `@Http.Path` - base path of every request from this client

Annotations on the interface method(s):
- `@Http.Path` - path of this method (sub-path of the path defined on the type)
- `@RestClient.Header` - a header to be sent with every request (repeatable)
- `@RestClient.ComputedHeader` - a header to be sent with every request computed from a
- `@Http.GET`, `@Http.POST` etc., or `@Http.Method("LIST")` - mutually exclusive, to define the HTTP method that the client will invoke
- `@Http.Produces` - the media type produced by the server (client response)
- `@Http.Consumes` - the media type expected by the server (client request)

Parameters defined by qualifiers (may be an `Optional`, supports `Mappers`):
- `@Http.HeaderParam` - a named header for the request
- `@Http.QueryParam` - a named query parameter
- `@Http.PathParam` - a named parameter for the definition of `@Http.Path`
- `@Http.Entity` - the HTTP request entity

To use a declarative rest client, simply inject the annotated interface it into your code, using `@RestClient.Client` qualifier for the injection point:

```java
@Service.Inject
MyClass(@RestClient.Client MyRestClient restClient) {
}
```

To create an error handler, create a service that implements `io.helidon.webclient.api.RestClient.ErrorHandler`

### Configuration

In case `@RestClient.Endpoint.clientName()` is defined and exists in the registry, all WebClient configuration will be ignored (except for the URI).

The `@RestClient.Endpoint` may define a `value()` with the URI of the endpoint. If it is empty, the value MUST be provided by configuration, otherwise it can be overridden from configuration.

The base of configuration for a declarative client is the fully qualified name of the annotated interface. This key can be modified using `configKey` property of the `@RestClient.Endpoint` annotation.

There are two keys that can be defined under this key:
- `uri` - the URI of the remote service (excluding the path as defined by `@Http.Path`)
- `client` - configuration options of Helidon WebClient

### Implementation

A class named `AnnotatedInterfaceName__DeclarativeClient` will be generated for types annotated with `@RestClient.Endpoint`.
This class will implement all of the interface methods, and it will use a configured instance of Helidon WebClient to invoke all requests.

The implementation uses constants wherever possible (header names, header values, media types etc.).

## Scheduling

Annotated method(s) of a service will be invoked with the schedule defined by the annotation.
When the registry is shutdown, all the scheduled tasks will be closed.

### Declaration

Annotations (mutually exclusive):
- `@Schedule.Cron` - on a method
- `@Schedule.FixedRate` - on a method

Parameters:
- `io.helidon.scheduling.CronInvocation` for `@Schedule.Cron`, not required
- `io.helidon.scheduling.FixedRateInvocation` for `@Schedule.FixedRate`, not required

Scopes:
- The service with annotated method(s) can be a `Singleton` or `PerLookup` scope.
- The generated `ScheduledStarter` is a `Singleton`

### Configuration
The schedule can be overridden by configuration, default configuration key is:
`<fully-qualified-class-name.method-name.schedule>`, i.e. `my.app.MyType.updateValues.schedule`, with the possibility to use a custom configuration key (through annotation property)

### Implementation
For each class with at least one annotated method, a `__ScheduledStarter` class is generated with
`@RunLevel(io.helidon.declarative.codegen.RunLevels.SCHEDULING)`.
If a `@Weight` is defined on the service, the generated starter will have the same weight (this allows ordering of triggering of scheduled tasks)
The class will have a `@PostConstruct` method that creates the tasks, and a
`@PreDestroy` that closes the tasks.


## Fault Tolerance

Support for fault-tolerance features. In most cases, fault tolerance is an interceptor, that makes sure the method is invoked as expected.

### Declaration

Annotations:
- `Ft.Fallback` - defines a method to fallback to in case an exception is thrown
- `Ft.Async` - runs the method asynchronously in an executor service
- `Ft.Retry` - retries the method in case an exception is thrown
- `Ft.Timeout` - a timeout exception is thrown in case the method takes longer than defined
- `Ft.Bulkhead` - limits parallel execution of the method
- `Ft.CircuitBreaker` - "breaks" the circuit in case exceptions are thrown, automatically throwing an exception, until the method starts returning without exception again 

Parameters:
Fault tolerance annotations ignore method parameters

### Configuration
For the cases where named instances are supported, you can create a custom service instance named according to the name from annotation.
Configuration override for annotation values is currently not supported.

### Implementation

#### Fallback
An element interceptor class `ClassName_methodName__Fallback` is generated for each `@Ft.Fallback` annotated method.
The generated type implements the fallback functionality without the use of reflection. The `fallback` method decides whether to re-throw the exception (if it should be skipped), or calls the fallback method (if it should be applied).

#### Async
An element interceptor class `ClassName_methodName__Async` is generated for each `@Ft.Async` annotated method. 
The `Async` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If not available, a new async is produced.
The `ExecutorService` used with produced async can be named - in this case the generated code tries to get a named executor from the service registry and use it. If not available, no executor is explicitly configured.

#### Retry
An element interceptor class `ClassName_methodName__Retry` is generated for each `@Ft.Retry` annotated method.
The `Retry` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If not available, a new retry instance is produced from annotation properties.

#### Timeout
An element interceptor class `ClassName_methodName__Timeout` is generated for each `@Ft.Timeout` annotated method.
The `Timeout` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If not available, a new timeout instance is produced from annotation properties.

#### Bulkhead
An element interceptor class `ClassName_methodName__Bulkhead` is generated for each `@Ft.Bulkhead` annotated method.
The `Bulkhead` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If not available, a new bulkhead instance is produced from annotation properties.


#### Circuit Breaker
An element interceptor class `ClassName_methodName__CircuitBreaker` is generated for each `@Ft.CircuitBreaker` annotated method.
The `CircuitBreaker` instance can be named - in this case the generated code tries to get a named instance from the registry and use it. If not available, a new circuit breaker instance is produced from annotation properties.

## Template
### Declaration

Annotations:
-

Parameters:
- 

### Configuration
### Implementation