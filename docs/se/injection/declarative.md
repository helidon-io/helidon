# Declarative

## Overview

Helidon declarative programming model allows inversion of control style programming with all the performance benefits of Helidon SE.

Our declarative approach has the following advantages:

- Uses Helidon SE imperative code to implement features (i.e. performance is same as "pure" imperative application)
- Generates all the necessary code at build-time, to avoid reflection and bytecode manipulation at runtime
- It is based on [Helidon Injection](injection.md#Overview)
- Declarative features are in the same modules as Helidon SE features (i.e. does not require additional dependencies)

> [!NOTE]
> Helidon Declarative is an incubating feature. The APIs shown here are subject to change. These APIs will be finalized in a future release of Helidon.

## Usage

To create a declarative application, use the annotations provided in our Helidon SE modules (details under [Features](#features)), and the maven plugin described in [Injection: Startup](injection.md#generate-binding) to generate the binding.

In addition, the following section must be added to the `build` of the Maven `pom.xml` to enable annotation processors that generate the necessary code:

```xml
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

- [`io.helidon.config.Config`](/apidocs/io.helidon.config/io/helidon/config/Config.html)

Annotations:

- [`io.helidon.config.Configuration.Value`](/apidocs/io.helidon.config/io/helidon/config/Configuration.html) - define the configuration key to inject, on constructor parameter
- Annotations defined in [`io.helidon.common.Default`](/apidocs/io.helidon.common/io/helidon/common/Default.html) - define a default typed value, on the same constructor parameter

Example of usage can be seen below in HTTP Server Endpoint example.

### HTTP Server Endpoint

To create an HTTP endpoint, simply annotate a class with `@RestServer.Endpoint`, and add at least one method annotated with one of the HTTP method annotations, such as `@Http.GET`.

Services available for injection:

N/A

Supported method parameters (no annotation required):

- [`io.helidon.webserver.http.ServerRequest`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerRequest.html)
- [`io.helidon.webserver.http.ServerResponse`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerResponse.html)
- [`io.helidon.common.context.Context`](/apidocs/io.helidon.common.context/io/helidon/common/context/Context.html)
- `io.helidon.common.security.SecurityContext`
- `` io.helidon.security.SecurityContext - in case `helidon-security `` module is on the classpath

Annotations on endpoint type:

- [`io.helidon.webserver.http.RestServer.Endpoint`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Endpoint.html) - required annotation
- [`io.helidon.webserver.http.RestServer.Listener`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Listener.html) - to define the named listener this should be served on (named port/socket)
- [`io.helidon.webserver.http.RestServer.Header`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Header.html) - header to return with each response from this endpoint
- [`io.helidon.webserver.http.RestServer.ComputedHeader`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.ComputedHeader.html) - computed header to return with each response from this endpoint
- [`io.helidon.http.Http.Path`](/apidocs/io.helidon.http/io/helidon/http/Http.Path.html) - path (context) this endpoint will be available on

Annotations on endpoint methods:

- [`io.helidon.webserver.http.RestServer.Header`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Header.html) - header to return with each response from this method
- [`io.helidon.webserver.http.RestServer.ComputedHeader`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.ComputedHeader.html) - computed header to return with each response from this method
- [`io.helidon.webserver.http.RestServer.Status`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Status.html) - status to return (if a custom one is required)
- [`io.helidon.http.Http.Path`](/apidocs/io.helidon.http/io/helidon/http/Http.Path.html) - path (context) this method will be available on (subpath of the endpoint path)
- [`io.helidon.http.Http.GET`](/apidocs/io.helidon.http/io/helidon/http/Http.GET.html) (and other methods) - definition of HTTP method this method will serve
- [`io.helidon.http.Http.HttpMethod`](/apidocs/io.helidon.http/io/helidon/http/Http.HttpMethod.html) - for custom HTTP method names (mutually exclusive with above)
- [`io.helidon.http.Http.Produces`](/apidocs/io.helidon.http/io/helidon/http/Http.Produces.html) - what media type this method produces (return entity content type)
- [`io.helidon.http.Http.Consumes`](/apidocs/io.helidon.http/io/helidon/http/Http.Consumes.html) - what media type this method accepts (request entity content type)

Annotations on method parameters:

- [`io.helidon.http.Http.Entity`](/apidocs/io.helidon.http/io/helidon/http/Http.Entity.html) - Request entity, a typed parameter is expected, will use HTTP media type modules to coerce into the correct type
- [`io.helidon.http.Http.HeaderParam`](/apidocs/io.helidon.http/io/helidon/http/Http.HeaderParam.html) - Typed HTTP request header value
- [`io.helidon.http.Http.QueryParam`](/apidocs/io.helidon.http/io/helidon/http/Http.QueryParam.html) - Typed HTTP query value
- [`io.helidon.http.Http.PathParam`](/apidocs/io.helidon.http/io/helidon/http/Http.PathParam.html) - Typed parameter from path template

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

- [`io.helidon.webclient.api.RestClient.Endpoint`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Endpoint.html) - required annotation
- [`io.helidon.http.Http.Path`](/apidocs/io.helidon.http/io/helidon/http/Http.Path.html) - path (context) the server listens on
- [`io.helidon.webclient.api.RestClient.Header`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Header.html) - header to include in every request to the server
- [`io.helidon.webclient.api.RestClient.ComputedHeader`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.ComputedHeader.html) - header to compute and include in every request to the server

Annotations on endpoint methods:

- [`io.helidon.webclient.api.RestClient.Header`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.Header.html) - header to include in every request to the server
- [`io.helidon.webclient.api.RestClient.ComputedHeader`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/RestClient.ComputedHeader.html) - header to compute and include in every request to the server
- [`io.helidon.http.Http.Path`](/apidocs/io.helidon.http/io/helidon/http/Http.Path.html) - path (context) the server serves this endpoint method on
- [`io.helidon.http.Http.GET`](/apidocs/io.helidon.http/io/helidon/http/Http.GET.html) (and other methods) - definition of HTTP method this method will invoke
- [`io.helidon.http.Http.HttpMethod`](/apidocs/io.helidon.http/io/helidon/http/Http.HttpMethod.html) - for custom HTTP method names (mutually exclusive with above)
- [`io.helidon.http.Http.Produces`](/apidocs/io.helidon.http/io/helidon/http/Http.Produces.html) - what media type this method produces (content type of entity from the server)
- [`io.helidon.http.Http.Consumes`](/apidocs/io.helidon.http/io/helidon/http/Http.Consumes.html) - what media type this method accepts (request entity content type)

Annotations on method parameters:

- [`io.helidon.http.Http.Entity`](/apidocs/io.helidon.http/io/helidon/http/Http.Entity.html) - Request entity, a typed parameter is expected, will use HTTP media type modules to write to the request
- [`io.helidon.http.Http.HeaderParam`](/apidocs/io.helidon.http/io/helidon/http/Http.HeaderParam.html) - Typed HTTP header value to send
- [`io.helidon.http.Http.QueryParam`](/apidocs/io.helidon.http/io/helidon/http/Http.QueryParam.html) - Typed HTTP query value to send
- [`io.helidon.http.Http.PathParam`](/apidocs/io.helidon.http/io/helidon/http/Http.PathParam.html) - Typed parameter from path template to construct the request URI

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

- [`io.helidon.faulttolerance.Ft.Retry`](/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Retry.html) - allow retries
- [`io.helidon.faulttolerance.Ft.Fallback`](/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Fallback.html) - fallback to another method that provides
- [`io.helidon.faulttolerance.Ft.Async`](/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Async.html) - invoke method asynchronously
- [`io.helidon.faulttolerance.Ft.Timeout`](/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Timeout.html) - invoke method with a timeout
- [`io.helidon.faulttolerance.Ft.Bulkhead`](/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.Bulkhead.html) - use bulkhead
- [`io.helidon.faulttolerance.Ft.CircuitBreaker`](/apidocs/io.helidon.faulttolerance/io/helidon/faulttolerance/Ft.CircuitBreaker.html) - use circuit breaker

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

- [`io.helidon.scheduling.Scheduling.Cron`](/apidocs/io.helidon.scheduling/io/helidon/scheduling/Scheduling.Cron.html) - execute with schedule defined by a CRON expression
- [`io.helidon.scheduling.Scheduling.FixedRate`](/apidocs/io.helidon.scheduling/io/helidon/scheduling/Scheduling.FixedRate.html) - execute with a fixed interval

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

```xml
<dependency>
    <groupId>io.helidon.validation</groupId>
    <artifactId>helidon-validation</artifactId>
</dependency>
```

#### Constraint Annotations

A "Constraint Annotation" is any annotation directly annotated with `io.helidon.validation.Validation`. Helidon Validation provides a set of built-in validation constraints, though custom constraints can be created, or existing constraints can be combined.

Existing constraints:

Constraints for any type:

- [`io.helidon.validation.Validation.NotNull`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.NotNull.html) - must not be null
- [`io.helidon.validation.Validation.Null`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Null.html) - must be null

Constraints for `String` and `CharSequence`:

- [`io.helidon.validation.Validation.Email`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Email.html) - matches an e-mail structure (basic check only)
- [`io.helidon.validation.Validation.String.NotBlank`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.NotBlank.html) - must not be blank (empty or only white-space characters)
- [`io.helidon.validation.Validation.String.NotEmpty`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.NotEmpty.html) - must not be empty (i.e. length is `0`)
- [`io.helidon.validation.Validation.String.Length`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Length.html) - check for maximal (and optionally minimal) length
- [`io.helidon.validation.Validation.String.Pattern`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.String.Pattern.html) - check against a regular expression

Constraints for types that extend `java.lang.Number`. These constraints accept any such type, though all types are eventually converted to a `BigDecimal` and the checks are done against the result. `Byte` is always converted as an unsigned number, i.e. its values are from `0` to `255` inclusive.

- [`io.helidon.validation.Validation.Number.Negative`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Negative.html) - the value must be negative (`< 0`)
- [`io.helidon.validation.Validation.Number.NegativeOrZero`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.NegativeOrZero.html) - the value must be negative or zero (`<= 0`)
- [`io.helidon.validation.Validation.Number.Positive`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Positive.html) - the value must be positive (`> 0`)
- [`io.helidon.validation.Validation.Number.PositiveOrZero`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.PositiveOrZero.html) - the value must be positive or zero (`>= 0`)
- [`io.helidon.validation.Validation.Number.Min`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Min.html) - the value must be at least the specified minimal value (`>= min`), value is defined as a `String`
- [`io.helidon.validation.Validation.Number.Max`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Max.html) - the value must be at most the specified maximal value (`<= max`), value is defined as a `String`
- [`io.helidon.validation.Validation.Number.Digits`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Number.Digits.html) - the number must have at most the specified number of integer and fractional digits

Constraints for `Integer` data types. These constraints accept `int, long, byte, char, short` and their boxed counterparts. `byte` is always converted as an unsigned number, i.e. its values are from `0` to `255` inclusive. These are convenience annotation that use `int` data type:

- [`io.helidon.validation.Validation.Integer.Min`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.Min.html) - the value must be at least the specified minimal value (`>= min`)
- [`io.helidon.validation.Validation.Integer.Max`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Integer.Max.html) - the value must be at most the specified maximal value (`<= max`)

Constraints for `Long` and `long` data types. No other type is supported:

- [`io.helidon.validation.Validation.Long.Min`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.Min.html) - the value must be at least the specified minimal value (`>= min`)
- [`io.helidon.validation.Validation.Long.Max`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Long.Max.html) - the value must be at most the specified maximal value (`<= max`)

Constraints for `Boolean` and `boolean` data type. No other type is supported:

- [`io.helidon.validation.Validation.Boolean.True`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Boolean.True.html) - the value must be `true`
- [`io.helidon.validation.Validation.Boolean.False`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Boolean.False.html) - the value must be `false`

Constraints for collection and map data types:

- [`io.helidon.validation.Validation.Collection.Size`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Collection.Size.html) - the size of the collection or map must be between the minimal and maximal sizes

Constraints for calendar/time data types. Behavior depends on the specific type - for example for `Year` data type, past is previous year, future is the next year, and present is the current year, regardless of which month it is. When using `Instant`, past is already the last millisecond.

- [`io.helidon.validation.Validation.Calendar.Future`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.Future.html) - the value must be in the future
- [`io.helidon.validation.Validation.Calendar.FutureOrPresent`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.FutureOrPresent.html) - the value must be in the future or now
- [`io.helidon.validation.Validation.Calendar.Past`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.Past.html) - the value must be in the past
- [`io.helidon.validation.Validation.Calendar.PastOrPresent`](/apidocs/io.helidon.validation/io/helidon/validation/Validation.Calendar.PastOrPresent.html) - the value must be in the past or now

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

A type annotated with `@Validation.Validated` will have validation code generated. Usage of that type can be marked with `@Validation.Valid` - if such an annotation is present, and it is on a field of another validated type, or it is a parameter, return type, or a type argument of a parameter/return type of a service method, the object instance will be validated using a generated interceptor.

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
- [`io.helidon.security.abac.role.RoleValidator.Roles`](/apidocs/io.helidon.security/io/helidon/security/abac/role/RoleValidator.Roles.html) - provide a set of roles that can access a resource, implies authentication is required
- `jakarta.annotation.security.RolesAllowed` - same as above (`RoleValidator.Roles`)

### Metrics

Add support for the following meters:

- Counter
- Timer
- Gauge

Method annotations:

- [`io.helidon.metrics.api.Metrics.Counted`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Counted.html) - adds a counter metric to the metric registry for method executions
- [`io.helidon.metrics.api.Metrics.Timed`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Timed.html) - adds a timer metric to the metric registry for method executions
- [`io.helidon.metrics.api.Metrics.Gauge`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Gauge.html) - marks a method that returns a number as a gauge

In addition, we can use [`io.helidon.metrics.api.Metrics.Tag`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.Tag.html) annotation on a type, method, or as a `tags` property of an annotation to add tags to the metric. Tags from type definition will be added to all metrics on the type, tags on methods on all metrics on the method, and tags in the metric annotation will only be used by that metric.

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

- [`io.helidon.tracing.Tracing.Traced`](%7Btracing-javadoc%7D/io/helidon/tracing/Tracing.Traced.md) - all methods on the annotated type are will be traced, or the annotated method will be traced
- [`io.helidon.tracing.Tracing.ParamTag`](%7Btracing-javadoc%7D/io/helidon/tracing/Tracing.ParamTag.md) - the annotated method parameter will be added as a tag to the span

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

- [`io.helidon.websocket.WsSession`](/apidocs/io.helidon.websocket/io/helidon/websocket/WsSession.html)
- `boolean` in a method annotated with `@WebSocket.OnMessage` - indicator of "last" message (if not present, the message will be combined before delivery)
- `java.lang.String` (`@WebSocket.OnMessage`) - the message delivered (text)
- `java.io.Reader` (`@WebSocket.OnMessage`) - the message delivered (text)
- [`io.helidon.common.buffers.BufferData`](/apidocs/io.helidon.common.buffers/io/helidon/common/buffers/BufferData.html) (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.nio.ByteBuffer` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.io.InputStream` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `io.helidon.http.HttpPrologue` (\`@WebSocket.OnHttpUpgrade) - the HTTP prologue (method, path, protocol version)
- `io.helidon.http.Headers` (\`@WebSocket.OnHttpUpgrade) - the request headers
- `int` (`@WebSocket.OnClose`) - the close code
- `java.lang.String` (`@WebSocket.OnClose`) - the close reason
- `java.lang.Throwable` (`@WebSocket.OnError`) - the throwable thrown

Annotations on endpoint type:

- [`io.helidon.webserver.websocket.WebSocketServer.Endpoint`](/apidocs/io.helidon.webserver.websocket/io/helidon/webserver/websocket/WebSocketServer.Endpoint.html) - required annotation
- [`io.helidon.webserver.websocket.WebSocketServer.Listener`](/apidocs/io.helidon.webserver.websocket/io/helidon/webserver/websocket/WebSocketServer.Listener.html) - to define the named listener this should be served on (named port/socket)
- [`io.helidon.http.Http.Path`](/apidocs/io.helidon.http/io/helidon/http/Http.Path.html) - path (context) this endpoint will be available on

Annotations on endpoint methods:

- [`io.helidon.websocket.WebSocket.OnMessage`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnMessage.html) - receives either a binary or a text message
- [`io.helidon.websocket.WebSocket.OnHttpUpgrade`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnHttpUpgrade.html) - invoked during HTTP upgrade, the method may return `Headers` to be sent during the upgrade response
- [`io.helidon.websocket.WebSocket.OnOpen`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnOpen.html) - invoked when the WebSocket connection is established (after upgrade)
- [`io.helidon.websocket.WebSocket.OnClose`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnClose.html) - invoked when the WebSocket connection is closed
- [`io.helidon.websocket.WebSocket.OnError`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnError.html) - invoked when an error occurs when invoking other methods

Annotations on method parameters:

- [`io.helidon.http.Http.PathParam`](/apidocs/io.helidon.http/io/helidon/http/Http.PathParam.html) - Typed parameter from path template

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

- [`io.helidon.websocket.WsSession`](/apidocs/io.helidon.websocket/io/helidon/websocket/WsSession.html)
- `boolean` in a method annotated with `@WebSocket.OnMessage` - indicator of "last" message (if not present, the message will be combined before delivery)
- `java.lang.String` (`@WebSocket.OnMessage`) - the message delivered (text)
- `java.io.Reader` (`@WebSocket.OnMessage`) - the message delivered (text)
- [`io.helidon.common.buffers.BufferData`](/apidocs/io.helidon.common.buffers/io/helidon/common/buffers/BufferData.html) (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.nio.ByteBuffer` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `java.io.InputStream` (`@WebSocket.OnMessage`) - the message delivered (binary)
- `int` (`@WebSocket.OnClose`) - the close code
- `java.lang.String` (`@WebSocket.OnClose`) - the close reason
- `java.lang.Throwable` (`@WebSocket.OnError`) - the throwable thrown

Annotations on endpoint type:

- [`io.helidon.webclient.websocket.WebSocketClient`](/apidocs/io.helidon.webclient.websocket/io/helidon/webclient/websocket/WebSocketClient.Endpoint.html) - required annotation
- [`io.helidon.http.Http.Path`](/apidocs/io.helidon.http/io/helidon/http/Http.Path.html) - path (context) this endpoint will be available on

Annotations on endpoint methods:

- [`io.helidon.websocket.WebSocket.OnMessage`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnMessage.html) - receives either a binary or a text message
- [`io.helidon.websocket.WebSocket.OnHttpUpgrade`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnHttpUpgrade.html) - invoked during HTTP upgrade, the method may return `Headers` to be sent during the upgrade response
- [`io.helidon.websocket.WebSocket.OnOpen`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnOpen.html) - invoked when the WebSocket connection is established (after upgrade)
- [`io.helidon.websocket.WebSocket.OnClose`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnClose.html) - invoked when the WebSocket connection is closed
- [`io.helidon.websocket.WebSocket.OnError`](/apidocs/io.helidon.websocket/io/helidon/websocket/WebSocket.OnError.html) - invoked when an error occurs when invoking other methods

Annotations on method parameters:

- [`io.helidon.http.Http.PathParam`](/apidocs/io.helidon.http/io/helidon/http/Http.PathParam.html) - Typed parameter from path template defined by `@Http.Path` on the class

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

- [`io.helidon.webserver.cors.Cors.Defaults`](%7Bws-cors-javadoc%7D/Cors.Defaults.md) - support all methods, all origins, do not combine with other annotations from `Cors` class
- [`io.helidon.webserver.cors.Cors.AllowOrigins`](%7Bws-cors-javadoc%7D/Cors.AllowOrigins.md) - configure allowed origins, either as a exact string, or regular expression (if the value contains `\`, `*` or `{`, it is considered a regular expression)
- [`io.helidon.webserver.cors.Cors.AllowMethods`](%7Bws-cors-javadoc%7D/Cors.AllowMethods.md) - set of allowed methods that the different origin script can use
- [`io.helidon.webserver.cors.Cors.AllowHeaders`](%7Bws-cors-javadoc%7D/Cors.AllowHeaders.md) - set of allowed headers sent from the different origin script
- [`io.helidon.webserver.cors.Cors.ExposeHeaders`](%7Bws-cors-javadoc%7D/Cors.ExposeHeaders.md) - set of headers from response exposed to the different origin script
- [`io.helidon.webserver.cors.Cors.AllowCredentials`](%7Bws-cors-javadoc%7D/Cors.AllowCredentials.md) - whether to add credentials (such as Cookie) to requests from the different origin script
- [`io.helidon.webserver.cors.Cors.MaxAgeSeconds`](%7Bws-cors-javadoc%7D/Cors.MaxAgeSeconds.md) - maximal number of seconds the pre-flight is considered valid

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
