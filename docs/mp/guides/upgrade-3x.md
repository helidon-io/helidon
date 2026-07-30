<!--@frontmatter
description: "Learn how to upgrade your Helidon MP application from 2.x to 3.x"
navigation:
  icon: i-lucide-refresh-cw
-->
# 3.x Upgrade

In Helidon 3.x we have made some changes to APIs and runtime behavior. This
guide will help you upgrade a Helidon MP 2.x application to 3.x.

## Java 17 Runtime

Java 11 is no longer supported in Helidon 3. Java 17 or newer is required.

## javax.\* namespace to jakarta.\* namespace

Helidon 3 supports MicroProfile 5.0 and *selected* Jakarta EE 9.1 APIs. In
Jakarta EE 9.1 the Java package namespace was changed from `javax` to `jakarta`.
Therefore, you must change your application to use `jakarta` instead of
corresponding `javax` for Jakarta EE packages.

In version 3.x Helidon supports MicroProfile 5.0 specification, which now is
fully migrated to `jakarta` namespace.

As a result, `javax` module is no longer in dependency management of Helidon
parent pom files.

## MicroProfile 5.0 support

MicroProfile 5.0 enables MicroProfile APIs to be used together with Jakarta EE
9.1 (Jakarta EE namespace). This release was mainly focused on updating
dependencies from `javax` to `jakarta`, as well as overall stability and
usability improvements.

MicroProfile 5.0 lays the foundation for the rapid innovation of MicroProfile
APIs for its 2022 releases.

MicroProfile 5.0 is an umbrella for the following specifications and their
corresponding versions:

- MicroProfile Config 3.1
- MicroProfile Fault Tolerance 4.0.2
- MicroProfile Health 4.0
- MicroProfile JWT Authentication 2.1
- MicroProfile Metrics 5.1.1
- MicroProfile OpenAPI 3.1.1
- MicroProfile OpenTracing 3.0
- MicroProfile Rest Client 3.0

Helidon 3.x supports the following Jakarta EE specifications:

- CDI (Jakarta Contexts and Dependency Injection) 4.0
- JAX-RS (Jakarta RESTful Web Services) 3.1
- JSON-B (Jakarta JSON Binding) 3.0
- JSON-P (Jakarta JSON Processing) 2.1
- Jakarta Annotations 2.1.1
- Jakarta Persistence API 3.1
- Jakarta Transactions API 2.0
- Jakarta WebSocket API 2.1
- Jakarta Bean Validation 3.0

Corresponding changes to Helidon code were made to support the corresponding
specifications' versions.

### Incompatible changes for each specification

Migration from `javax` to `jakarta` namespace is making this release backward
incompatible with previous version of MicroProfile. For each specification there
are also API and functional changes, described below.

#### MicroProfile specifications

- **MicroProfile Config 3.1**:

  Incompatible changes described in [MicroProfile Config 3.1
  Specification][microprofile-con]

- **MicroProfile Fault Tolerance 4.0.2**:

  Incompatible changes described in [MicroProfile Fault Tolerance 4.0.2
  Specification][microprofile-fau]

- **MicroProfile Health 4.0**:

  Incompatible changes described in [MicroProfile Health 4.0
  Specification][microprofile-hea]

- **MicroProfile JWT Authentication 2.1**:

  Incompatible changes described in [MicroProfile JWT Authentication 2.1
  Specification][microprofile-jwt]

- **MicroProfile Metrics 5.1.1**:

  Incompatible changes described in [MicroProfile Metrics 5.1.1
  Specification][microprofile-met]

- **MicroProfile OpenAPI 3.1.1**:

  Incompatible changes described in [MicroProfile OpenAPI 3.1.1
  Specification][microprofile-ope]

- **MicroProfile OpenTracing 3.0**:

  Incompatible changes described in [MicroProfile OpenTracing 3.0
  Specification][microprofile-ope-2]

- **MicroProfile Rest Client 3.0**:

  Incompatible changes described in [MicroProfile Rest Client 3.0
  Specification][microprofile-res]

### Supported Jakarta EE specifications

- **CDI (Jakarta Contexts and Dependency Injection) 4.0**:

  Changes described in [CDI (Jakarta Contexts and Dependency Injection) 4.0
  Specification][cdi-jakarta-cont]

- **JAX-RS (Jakarta RESTful Web Services) 3.1**:

  Moved to `jakarta` namespace. Changes described in [JAX-RS (Jakarta RESTful
  Web Services) 3.1Specification][microprofile-res]

- **JSON-B (Jakarta JSON Binding) 3.0**:

  Moved to `jakarta` namespace. Changes described in [JSON-B (Jakarta JSON
  Binding) 3.0 Specification][json-b-jakarta-j]

- **JSON-P (Jakarta JSON Processing) 2.1**:

  Moved to `jakarta` namespace.

- **Jakarta Annotations 2.1.1**:

  Moved to `jakarta` namespace. Moved to `jakarta` namespace. Full information
  in [Jakarta Annotations 2.1.1 Specification][jakarta-annotati]

- **Jakarta Persistence API 3.1**:

  Moved to `jakarta` namespace. Changes described in [Jakarta Persistence API
  3.1 Specification][jakarta-persiste]

- **Jakarta Transactions API 2.0**:

  Moved to `jakarta` namespace. Changes described in [Jakarta Transactions API
  2.0 Specification][jakarta-transact]

- **Jakarta WebSocket API 2.1**:

  Moved to `jakarta` namespace. Changes described in [Jakarta WebSocket API 2.1
  Specification][jakarta-websocke]

