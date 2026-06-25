<!--@frontmatter
description: "Helidon MP Specifications"
navigation:
  icon: i-lucide-layers-3
-->
# Specifications

## MicroProfile

Helidon MP is an Eclipse MicroProfile 6.1 runtime. It implements and supports
the following specifications for standardized microservice APIs.

| Specification                                              | Version                           | Description                                                                                                     |
|------------------------------------------------------------|-----------------------------------|-----------------------------------------------------------------------------------------------------------------|
| [MicroProfile Config][mp-config]                           | [3.1][mp-config-spec]             | A flexible configuration framework with support for multiple sources and formats                                |
| [MicroProfile Fault Tolerance][mp-ft]                      | [4.0.2][mp-ft-spec]               | Common strategies for various system problems such as time-outs, retries, Circuit Breaker, etc.                 |
| [MicroProfile GraphQL][mp-graphql]                         | [2.0][mp-graphql-spec]            | API for working with GraphQL                                                                                    |
| [MicroProfile Health][mp-health]                           | [4.0][mp-health-spec]             | Health checks for automatic service restart/shutdown                                                            |
| [MicroProfile JWT Auth][mp-jwt]                            | [2.1][mp-jwt-spec]                | Defines a compact and self-contained way for securely transmitting information between parties as a JSON object |
| [MicroProfile Long-Running Actions (LRA)][mp-lra]          | [2.0][mp-lra-spec]                | Distributed transactions for microservices following SAGA pattern                                               |
| [MicroProfile Metrics][mp-metrics]                         | [5.1.1][mp-metrics-spec]          | Defining and exposing telemetry data in Prometheus and JSON formats                                             |
| [MicroProfile Open API][mp-openapi]                        | [3.1.1][mp-openapi-spec]          | Annotations for documenting your application endpoints                                                          |
| [MicroProfile OpenTracing][mp-opentracing]                 | [3.0][mp-opentracing-spec]        | Profile and monitor your applications across multiple services                                                  |
| [MicroProfile Reactive Messaging][mp-reactive-messaging]   | [3.0][mp-reactive-messaging-spec] | Standard API for sending and receiving messages/events using streams                                            |
| [MicroProfile Reactive Streams Operators][mp-rs-operators] | [3.0][mp-rs-operators-spec]       | Control flow and error processing for event streams                                                             |
| [MicroProfile REST Client][mp-rest-client]                 | [3.0][mp-rest-client-spec]        | Type-safe API for RESTful Web Services                                                                          |

## Jakarta EE

MicroProfile 6.1 includes the Jakarta EE Core Profile. As part of that support,
Helidon MP implements and supports the following specifications.

| Specification                                      | Version                       | Description                                                 |
|----------------------------------------------------|-------------------------------|-------------------------------------------------------------|
| [Jakarta Bean Validation][validation]              | [3.0][bv-spec]                | Object level constraint declaration and validation facility |
| Jakarta Context and Dependency Injection (CDI)     | [4.0][cdi-spec]               | Declarative dependency injection and supporting services    |
| Jakarta JSON Processing (JSON-P)                   | [2.1][jsonp-spec]             | API to parse, generate, transform, and query JSON docs      |
| Jakarta JSON Binding (JSON-B)                      | [3.0][jsonb-spec]             | Binding framework for converting POJOs to/from JSON docs    |
| [Jakarta RESTful Web Services (JAX-RS)][mp-server] | [3.1][jaxrs-spec]             | API to develop web services following the REST pattern      |
| [Jakarta Persistence (JPA)][jpa]                   | [3.1][jpa-spec]               | Management of persistence and object/relational mapping     |
| [Jakarta Transactions (JTA)][jta]                  | [2.0][jta-spec]               | Allows handling transactions consistent with X/Open XA-spec |
| [Jakarta WebSocket][jakarta-websocke]              | [2.1][jakarta-websocket-spec] | API for Server and Client Endpoints for WebSocket protocol  |

[validation]: ../mp/validation.md
[bv-spec]: https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html
[cdi-spec]: https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html
[jsonp-spec]: https://jakarta.ee/specifications/jsonp/2.1/apidocs
[jsonb-spec]: https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0.html
[mp-server]: ../mp/server.md
[jaxrs-spec]: https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html
[jpa]: ../mp/persistence.md#jakarta-persistence-jpa
[jpa-spec]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
[jta]: ../mp/persistence.md#jakarta-transactions-jta-integration
[jta-spec]: https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html
[jakarta-websocke]: ../mp/websocket.md
[jakarta-websocket-spec]: https://jakarta.ee/specifications/websocket/2.1/jakarta-websocket-spec-2.1.html
[mp-config]: ../mp/config/config.md
[mp-config-spec]: https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html
[mp-ft]: ../mp/fault-tolerance.md
[mp-ft-spec]: https://download.eclipse.org/microprofile/microprofile-fault-tolerance-4.0.2/microprofile-fault-tolerance-spec-4.0.2.html
[mp-graphql]: ../mp/graphql.md
[mp-graphql-spec]: https://download.eclipse.org/microprofile/microprofile-graphql-2.0/microprofile-graphql-spec-2.0.html
[mp-health]: ../mp/health.md
[mp-health-spec]: https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html
[mp-jwt]: ../mp/jwt.md
[mp-jwt-spec]: https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html
[mp-lra]: ../mp/lra.md
[mp-lra-spec]: https://download.eclipse.org/microprofile/microprofile-lra-2.0/microprofile-lra-spec-2.0.html
[mp-metrics]: ../mp/metrics/metrics.md
[mp-metrics-spec]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html
[mp-openapi]: ../mp/openapi/openapi.md
[mp-openapi-spec]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html
[mp-opentracing]: ../mp/tracing.md
[mp-opentracing-spec]: https://download.eclipse.org/microprofile/microprofile-opentracing-3.0/microprofile-opentracing-spec-3.0.html
[mp-reactive-messaging]: ../mp/reactive-messaging/reactive-messaging.md
[mp-reactive-messaging-spec]: https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html
[mp-rs-operators]: ../mp/reactive-streams/rsoperators.md
[mp-rs-operators-spec]: https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/microprofile-reactive-streams-operators-spec-3.0.html
[mp-rest-client]: ../mp/restclient/restclient.md
[mp-rest-client-spec]: https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html
