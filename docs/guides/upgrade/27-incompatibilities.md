<!--@frontmatter
description: "Summary of incompatibilities when upgrading Helidon 4 applications to Helidon 27"
navigation:
  icon: i-lucide-alert-triangle
-->
# 27 Upgrade Incompatibilities

This document summarizes known incompatibilities when upgrading from Helidon
4.5.4 to Helidon 27. It focuses on APIs, modules, and managed third-party
libraries that can require application changes.

## High Impact Changes

| Area | What changed | What to do |
| --- | --- | --- |
| Java | Helidon 27 requires Java 26. | Build, test, package, and run with JDK 26 or later. Update container images and CI toolchains. |
| MicroProfile | MicroProfile support has been decoupled from Helidon and releases independently. MicroProfile support is not available for Helidon 27 at this time. | Keep MicroProfile workloads on Helidon 4.5.x until the independent MicroProfile release is available, or rewrite them to Helidon Core APIs. |
| Removed modules | The `microprofile`, `jersey`, `lra`, `messaging`, and `integrations` module trees are no longer part of Helidon 27. | Remove these dependencies or replace them with direct third-party dependencies and application-owned integration code. |
| Metrics | Prometheus Java client integration is removed. Metrics scopes are ignored by Helidon Core metrics and are deprecated for removal. Static `Metrics` helper methods are removed. | Use the Micrometer-backed metrics provider and service registry lookups. Replace scopes with tags. |
| Tracing | Jaeger, Zipkin, and OpenTracing compatibility modules are removed. Static global tracer ownership APIs are removed. | Use OpenTelemetry through `helidon-tracing-providers-opentelemetry` and prefer OTLP export. Use injection or the service registry for tracer access. |
| Config | `io.helidon.config.Config` no longer extends the old common config API. Object mapping is no longer in the core Config module. | Update code that depends on `io.helidon.common.config` bridge types. Add `helidon-config-object-mapping` when object mapping is used. |
| Security | HTTP Digest authentication support is removed. Some legacy security providers and constructors are removed or deprecated. | Use OIDC, HTTP Basic, header assertion, HTTP signatures, or ABAC. Replace direct builder constructors with static `builder()` or `create(...)` methods. |

## Removed Modules

Helidon 27 removes several module families that were available in Helidon
4.5.4.

### MicroProfile and JAX-RS

Removed artifacts include:

- `helidon-mp`
- `helidon-microprofile`
- `helidon-microprofile-*`
- `helidon-jersey-*`
- MicroProfile archetypes, examples, tests, and TCK modules

This removes Helidon-provided CDI, JAX-RS, and MicroProfile specification
implementations from the Helidon 27 release train.

### LRA and Messaging

Removed artifacts include:

- `helidon-lra-*`
- `helidon-microprofile-lra*`
- `helidon-messaging`
- `helidon-messaging-aq`
- `helidon-messaging-jms`
- `helidon-messaging-kafka`
- `helidon-messaging-mock`
- `helidon-messaging-wls-jms`

### Integrations

The former `integrations` tree is removed. This includes Helidon-provided
integration modules for CDI data sources, JPA, JTA, JDBC, Eureka, CRaC,
Micrometer CDI integration, Micronaut, MicroStream, Neo4j, OCI, Vault,
LangChain4j, OpenAPI UI, GraalVM native image extensions, and database helper
modules.

Applications that used these artifacts should depend directly on the
corresponding third-party library or keep that part of the application on
Helidon 4.5.x until a replacement is available.

### Tracing Compatibility

Removed artifacts include:

- `helidon-tracing-exporter-jaeger`
- `helidon-tracing-providers-jaeger`
- `helidon-tracing-providers-opentracing`
- `helidon-tracing-providers-zipkin`
- `helidon-tracing-tracer-resolver`
- `helidon-tracing-jersey`
- `helidon-tracing-jersey-client`

Use OpenTelemetry tracing and OTLP export. The Zipkin exporter is also no
longer published by OpenTelemetry Java 1.65.

### Metrics and CORS

Removed artifacts include:

- `helidon-metrics-prometheus`
- `helidon-cors`

Use `helidon-webserver-observe-metrics` for metrics endpoint support and
`io.helidon.webserver:helidon-webserver-cors` for CORS.

## Removed and Changed APIs

### Config

- `Config` no longer extends `io.helidon.common.config.Config`.
- `Config.Key` no longer extends `io.helidon.common.config.Config.Key`.
- Deprecated `Config.global(Config)` and `Config.config(io.helidon.common.config.Config)` bridge methods are removed.
- Deprecated `Config.map(...)` and `Config.mapList(...)` overloads that used the common config API are removed.
- `ConfiguredProvider` implementations must use `io.helidon.config.Config`.
- Object mapping requires `helidon-config-object-mapping`.

### Metrics

- Former static convenience methods on `Metrics` are removed.
- Use injected `MeterRegistry` or `Services.get(MeterRegistry.class)`.
- Use `Services.get(MetricsFactory.class)` for registry-owned creation helpers.
- Metrics scopes are deprecated and ignored by Helidon Core metrics.
- Legacy scoped paths such as `/observe/metrics/application`, `/observe/metrics/base`, and `/observe/metrics/vendor` return the same unscoped metrics as `/observe/metrics`.
- `gc.time` is always a `Gauge`.
- `metrics.gc-time-type` is removed.
- `metrics.rest-request-enabled` is removed. Use `metrics.rest-request.enabled`.

### Tracing and Telemetry

