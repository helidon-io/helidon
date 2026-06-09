# Declarative

## Overview

Helidon declarative programming model allows inversion of control style programming with all the performance benefits of Helidon SE.

Our declarative approach has the following advantages:

- Uses Helidon SE imperative code to implement features (i.e. performance is same as "pure" imperative application)
- Generates all the necessary code at build-time, to avoid reflection and bytecode manipulation at runtime
- It is based on [Helidon Injection](injection.md#overview)
- Declarative features are in the same modules as Helidon SE features (i.e. does not require additional dependencies)

> [!NOTE]
> Helidon Declarative is a preview feature. The APIs shown here are subject to change between major releases without deprecation, but they are intended for supported external use.

## Usage

To create a declarative application, use the annotations provided in our Helidon SE modules (details under [Features][features]), and the maven plugin described in [Injection: Startup][injection-startup] to generate the binding.

In addition, the following section must be added to the `build` of the Maven `pom.xml` to enable annotation processors that generate the necessary code:

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

## Features

The following features are currently implemented:

- [Configuration](#configuration)
- [HTTP Server Endpoint](#http-server-endpoint)
- [Typed HTTP Client](#typed-http-client)
- [Fault Tolerance](#fault-tolerance)
- [Scheduling](#scheduling)
- [Validation](#validation)
- [Security](#security)
- [Metrics](#metrics)
- [Tracing](#tracing)
- [WebSocket Server](#websocket-server)
- [WebSocket Client](#websocket-client)
- [WebServer CORS](#webserver-cors)
- [Health Checks](#health-checks)

A Helidon Declarative application should be started using the generated application binding, to ensure no lookup and no reflection. The call to `ServiceRegistryManager.start` ensures that all services with a defined `RunLevel` are started, including Helidon WebServer, Scheduled services etc.

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

Configuration can be injected as a whole into any service, or a specific configuration option can be injected using `@Configuration.Value`. Default values can be defined using annotations in `@Default`

Services available for injection:

- [`io.helidon.config.Config`][io-helidon-config-config]

Annotations:

- [`io.helidon.config.Configuration.Value`][io-helidon-config-configuration-value] - define the configuration key to inject, on constructor parameter
- Annotations defined in [`io.helidon.common.Default`][io-helidon-common-default] - define a default typed value, on the same constructor parameter

Example of usage can be seen below in HTTP Server Endpoint example.

### HTTP Server Endpoint

To create an HTTP endpoint, simply annotate a class with `@RestServer.Endpoint`, and add at least one method annotated with one of the HTTP method annotations, such as `@Http.GET`.

Services available for injection:

N/A

Supported method parameters (no annotation required):

- [`io.helidon.webserver.http.ServerRequest`][io-helidon-webserver-http-serverrequest]
- [`io.helidon.webserver.http.ServerResponse`][io-helidon-webserver-http-serverresponse]
- [`io.helidon.common.context.Context`][io-helidon-common-context-context]
- `io.helidon.common.security.SecurityContext`
- `io.helidon.security.SecurityContext` - in case `helidon-security` module is on the classpath

Annotations on endpoint type:

- [`io.helidon.webserver.http.RestServer.Endpoint`][io-helidon-webserver-http-restserver-endpoint] - required annotation
- [`io.helidon.webserver.http.RestServer.Listener`][io-helidon-webserver-http-restserver-listener] - to define the named listener this should be served on (named port/socket)
- [`io.helidon.webserver.http.RestServer.Header`][io-helidon-webserver-http-restserver-header] - header to return with each response from this endpoint
- [`io.helidon.webserver.http.RestServer.ComputedHeader`][io-helidon-webserver-http-restserver-computedheader] - computed header to return with each response from this endpoint
- [`io.helidon.http.Http.Path`][io-helidon-http-http-path] - path (context) this endpoint will be available on

Annotations on endpoint methods:

- [`io.helidon.webserver.http.RestServer.Header`][io-helidon-webserver-http-restserver-header] - header to return with each response from this method
- [`io.helidon.webserver.http.RestServer.ComputedHeader`][io-helidon-webserver-http-restserver-computedheader] - computed header to return with each response from this method
- [`io.helidon.webserver.http.RestServer.Status`][io-helidon-webserver-http-restserver-status] - status to return (if a custom one is required)
- [`io.helidon.http.Http.Path`][io-helidon-http-http-path] - path (context) this method will be available on (subpath of the endpoint path)
- [`io.helidon.http.Http.GET`][io-helidon-http-http-get] (and other methods) - definition of HTTP method this method will serve
- [`io.helidon.http.Http.HttpMethod`][io-helidon-http-http-httpmethod] - for custom HTTP method names (mutually exclusive with above)
- [`io.helidon.http.Http.Produces`][io-helidon-http-http-produces] - what media type this method produces (return entity content type)
- [`io.helidon.http.Http.Consumes`][io-helidon-http-http-consumes] - what media type this method accepts (request entity content type)

Annotations on method parameters:

- [`io.helidon.http.Http.Entity`][io-helidon-http-http-entity] - Request entity, a typed parameter is expected, will use HTTP media type modules to coerce into the correct type
- [`io.helidon.http.Http.HeaderParam`][io-helidon-http-http-headerparam] - Typed HTTP request header value
- [`io.helidon.http.Http.QueryParam`][io-helidon-http-http-queryparam] - Typed HTTP query value
- [`io.helidon.http.Http.PathParam`][io-helidon-http-http-pathparam] - Typed parameter from path template

Example of an HTTP Server Endpoint

```java
@RestServer.Endpoint // identifies this class as a server endpoint
@Http.Path("/greet") // serve this endpoint on /greet context root (path)
@Service.Singleton   // a singleton service (single instance within a service registry)
static class GreetEndpoint {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private final String greeting;

    // inject app.greeting configuration value, use "Hello" if not configured
    GreetEndpoint(@Configuration.Value("app.greeting") @Default.Value("Hello") String greeting) {
        this.greeting = greeting;
    }

    @Http.GET   // HTTP GET endpoint
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE) // produces entity of application/json media type
    public JsonObject getDefaultMessageHandler() {
        // build the JSON object (requires `helidon-http-media-jsonp` on classpath)
        return JSON.createObjectBuilder()
                .add("message", greeting + " World!")
                .build();
    }
}
```

### Typed HTTP Client

To create a typed HTTP client, create an interface annotated with `RestClient.Endpoint`, and at least one method annotated with one fo the HTTP method annotations, such as `@Http.GET`. Methods can only have parameters annotated with one of the `Http` qualifiers.

Annotations on endpoint type:

- [`io.helidon.webclient.api.RestClient.Endpoint`][io-helidon-webclient-api-restclient-endpoint] - required annotation
- [`io.helidon.http.Http.Path`][io-helidon-http-http-path] - path (context) the server listens on
- [`io.helidon.webclient.api.RestClient.Header`][io-helidon-webclient-api-restclient-header] - header to include in every request to the server
- [`io.helidon.webclient.api.RestClient.ComputedHeader`][io-helidon-webclient-api-restclient-computedheader] - header to compute and include in every request to the server

Annotations on endpoint methods:

- [`io.helidon.webclient.api.RestClient.Header`][io-helidon-webclient-api-restclient-header] - header to include in every request to the server
- [`io.helidon.webclient.api.RestClient.ComputedHeader`][io-helidon-webclient-api-restclient-computedheader] - header to compute and include in every request to the server
- [`io.helidon.http.Http.Path`][io-helidon-http-http-path] - path (context) the server serves this endpoint method on
- [`io.helidon.http.Http.GET`][io-helidon-http-http-get] (and other methods) - definition of HTTP method this method will invoke
- [`io.helidon.http.Http.HttpMethod`][io-helidon-http-http-httpmethod] - for custom HTTP method names (mutually exclusive with above)
- [`io.helidon.http.Http.Produces`][io-helidon-http-http-produces] - what media type this method produces (content type of entity from the server)
- [`io.helidon.http.Http.Consumes`][io-helidon-http-http-consumes] - what media type this method accepts (request entity content type)

Annotations on method parameters:

- [`io.helidon.http.Http.Entity`][io-helidon-http-http-entity] - Request entity, a typed parameter is expected, will use HTTP media type modules to write to the request
- [`io.helidon.http.Http.HeaderParam`][io-helidon-http-http-headerparam] - Typed HTTP header value to send
- [`io.helidon.http.Http.QueryParam`][io-helidon-http-http-queryparam] - Typed HTTP query value to send
- [`io.helidon.http.Http.PathParam`][io-helidon-http-http-pathparam] - Typed parameter from path template to construct the request URI

Example of a Typed HTTP Client

```java
@RestClient.Endpoint("${greet-service.client.uri:http://localhost:8080}")
@RestClient.Header(name = HeaderNames.USER_AGENT_NAME, value = "my-client")
interface GreetClient {
    @Http.GET
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    JsonObject getDefaultMessageHandler();
}
```

### Fault Tolerance

Fault tolerance annotations allow adding features to methods on services. The annotations can be added to any method that supports interception (i.e. methods that are not private).

Method Annotations:

- [`io.helidon.faulttolerance.Ft.Retry`][io-helidon-faulttolerance-ft-retry] - allow retries
- [`io.helidon.faulttolerance.Ft.Fallback`][io-helidon-faulttolerance-ft-fallback] - fallback to another method that provides
- [`io.helidon.faulttolerance.Ft.Async`][io-helidon-faulttolerance-ft-async] - invoke method asynchronously
- [`io.helidon.faulttolerance.Ft.Timeout`][io-helidon-faulttolerance-ft-timeout] - invoke method with a timeout
- [`io.helidon.faulttolerance.Ft.Bulkhead`][io-helidon-faulttolerance-ft-bulkhead] - use bulkhead
- [`io.helidon.faulttolerance.Ft.CircuitBreaker`][io-helidon-faulttolerance-ft-circuitbreaker] - use circuit breaker

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

- [`io.helidon.scheduling.Scheduling.Cron`][io-helidon-scheduling-scheduling-cron] - execute with schedule defined by a CRON expression
- [`io.helidon.scheduling.Scheduling.FixedRate`][io-helidon-scheduling-scheduling-fixedrate] - execute with a fixed interval

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

Configuration expressions is a reference to a configuration key, with optional default value:

`${config.key:default-value}`

### Validation

Validation provides an ability to validate service method parameters and return types. This is achieved through constraint annotations and type validation.

To use validation, the proper dependency must be added to your `pom.xml`, and an annotation processor must be configured to code generate the required classes. The annotation processor is part of the bundle mentioned in Helidon Declarative introduction above.

Helidon validation module:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.validation</groupId>
  <artifactId>helidon-validation</artifactId>
</dependency>
```

#### Constraint Annotations

A "Constraint Annotation" is any annotation directly annotated with `io.helidon.validation.Validation.Constraint`. Helidon Validation provides a set of built-in validation constraints, though custom constraints can be created, or existing constraints can be combined.

Existing constraints:

Constraints for any type:

- [`io.helidon.validation.Validation.NotNull`][io-helidon-validation-validation-notnull] - must not be null
- [`io.helidon.validation.Validation.Null`][io-helidon-validation-validation-null] - must be null

Constraints for `String` and `CharSequence`:

- [`io.helidon.validation.Validation.Email`][io-helidon-validation-validation-email] - matches an e-mail structure (basic check only)
- [`io.helidon.validation.Validation.String.NotBlank`][io-helidon-validation-validation-string-notblank] - must not be blank (empty or only white-space characters)
- [`io.helidon.validation.Validation.String.NotEmpty`][io-helidon-validation-validation-string-notempty] - must not be empty (i.e. length is `0`)
- [`io.helidon.validation.Validation.String.Length`][io-helidon-validation-validation-string-length] - check for maximal (and optionally minimal) length
- [`io.helidon.validation.Validation.String.Pattern`][io-helidon-validation-validation-string-pattern] - check against a regular expression

Constraints for types that extend `java.lang.Number`. These constraints accept any such type, though all types are eventually converted to a `BigDecimal` and the checks are done against the result. `Byte` is always converted as an unsigned number, i.e. its values are from `0` to `255` inclusive.

- [`io.helidon.validation.Validation.Number.Negative`][io-helidon-validation-validation-number-negative] - the value must be negative (`< 0`)
- [`io.helidon.validation.Validation.Number.NegativeOrZero`][io-helidon-validation-validation-number-negativeorzero] - the value must be negative or zero (`<= 0`)
- [`io.helidon.validation.Validation.Number.Positive`][io-helidon-validation-validation-number-positive] - the value must be positive (`> 0`)
- [`io.helidon.validation.Validation.Number.PositiveOrZero`][io-helidon-validation-validation-number-positiveorzero] - the value must be positive or zero (`>= 0`)
- [`io.helidon.validation.Validation.Number.Min`][io-helidon-validation-validation-number-min] - the value must be at least the specified minimal value (`>= min`), value is defined as a `String`
- [`io.helidon.validation.Validation.Number.Max`][io-helidon-validation-validation-number-max] - the value must be at most the specified maximal value (`<= max`), value is defined as a `String`
- [`io.helidon.validation.Validation.Number.Digits`][io-helidon-validation-validation-number-digits] - the number must have at most the specified number of integer and fractional digits
- [`io.helidon.validation.Validation.Number.MultipleOf`][io-helidon-validation-validation-number-multipleof] - the value must be evenly divisible by the specified positive factor, which is defined as a `String`

Constraints for `Integer` data types. These constraints accept `int, long, byte, char, short` and their boxed counterparts. `byte` is always converted as an unsigned number, i.e. its values are from `0` to `255` inclusive. These are convenience annotation that use `int` data type:

- [`io.helidon.validation.Validation.Integer.Min`][io-helidon-validation-validation-integer-min] - the value must be at least the specified minimal value (`>= min`)
- [`io.helidon.validation.Validation.Integer.Max`][io-helidon-validation-validation-integer-max] - the value must be at most the specified maximal value (`<= max`)
- [`io.helidon.validation.Validation.Integer.MultipleOf`][io-helidon-validation-validation-integer-multipleof] - the value must be evenly divisible by the specified positive integer factor

Constraints for `Long` and `long` data types. No other type is supported:

- [`io.helidon.validation.Validation.Long.Min`][io-helidon-validation-validation-long-min] - the value must be at least the specified minimal value (`>= min`)
- [`io.helidon.validation.Validation.Long.Max`][io-helidon-validation-validation-long-max] - the value must be at most the specified maximal value (`<= max`)
- [`io.helidon.validation.Validation.Long.MultipleOf`][io-helidon-validation-validation-long-multipleof] - the value must be evenly divisible by the specified positive long factor

Constraints for `Boolean` and `boolean` data type. No other type is supported:

- [`io.helidon.validation.Validation.Boolean.True`][io-helidon-validation-validation-boolean-true] - the value must be `true`
- [`io.helidon.validation.Validation.Boolean.False`][io-helidon-validation-validation-boolean-false] - the value must be `false`

Constraints for collection and map data types:

- [`io.helidon.validation.Validation.Collection.Size`][io-helidon-validation-validation-collection-size] - the size of the collection or map must be between the minimal and maximal sizes

Constraints for calendar/time data types. Behavior depends on the specific type - for example for `Year` data type, past is previous year, future is the next year, and present is the current year, regardless of which month it is. When using `Instant`, past is already the last millisecond.

- [`io.helidon.validation.Validation.Calendar.Future`][io-helidon-validation-validation-calendar-future] - the value must be in the future
- [`io.helidon.validation.Validation.Calendar.FutureOrPresent`][io-helidon-validation-validation-calendar-futureorpresent] - the value must be in the future or now
- [`io.helidon.validation.Validation.Calendar.Past`][io-helidon-validation-validation-calendar-past] - the value must be in the past
- [`io.helidon.validation.Validation.Calendar.PastOrPresent`][io-helidon-validation-validation-calendar-pastorpresent] - the value must be in the past or now

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

A type annotated with `@Validation.Validated` will have validation code generated. Usage of that type can be marked with `@Validation.Valid` - if such an annotation is present, and it is on a field of another validated type, or it is a parameter, return type, or a type argument of a parameter/return type of a service method, the object instance will be validated using a generated interceptor. Type-use validation is supported on nested `Optional`, `Collection`, `List`, `Set`, `Map` key/value types, array component types, and wildcard bounds.

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

Constraint annotations and `@Validation.Valid` can also be declared on non-private instance methods of service interfaces. They are applied when the service instance is obtained from the service registry. The same applies to instances returned by registry-managed service factories, including `Supplier`, `Service.ServicesFactory`, `Service.InjectionPointFactory`, and `Service.QualifiedFactory` services. This can change runtime behavior for services that already declared interface validation: invalid calls through registry-obtained services may now fail with a `ValidationException`, while directly constructed objects are not intercepted.

Example of a validated service contract

```java
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

A custom constraint annotation can be created (and act as a compound annotation as well).

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

Identity propagation (when using a WebClient) depends on configuration of the client and configuration of security. We currently do not have a declarative way of modifying client behavior.

Supported annotations:

- `io.helidon.security.annotations.Authenticated` - mark an endpoint or a method as requiring authentication
- `io.helidon.security.annotations.Authorized` - mark an endpoint or a method as requiring authorization
- `io.helidon.security.annotations.Audited` - mark an endpoint or a method as requiring audit logging
- `io.helidon.security.abac.role.RoleValidator.PermitAll` - annotated method does not require any authentication or authorization (even if endpoint does)
- `jakarta.annotation.security.PermitAll` - same as `RoleValidator.PermitAll`
- `jakarta.annotation.security.DenyAll` - annotated method will not be callable with any kind of authentication or authorization
- [`io.helidon.security.abac.role.RoleValidator.Roles`][io-helidon-security-abac-role-rolevalidator-roles] - provide a set of roles that can access a resource, implies authentication is required
- `jakarta.annotation.security.RolesAllowed` - same as above (`RoleValidator.Roles`)

### Metrics

Add support for the following meters:

- Counter
- Timer
- Gauge

Method annotations:

- [`io.helidon.metrics.api.Metrics.Counted`][io-helidon-metrics-api-metrics-counted] - adds a counter metric to the metric registry for method executions
- [`io.helidon.metrics.api.Metrics.Timed`][io-helidon-metrics-api-metrics-timed] - adds a timer metric to the metric registry for method executions
- [`io.helidon.metrics.api.Metrics.Gauge`][io-helidon-metrics-api-metrics-gauge] - marks a method that returns a number as a gauge

In addition, we can use [`io.helidon.metrics.api.Metrics.Tag`][io-helidon-metrics-api-metrics-tag] annotation on a type, method, or as a `tags` property of an annotation to add tags to the metric. Tags from type definition will be added to all metrics on the type, tags on methods on all metrics on the method, and tags in the metric annotation will only be used by that metric.

The example below shows additional tags. The counter on method `counted` will have the following tags: `service=Metered;method=counted` (and of course the scope tag that is always added).

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

A gauge is a method that returns a `Number`, and is invoked by the metrics implementation to obtain a value. Example below shows a definition of a `Gauge`. Note that a `unit` is mandatory for gauges.

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

Add support for tracing of methods. This feature will add a new span for each annotated method (or all methods on an annotated type).

Annotations:

- [`io.helidon.tracing.Tracing.Traced`][io-helidon-tracing-tracing-traced] - all methods on the annotated type are will be traced, or the annotated method will be traced
- [`io.helidon.tracing.Tracing.ParamTag`][io-helidon-tracing-tracing-paramtag] - the annotated method parameter will be added as a tag to the span

Notes on defaults:

- if a `kind` is defined to other value than `INTERNAL`, it will be used unless a `kind` other than `INTERNAL` is defined on a method annotation (i.e. it is not possible to have `SERVER` on type, and `INTERNAL` on method)
- span name defaults to `fully-qualified-class-name.method-name`

The following example shows annotation on a type. This would make all methods traced with span kind of `SERVER`, and with a tag `service` with value `TracedService`.

Example of traced type

```java
@Service.Singleton
@Tracing.Traced(tags = @Tracing.Tag(key = "service", value = "TracedService"),
                kind = Span.Kind.SERVER)
static class TracedService {
```

A traced method with an explicit span name, adding a tag with a constant value, and a tag with a value from annotated parameter. The tag name defaults to parameter name (`userAgent` in this case).

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

To create a WebSocket endpoint, simply annotate a class with `@WebSocketServer.Endpoint`, and add at least one method annotated with one of the WebSocket method annotations, such as `@WebSocket.OnMessage`.

Services available for injection:

N/A

Supported method parameters (no annotation required):

- [`io.helidon.websocket.WsSession`][io-helidon-websocket-wssession]
- `boolean` in a method annotated with `@WebSocket.OnMessage` - indicator of "last" message (if not present, the message will be combined before delivery)
- `java.lang.String` (`@WebSocket.OnMessage`) - the message delivered (text)
- `java.io.Reader` (`@WebSocket.OnMessage`) - the message delivered (text)
- [`io.helidon.common.buffers.BufferData`][io-helidon-common-buffers-bufferdata] (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.nio.ByteBuffer` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.io.InputStream` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `io.helidon.http.HttpPrologue` (\`@WebSocket.OnHttpUpgrade) - the HTTP prologue (method, path, protocol version)
- `io.helidon.http.Headers` (\`@WebSocket.OnHttpUpgrade) - the request headers
- `int` (`@WebSocket.OnClose`) - the close code
- `java.lang.String` (`@WebSocket.OnClose`) - the close reason
- `java.lang.Throwable` (`@WebSocket.OnError`) - the throwable thrown

Annotations on endpoint type:

- [`io.helidon.webserver.websocket.WebSocketServer.Endpoint`][io-helidon-webserver-websocket-websocketserver-endpoint] - required annotation
- [`io.helidon.webserver.websocket.WebSocketServer.Listener`][io-helidon-webserver-websocket-websocketserver-listener] - to define the named listener this should be served on (named port/socket)
- [`io.helidon.http.Http.Path`][io-helidon-http-http-path] - path (context) this endpoint will be available on

Annotations on endpoint methods:

- [`io.helidon.websocket.WebSocket.OnMessage`][io-helidon-websocket-websocket-onmessage] - receives either a binary or a text message
- [`io.helidon.websocket.WebSocket.OnHttpUpgrade`][io-helidon-websocket-websocket-onhttpupgrade] - invoked during HTTP upgrade, the method may return `Headers` to be sent during the upgrade response
- [`io.helidon.websocket.WebSocket.OnOpen`][io-helidon-websocket-websocket-onopen] - invoked when the WebSocket connection is established (after upgrade)
- [`io.helidon.websocket.WebSocket.OnClose`][io-helidon-websocket-websocket-onclose] - invoked when the WebSocket connection is closed
- [`io.helidon.websocket.WebSocket.OnError`][io-helidon-websocket-websocket-onerror] - invoked when an error occurs when invoking other methods

Annotations on method parameters:

- [`io.helidon.http.Http.PathParam`][io-helidon-http-http-pathparam] - Typed parameter from path template

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

To create a WebSocket client endpoint, simply annotate a class with `@WebSocketClient.Endpoint`, and add at least one method annotated with one of the WebSocket method annotations, such as `@WebSocket.OnMessage`.

Services available for injection:

- a factory for the endpoint (generated), if endpoint is named `EchoEndpoint`, an `EchoEndpointFactory` will be generated with methods to connect to remote server

Supported method parameters (no annotation required):

- [`io.helidon.websocket.WsSession`][io-helidon-websocket-wssession]
- `boolean` in a method annotated with `@WebSocket.OnMessage` - indicator of "last" message (if not present, the message will be combined before delivery)
- `java.lang.String` (`@WebSocket.OnMessage`) - the message delivered (text)
- `java.io.Reader` (`@WebSocket.OnMessage`) - the message delivered (text)
- [`io.helidon.common.buffers.BufferData`][io-helidon-common-buffers-bufferdata] (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.nio.ByteBuffer` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.io.InputStream` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `int` (`@WebSocket.OnClose`) - the close code
- `java.lang.String` (`@WebSocket.OnClose`) - the close reason
- `java.lang.Throwable` (`@WebSocket.OnError`) - the throwable thrown

Annotations on endpoint type:

- [`io.helidon.webclient.websocket.WebSocketClient`][io-helidon-webclient-websocket-websocketclient] - required annotation
- [`io.helidon.http.Http.Path`][io-helidon-http-http-path] - path (context) this endpoint will be available on

Annotations on endpoint methods:

- [`io.helidon.websocket.WebSocket.OnMessage`][io-helidon-websocket-websocket-onmessage] - receives either a binary or a text message
- [`io.helidon.websocket.WebSocket.OnHttpUpgrade`][io-helidon-websocket-websocket-onhttpupgrade] - invoked during HTTP upgrade, the method may return `Headers` to be sent during the upgrade response
- [`io.helidon.websocket.WebSocket.OnOpen`][io-helidon-websocket-websocket-onopen] - invoked when the WebSocket connection is established (after upgrade)
- [`io.helidon.websocket.WebSocket.OnClose`][io-helidon-websocket-websocket-onclose] - invoked when the WebSocket connection is closed
- [`io.helidon.websocket.WebSocket.OnError`][io-helidon-websocket-websocket-onerror] - invoked when an error occurs when invoking other methods

Annotations on method parameters:

- [`io.helidon.http.Http.PathParam`][io-helidon-http-http-pathparam] - Typed parameter from path template defined by `@Http.Path` on the class

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

### WebServer CORS

CORS can be configured through Helidon Config, the root key is `cors`.

To add an explicit CORS (Cross-origin resource sharing) configuration to an endpoint method, you may annotate it with one of the annotations in the `Cors` class, such as `@Cors.Defaults`.

Annotations on endpoint method (must be an `OPTIONS` method):

- [`io.helidon.webserver.cors.Cors.Defaults`][io-helidon-webserver-cors-cors-defaults] - support all methods, all origins, do not combine with other annotations from `Cors` class
- [`io.helidon.webserver.cors.Cors.AllowOrigins`][io-helidon-webserver-cors-cors-alloworigins] - configure allowed origins, either as a exact string, or regular expression (if the value contains `\`, `*` or `{`, it is considered a regular expression)
- [`io.helidon.webserver.cors.Cors.AllowMethods`][io-helidon-webserver-cors-cors-allowmethods] - set of allowed methods that the different origin script can use
- [`io.helidon.webserver.cors.Cors.AllowHeaders`][io-helidon-webserver-cors-cors-allowheaders] - set of allowed headers sent from the different origin script
- [`io.helidon.webserver.cors.Cors.ExposeHeaders`][io-helidon-webserver-cors-cors-exposeheaders] - set of headers from response exposed to the different origin script
- [`io.helidon.webserver.cors.Cors.AllowCredentials`][io-helidon-webserver-cors-cors-allowcredentials] - whether to add credentials (such as Cookie) to requests from the different origin script
- [`io.helidon.webserver.cors.Cors.MaxAgeSeconds`][io-helidon-webserver-cors-cors-maxageseconds] - maximal number of seconds the pre-flight is considered valid

Example of a CORS protected endpoint

```java
@Service.Singleton
@Http.Path("/cors")
static class CorsEndpoint {
    @Http.OPTIONS
    @Cors.AllowOrigins("${app.cors.allow-origins:http://foos.bar,http://bars.foo}") // (1)
    @Cors.AllowHeaders({"X-foo", "X-bar"}) // (2)
    @Cors.AllowMethods({Method.DELETE_NAME, Method.PUT_NAME, "LIST"}) // (3)
    @Cors.MaxAgeSeconds(180) // (4)
    void options() {
    }
}
```

1.  Configure origins that can be overridden using config key `app.cors.allow-origins` with the provided default values (comma separated)
2.  Configure headers the script can send to this host
3.  Configure allowed methods for CORS requests
4.  Configure max age to be 3 minutes

### Health Checks

To add a declarative health check, create a service that implements io.helidon.health.HealthCheck or produces an instance of it. The WebServer health observer discovers all such services and uses them to contribute to the health response. Because the lookup is performed only once, you must not use the @Service.PerRequest scope. The recommended scope is @Service.Singleton.

[features]: #features
[injection-startup]: injection.md#startup
[io-helidon-config-config]: /apidocs/io.helidon.config/io/helidon/config/Config.html
[io-helidon-config-configuration-value]: /apidocs/io.helidon.config/io/helidon/config/Configuration.html
[io-helidon-common-default]: /apidocs/io.helidon.common/io/helidon/common/Default.html
[io-helidon-webserver-http-serverrequest]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerRequest.html
[io-helidon-webserver-http-serverresponse]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerResponse.html
[io-helidon-common-context-context]: /apidocs/io.helidon.common.context/io/helidon/common/context/Context.html
[io-helidon-webserver-http-restserver-endpoint]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Endpoint.html
[io-helidon-webserver-http-restserver-listener]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Listener.html
[io-helidon-webserver-http-restserver-header]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Header.html
[io-helidon-webserver-http-restserver-computedheader]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.ComputedHeader.html
[io-helidon-http-http-path]: /apidocs/io.helidon.http/io/helidon/http/Http.Path.html
[io-helidon-webserver-http-restserver-status]: /apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Status.html
[io-helidon-http-http-get]: /apidocs/io.helidon.http/io/helidon/http/Http.GET.html
[io-helidon-http-http-httpmethod]: /apidocs/io.helidon.http/io/helidon/http/Http.HttpMethod.html
[io-helidon-http-http-produces]: /apidocs/io.helidon.http/io/helidon/http/Http.Produces.html
[io-helidon-http-http-consumes]: /apidocs/io.helidon.http/io/helidon/http/Http.Consumes.html
[io-helidon-http-http-entity]: /apidocs/io.helidon.http/io/helidon/http/Http.Entity.html
[io-helidon-http-http-headerparam]: /apidocs/io.helidon.http/io/helidon/http/Http.HeaderParam.html
[io-helidon-http-http-queryparam]: /apidocs/io.helidon.http/io/helidon/http/Http.QueryParam.html
[io-helidon-http-http-pathparam]: /apidocs/io.helidon.http/io/helidon/http/Http.PathParam.html
[io-helidon-webclient-api-restclient-endpoint]: /apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Endpoint.html
[io-helidon-webclient-api-restclient-header]: /apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Header.html
[io-helidon-webclient-api-restclient-computedheader]: /apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.ComputedHeader.html
[io-helidon-faulttolerance-ft-retry]: /apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Retry.html
[io-helidon-faulttolerance-ft-fallback]: /apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Fallback.html
[io-helidon-faulttolerance-ft-async]: /apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Async.html
[io-helidon-faulttolerance-ft-timeout]: /apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Timeout.html
[io-helidon-faulttolerance-ft-bulkhead]: /apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Bulkhead.html
[io-helidon-faulttolerance-ft-circuitbreaker]: /apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.CircuitBreaker.html
[io-helidon-scheduling-scheduling-cron]: /apidocs/io.helidon.scheduling/io/helidon/scheduling/Scheduling.Cron.html
[io-helidon-scheduling-scheduling-fixedrate]: /apidocs/io.helidon.scheduling/io/helidon/scheduling/Scheduling.FixedRate.html
[io-helidon-validation-validation-notnull]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.NotNull.html
[io-helidon-validation-validation-null]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Null.html
[io-helidon-validation-validation-email]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Email.html
[io-helidon-validation-validation-string-notblank]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.String.NotBlank.html
[io-helidon-validation-validation-string-notempty]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.String.NotEmpty.html
[io-helidon-validation-validation-string-length]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Length.html
[io-helidon-validation-validation-string-pattern]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Pattern.html
[io-helidon-validation-validation-number-negative]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Negative.html
[io-helidon-validation-validation-number-negativeorzero]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.NegativeOrZero.html
[io-helidon-validation-validation-number-positive]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Positive.html
[io-helidon-validation-validation-number-positiveorzero]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.PositiveOrZero.html
[io-helidon-validation-validation-number-min]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Min.html
[io-helidon-validation-validation-number-max]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Max.html
[io-helidon-validation-validation-number-digits]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Digits.html
[io-helidon-validation-validation-number-multipleof]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.MultipleOf.html
[io-helidon-validation-validation-integer-min]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.Min.html
[io-helidon-validation-validation-integer-max]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.Max.html
[io-helidon-validation-validation-integer-multipleof]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.MultipleOf.html
[io-helidon-validation-validation-long-min]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.Min.html
[io-helidon-validation-validation-long-max]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.Max.html
[io-helidon-validation-validation-long-multipleof]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.MultipleOf.html
[io-helidon-validation-validation-boolean-true]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Boolean.True.html
[io-helidon-validation-validation-boolean-false]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Boolean.False.html
[io-helidon-validation-validation-collection-size]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Collection.Size.html
[io-helidon-validation-validation-calendar-future]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.Future.html
[io-helidon-validation-validation-calendar-futureorpresent]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.FutureOrPresent.html
[io-helidon-validation-validation-calendar-past]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.Past.html
[io-helidon-validation-validation-calendar-pastorpresent]: /apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.PastOrPresent.html
[io-helidon-security-abac-role-rolevalidator-roles]: /apidocs/io.helidon.security/io/helidon/security/abac/role/RoleValidator.Roles.html
[io-helidon-metrics-api-metrics-counted]: /apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Counted.html
[io-helidon-metrics-api-metrics-timed]: /apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Timed.html
[io-helidon-metrics-api-metrics-gauge]: /apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Gauge.html
[io-helidon-metrics-api-metrics-tag]: /apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Tag.html
[io-helidon-tracing-tracing-traced]: %7Btracing-javadoc%7D/io/helidon/tracing/Tracing.Traced.md
[io-helidon-tracing-tracing-paramtag]: %7Btracing-javadoc%7D/io/helidon/tracing/Tracing.ParamTag.md
[io-helidon-websocket-wssession]: /apidocs/io.helidon.websocket/io/helidon/websocket/WsSession.html
[io-helidon-common-buffers-bufferdata]: /apidocs/io.helidon.common.buffers/io/helidon/common/buffers/BufferData.html
[io-helidon-webserver-websocket-websocketserver-endpoint]: /apidocs/io.helidon.webserver.websocket/io/helidon/webserver/websocket/WebSocketServer.Endpoint.html
[io-helidon-webserver-websocket-websocketserver-listener]: /apidocs/io.helidon.webserver.websocket/io/helidon/webserver/websocket/WebSocketServer.Listener.html
[io-helidon-websocket-websocket-onmessage]: /apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnMessage.html
[io-helidon-websocket-websocket-onhttpupgrade]: /apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnHttpUpgrade.html
[io-helidon-websocket-websocket-onopen]: /apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnOpen.html
[io-helidon-websocket-websocket-onclose]: /apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnClose.html
[io-helidon-websocket-websocket-onerror]: /apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnError.html
[io-helidon-webclient-websocket-websocketclient]: /apidocs/io.helidon.webclient.websocket/io/helidon/webclient/websocket/WebSocketClient.Endpoint.html
[io-helidon-webserver-cors-cors-defaults]: %7Bws-cors-javadoc%7D/Cors.Defaults.md
[io-helidon-webserver-cors-cors-alloworigins]: %7Bws-cors-javadoc%7D/Cors.AllowOrigins.md
[io-helidon-webserver-cors-cors-allowmethods]: %7Bws-cors-javadoc%7D/Cors.AllowMethods.md
[io-helidon-webserver-cors-cors-allowheaders]: %7Bws-cors-javadoc%7D/Cors.AllowHeaders.md
[io-helidon-webserver-cors-cors-exposeheaders]: %7Bws-cors-javadoc%7D/Cors.ExposeHeaders.md
[io-helidon-webserver-cors-cors-allowcredentials]: %7Bws-cors-javadoc%7D/Cors.AllowCredentials.md
[io-helidon-webserver-cors-cors-maxageseconds]: %7Bws-cors-javadoc%7D/Cors.MaxAgeSeconds.md
