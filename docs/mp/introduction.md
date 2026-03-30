# Helidon MP

## Introduction

Helidon MP is an Eclipse MicroProfile 6.1 runtime that allows the Jakarta EE community to run microservices in a portable way. It is designed for ease of use and provides Spring Boot like development experience with heavy usage of dependency injection and annotations.

Even though Helidon MP supports Jakarta EE APIs it does not require an application server. Helidon MP applications are stand-alone Java applications running in their own JVM powered by Helidon WebServer. So you get all the benefits of a low overhead server built on Java virtual threads.

## Supported Jakarta EE Specifications

| Specification | Version | Description |
|----|----|----|
| [Jakarta Bean Validation](../mp/beanvalidation.md) | [3.0](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html) | Object level constraint declaration and validation facility |
| Jakarta Context and Dependency Injection (CDI) | [4.0](https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html) | Declarative dependency injection and supporting services |
| Jakarta JSON Processing (JSON-P) | [2.1](https://jakarta.ee/specifications/jsonp/2.1/apidocs) | API to parse, generate, transform, and query JSON docs |
| Jakarta JSON Binding (JSON-B) | [3.0](https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0.html) | Binding framework for converting POJOs to/from JSON docs |
| [Jakarta RESTful Web Services (JAX-RS)](../mp/server.md) | [3.1](https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html) | API to develop web services following the REST pattern |
| [Jakarta Persistence (JPA)](../mp/persistence.md#JPA) | [3.1](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html) | Management of persistence and object/relational mapping |
| [Jakarta Transactions (JTA)](../mp/persistence.md#JTA) | [2.0](https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html) | Allows handling transactions consistent with X/Open XA-spec |
| [Jakarta WebSocket](../mp/websocket.md) | [2.1](https://jakarta.ee/specifications/websocket/2.1/jakarta-websocket-spec-2.1.html) | API for Server and Client Endpoints for WebSocket protocol |

## Supported MicroProfile Specifications

| Specification | Version | Description |
|----|----|----|
| [MicroProfile Config](../mp/config/introduction.md) | [3.1](https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html) | A flexible configuration framework with support for multiple sources and formats |
| [MicroProfile Fault Tolerance](../mp/fault-tolerance.md) | [4.0.2](https://download.eclipse.org/microprofile/microprofile-fault-tolerance-4.0.2/microprofile-fault-tolerance-spec-4.0.2.html) | Common strategies for various system problems such as time-outs, retries, Circuit Breaker, etc. |
| [MicroProfile GraphQL](../mp/graphql.md) | [2.0](https://download.eclipse.org/microprofile/microprofile-graphql-2.0/microprofile-graphql-spec-2.0.html) | API for working with GraphQL |
| [MicroProfile Health](../mp/health.md) | [4.0](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html) | Health checks for automatic service restart/shutdown |
| [MicroProfile JWT Auth](../mp/jwt.md) | [2.1](https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html) | Defines a compact and self-contained way for securely transmitting information between parties as a JSON object |
| [MicroProfile Long-Running Actions (LRA)](../mp/lra.md) | [2.0](https://download.eclipse.org/microprofile/microprofile-lra-2.0/microprofile-lra-spec-2.0.html) | Distributed transactions for microservices following SAGA pattern |
| [MicroProfile Metrics](../mp/metrics/metrics.md) | [5.1.1](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html) | Defining and exposing telemetry data in Prometheus and JSON formats |
| [MicroProfile Open API](../mp/openapi/openapi.md) | [3.1.1](https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html) | Annotations for documenting your application endpoints |
| [MicroProfile OpenTracing](../mp/tracing.md) | [3.0](https://download.eclipse.org/microprofile/microprofile-opentracing-3.0/microprofile-opentracing-spec-3.0.html) | Profile and monitor your applications across multiple services |
| [MicroProfile Reactive Messaging](../mp/reactivemessaging/introduction.md) | [3.0](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html) | Standard API for sending and receiving messages/events using streams |
| [MicroProfile Reactive Streams Operators](../mp/reactivestreams/rsoperators.md) | [3.0](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/microprofile-reactive-streams-operators-spec-3.0.html) | Control flow and error processing for event streams |
| [MicroProfile REST Client](../mp/restclient/restclient.md) | [3.0](https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html) | Type-safe API for RESTful Web Services |

## Other Components

| Component | Description |
|----|----|
| [CORS](../mp/cors/cors.md) | Cross Origin Resource Sharing – API to control if and how REST resources served by their applications can be shared across origins |
| [gRPC](../mp/grpc/server.md) | gRPC server and client |
| [OCI SDK](../mp/integrations/oci.md) | Full set of APIs for working with OCI services |
| [Scheduling](../mp/scheduling.md) | Scheduling functionality based on [Cron-utils](https://github.com/jmrozanec/cron-utils) |
| [Security](../mp/security/security.md) | A tool-chain to handle authentication, authorization and context propagation |

## Upgrade

In case you need to upgrade the version of Helidon, follow the `Upgrade Guides`.

For upgrade from Helidon 1.x to 2.x:

- [Helidon MP 2x Upgrade Guide](../mp/guides/upgrade.md)

For upgrade from Helidon 2.x to 3.x:

- [Helidon MP 3x Upgrade Guide](../mp/guides/upgrade_3x.md)

For upgrade from Helidon 3.x to 4.x:

- [Helidon MP 4x Upgrade Guide](../mp/guides/upgrade_4x.md)

## Next Steps

- Try the [Helidon MP quickstart guides](../mp/guides/overview.md) to get your first Helidon MP application up and running in minutes.
- Browse the [Helidon Javadocs](/apidocs/index.html?overview-summary.html)