- **Jakarta Bean Validation 3.0**:

  Moved to `jakarta` namespace. Changes described in [Jakarta Bean Validation
  3.0 Specification][jakarta-bean-val]

> [!NOTE]
> Please, read each specification carefully for incompatible changes!

## Deprecations

- The custom Helidon OCI clients have been deprecated. Use the OCI Java SDK
  instead. For Helidon MP, use
  `io.helidon.integrations.oci:helidon-integrations-oci` only for OCI
  authentication, region, and configuration support; the legacy
  `io.helidon.integrations.oci.sdk:helidon-integrations-oci-sdk-cdi` module is
  deprecated.
- The `MultiPart buffered readers` have been deprecated. Use the `MultiPart
  stream readers` instead.

### Helidon Common

Deprecations in the following classes:

- `Resource` - old configuration approach (since 2.0)
- `ThreadPoolSupplier` - Named thread pools (since 2.4.2)

More information in the following [task][task].

### Media Common

Deprecations in the following classes:

- `ContentReaders` - Methods with alternatives (since 2.0)
- `ContentTypeCharset` - Class with alternative (since 2.0)
- `ContentWriters` - Methods with alternatives (since 2.0)
- `MessageBodyReaderContext` - Methods with alternatives (since 2.0)
- `MessageBodyWriterContext` - Methods with alternatives (since 2.0)
- `ReadableByteChannelPublisher` - Class with alternative (since 2.0)

More information in the following [task][task-2].

### Metrics

Deprecations in the following classes:

- `MetricsSupport` - 3 methods, replacing Config with metrics settings
- `KeyPerformanceIndicatorMetricsSettings` - New class in metrics API, for
  backward compatibility only
- `RegistryFactory` - New class in metrics API, for backward compatibility only

More information in the following [task][task-3].

### Common Context

Deprecations in the following class:

- `DataPropagationProvider` - clearData should use new method

More information in the following [task][task-4].

### GRPC core

Deprecations:

- `JavaMarshaller` - removed support for JavaMarshaller

More information in the following [task][task-5].

### LRA

Deprecations in the following class:

- `CoordinatorClient` - multiple methods
- `Headers`

More information in the following [task][task-6].

### MP Messaging

Deprecations in the following class:

- `MessagingCdiExtension` - Alternative methods used

More information in the following [task][task-7].

### JWT

Deprecations in the following class:

- `Jwt` - Audience can be a list (since 2.4.0)

More information in the following [task][task-8].

### MP Metrics

Deprecations in the following class:

- `MetricUtil` - multiple methods
- `MetricsCdiExtension` - multiple methods

More information in the following [task][task-9].

### HTTP Signature Security Provider

- `backwardCompatibleEol` - set to false

More information in the following [task][task-10].

### Service Common

Deprecations in the following class:

- `HelidonRestServiceSupport` - method *configureEndpoint(Rules)*

More information in the following [task][task-9].

### WebServer

- `Static content support` in `WebServer` - moved to a separate module. Fully
  removed from `WebServer` module.

More information in the following [task][task-11].

[microprofile-con]: https://download.eclipse.org/microprofile/microprofile-config-3.0.1/microprofile-config-spec-3.0.1.html#_incompatible_changes
[microprofile-fau]: https://download.eclipse.org/microprofile/microprofile-fault-tolerance-4.0/microprofile-fault-tolerance-spec-4.0.html#_backward_incompatible_changes=
[microprofile-hea]: https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html#_incompatible_changes
[microprofile-jwt]: https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.0/microprofile-jwt-auth-spec-2.0.html#_incompatible_changes
[microprofile-met]: https://download.eclipse.org/microprofile/microprofile-metrics-4.0/microprofile-metrics-spec-4.0.html#_incompatible_changes
[microprofile-ope]: https://download.eclipse.org/microprofile/microprofile-open-api-2.0.1/microprofile-openapi-spec-2.0.1.html#_incompatible_changes
[microprofile-ope-2]: https://download.eclipse.org/microprofile/microprofile-opentracing-3.0/microprofile-opentracing-spec-3.0.html#_incompatible_changes
[microprofile-res]: https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html#_incompatible_changes
[cdi-jakarta-cont]: https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#architecture
[json-b-jakarta-j]: https://jakarta.ee/specifications/jsonb/2.0/jakarta-jsonb-spec-2.0.html#change-log
[jakarta-annotati]: https://jakarta.ee/specifications/annotations/2.0/annotations-spec-2.0.html
[jakarta-persiste]: https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0.html#revision-history
[jakarta-transact]: https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html#revision-history
[jakarta-websocke]: https://jakarta.ee/specifications/websocket/2.0/websocket-spec-2.0.html#changes
[jakarta-bean-val]: https://jakarta.ee/specifications/bean-validation/2.0/bean-validation_2.0.html#changelog
[task]: https://github.com/helidon-io/helidon/issues/4363
[task-2]: https://github.com/helidon-io/helidon/issues/4364
[task-3]: https://github.com/helidon-io/helidon/issues/4365
[task-4]: https://github.com/helidon-io/helidon/issues/4366
[task-5]: https://github.com/helidon-io/helidon/issues/4367
[task-6]: https://github.com/helidon-io/helidon/issues/4368
[task-7]: https://github.com/helidon-io/helidon/issues/4369
[task-8]: https://github.com/helidon-io/helidon/issues/4370
[task-9]: https://github.com/helidon-io/helidon/issues/4371
[task-10]: https://github.com/helidon-io/helidon/issues/4372
[task-11]: https://github.com/helidon-io/helidon/issues/4374
