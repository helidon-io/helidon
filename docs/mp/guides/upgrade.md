# Helidon MP 2.x Upgrade Guide

In Helidon 2.x we have made some changes to APIs and runtime behavior. This guide will help you migrate a Helidon MP 1.x application to 2.x.

## Java 11 Runtime

Java 8 is no longer supported in Helidon 2. Java 11 or newer is required.

## Tracing

We have upgraded to OpenTracing version 0.33.0 that is not backward compatible. OpenTracing introduced the following breaking changes:

| Removed | Replacement |
|----|----|
| `ScopeManager.active()` | `Tracer.activeSpan()` |
| `ScopeManager.activate(Span, boolean)` | `ScopeManager.activate(Span)` - second parameter is now always `false` |
| `SpanBuilder.startActive()` | `Tracer.activateSpan(Span)` |
| `TextMapExtractAdapter` and `TextMapInjectAdapter` | `TextMapAdapter` |
| Module name changed `opentracing.api` | `io.opentracing.api` (same for `noop` and `util`) |

If you use the `TracerBuilder` abstraction in Helidon and have no custom Spans, there is no change required

## Security: OIDC

When the OIDC provider is configured to use cookie (default configuration) to carry authentication information, the cookie `Same-Site` is now set to `Lax` (used to be `Strict`). This is to prevent infinite redirects, as browsers would refuse to set the cookie on redirected requests (due to this setting). Only in the case of the frontend host and identity host match, we leave `Strict` as the default

## MicroProfile Bundles

We have removed the versioned MicroProfile bundles (i.e. `helidon-microprofile-x.x`), and introduced unversioned core and full bundles:

- `io.helidon.microprofile.bundles:helidon-microprofile-core` - contains only MP Server and Config. Allows you to add only the specifications needed by your application.
- `io.helidon.microprofile.bundles:helidon-microprofile` - contains the latest full MicroProfile version implemented by Helidon

## Application Main and Startup

- `io.helidon.microprofile.server.Main` has been deprecated. Use `io.helidon.microprofile.cdi.Main` instead.
- `io.helidon.microprofile.server.Server` is still available, although the features are much reduced.
- You no longer need to initialize Java Util Logging explicitly. Put `logging.properties` on the classpath or in the current directory to be automatically picked up to configure Java Util Logging.

## JAX-RS Applications

Helidon 1.x usually required that you have an `Application` subclass that returned the Application classes to scan. For common cases this is no longer necessary, and you might be able to remove your `Application` class.

JAX-RS applications now work similarly to how they work in application servers:

- if there is an `Application` subclass that returns anything from `getClasses` or `getSingletons`, it is used as is
- if there is an `Application` subclass that returns empty sets from these methods, all available resource classes will be part of such an application
- if there is no `Application` subclass, a synthetic application will be created with all available resource classes
- `Application` subclasses MUST be annotated with `@ApplicationScoped`, otherwise they are ignored

## MicroProfile JWT-Auth

If a JAX-RS application exists that is annotated with `@LoginConfig` with value MP-JWT, the correct authentication provider is added to security. The startup would fail if the provider is required yet not configured.

## Security in Helidon MP

- If there is no authentication provider configured, authentication will now fail.
- If there is no authorization provider configured, the ABAC provider will be configured.

In Helidon 1.x these were configured if there was no provider configured overall.

## CDI and MicroProfile Server

In order to support GraalVM `native-image` we have had to re-implement how CDI is initialized and started. This has resulted in some changes in APIs and behavior:

- You can no longer start the CDI container yourself.
- You can only run a single instance of Server in a JVM.
- If you use `SeContainerInitializer` you will get an exception.
  - This can be worked around by configuration property `mp.initializer.allow=true`, and warning can be removed using `mp.initializer.no-warn=true`
  - Once `SeContainerInitializer` is used you can no longer use MP with `native-image`
- You can no longer provide a `Context` instance. The root context is now built-in.
- `MpService` and `MpServiceContext` have been removed.
  - Methods from context have been moved to `JaxRsCdiExtension` and `ServerCdiExtension`. These can be accessed from CDI extension through `BeanManager.getExtension`.
  - Methods `register` can be used on current `io.helidon.context.Context`
  - `MpService` equivalent is a CDI extension. All Helidon services were refactored to CDI extension (you can use these for reference).
- `Server.cdiContainer` is removed, use `CDI.current()` instead.

## Metrics

Helidon now supports only MicroProfile Metrics 2.x. Support for Metrics 1.x has been removed, and modules for 2.x have been renamed from `metrics2` to `metrics`.

## Java EE dependencies

We have moved from dependencies in groupId `javax` (Java EE modules) to dependencies in groupId `jakarta` (Jakarta EE modules).

In case you declared a dependency on a javax module, you should change it to a jakarta one.

Example:

```xml
<dependency>
    <groupId>javax.activation</groupId>
    <artifactId>javax.activation-api</artifactId>
</dependency>
```

should be changed to

```xml
<dependency>
    <groupId>jakarta.activation</groupId>
    <artifactId>jakarta.activation-api</artifactId>
</dependency>
```

As the `javax` module is no longer in dependency management of Helidon parent pom files.