- Static global tracer APIs are removed from Helidon tracing.
- `HelidonOpenTelemetry.global(...)` and `OpenTelemetryTracerProvider.globalTracer(...)` are removed.
- Use injection or `Services.get(Tracer.class)`.
- Register application-owned `OpenTelemetry` or `Tracer` instances with `Services.set(...)` before lookup.
- Deprecated Helidon tracing baggage APIs are removed. Use OpenTelemetry baggage APIs where needed.

### WebServer and HTTP

- Server-side Unix domain sockets are no longer selected with `bind-address: "unix:..."`.
- Configure Unix domain sockets with `bindings.uds.socket`.
- `ConnectionConfigBlueprint` is removed. Use `io.helidon.common.socket.SocketOptionsBlueprint`.
- `ListenerConfigBlueprint.receiveBufferSize()` and `connectionConfig()` are removed. Use `connectionOptions()`.
- `Header.value()` is removed. Use `Header.get()`.
- `HostValidator` is removed. Use `io.helidon.common.uri.UriValidator`.
- `TlsConfigBlueprint` helper APIs and old `Tls` reload methods are removed or deprecated. Use `TlsMaterial`.
- `WebServer.reloadTls(Tls)` is deprecated. Use `reloadTls(TlsMaterial)`.
- `ListenerConfigBlueprint.maxTcpConnections()` is deprecated. Use `maxConnections()`.

### WebClient

- Deprecated compatibility methods are removed from WebClient connection, DNS, and HTTP client configuration APIs.
- Use current URI, connection, and DNS resolver builder APIs.

### Fault Tolerance

- Deprecated helpers such as `FaultTolerance.config(io.helidon.common.config.Config)`, `executor(Supplier<? extends ExecutorService>)`, `toDelayedRunnable(...)`, and `toDelayedCallable(...)` are removed.
- If both `delay-factor` and absolute `jitter` are configured, Helidon 27 applies the delay factor first and then jitter. Earlier releases ignored `jitter` in that case.

### Security

- HTTP Digest authentication is removed.
- JWT Provider is retained but is no longer evolved.
- Several builder constructors are deprecated for removal. Use static `builder()` or `create(...)` methods.
- JSON-P was removed from IDCS, OIDC, and JWT internals.
- `EncryptedJwt.RSA1_5` is deprecated for removal.

### DB Client

- `helidon-dbclient-metrics-hikari` keeps the same Maven coordinates and Java packages, but its JPMS module name is now `io.helidon.dbclient.metrics.hikari`.
- If your `module-info.java` uses `requires helidon.dbclient.metrics.hikari;`, change it to `requires io.helidon.dbclient.metrics.hikari;`.
- Deprecated DB client builder constructors are deprecated for removal. Use static `builder()` methods.

### gRPC and GraphQL

- Deprecated gRPC helper APIs such as `CollectingObserver` and `ResponseHelper` are removed.
- MP-style nested gRPC annotations are deprecated for removal.
- `@GraphQl.Subscription` is deprecated. Subscription execution is reserved for future use and is ignored.

### Feature Metadata

- Top-level `Aot`, `Feature`, `Incubating`, and `Preview` annotations are removed.
- Use the nested annotations in `io.helidon.common.features.api.Features`.

## Deprecated APIs in Helidon 27

Helidon 27 still contains deprecated APIs that compile but should not be used in
new code. Treat these as near-term cleanup items during the upgrade:

- Metrics scopes and scope-aware registry methods
- Static metrics factory and lifecycle methods
- Static meter builders and factory methods
- Tracing global accessors and wrapper aliases
- TLS reload methods that accept `Tls`
- Security and DB client direct builder constructors
- Old time-unit overloads in reactive, file watcher, and health APIs
- Old config date and time mappers for `Date`, `Calendar`, `TimeZone`, and related types
- `SecurityContext.atzChecked()`
- HTTP/1 split receive and send logging accessors
- `BufferData.asInputStream()`
- `HeaderNames.TSV_NAME` and `HeaderNames.TSV`

Compile with deprecation warnings enabled and replace these APIs before relying
on them for long-lived Helidon 27 code.

## Managed Third-Party Libraries

The following managed dependency changes are most likely to matter to
applications upgrading from Helidon 4.5.4.

| Library | Helidon 4.5.4 | Helidon 27 | Compatibility notes |
| --- | --- | --- | --- |
| HikariCP | 5.0.1 | 7.1.0 | Validate pool behavior and metrics. HikariCP 6 changed some connection eviction and metrics behavior. |
| Micrometer | 1.15.12 | 1.17.1 | Prometheus duplicate meter names are stricter. Duplicate time series errors now use the Prometheus Java client exception type. |
| Micrometer Prometheus | 1.15.2 | 1.17.1 | Review custom Prometheus naming conventions and any direct Prometheus Java client usage. |
| OpenTelemetry | 1.62.0 | 1.65.0 | Zipkin exporter publishing stopped in OpenTelemetry Java 1.65. Prometheus reader constructors and some SPI property names changed in earlier 1.63 and 1.64 releases. |
| OpenTelemetry semantic conventions | 1.37.0 | 1.43.0 | Review code that uses semantic convention constants directly. |
| Protobuf | 4.31.1 | 4.36.0 | Regenerate gRPC or protobuf classes with a matching `protoc` version if your build pins code generation. |
| ASM | 9.8 | 9.10.1 | Usually build-time only. Recheck custom bytecode tooling. |

Jackson remains at `2.21.6` when comparing Helidon 4.5.4 with Helidon 27.
Gson remains managed, but the Helidon Gson media module is removed.
