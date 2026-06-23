# Introduction

Helidon SE is Helidon’s foundational set of APIs and, as of Helidon 4, it uses
virtual threads to enable these APIs to change from asynchronous to blocking.

## Components

The REST framework for Helidon SE is the Helidon WebServer. It was built from
the ground up to take full advantage of Java 21’s virtual threads.

Helidon SE supports a number of additional Helidon features:

<!--@mdc ::table-collapse -->
| Name                                   | Description                                                                                                                                                     |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Config][config]                       | A flexible configuration framework with support for multiple sources and formats.                                                                               |
| [CORS][cors]                           | Add support for CORS to your application using a Helidon module.                                                                                                |
| [DB Client][db-client]                 | Provides a unified, reactive API for working with databases.                                                                                                    |
| [GraphQL][graphql]                     | Build GraphQL servers.                                                                                                                                          |
| [gRPC][grpc]                           | Build gRPC servers and clients.                                                                                                                                 |
| [Health Checks][health-checks]         | Expose health statuses of your applications.                                                                                                                    |
| [Injection][injection]                 | Use of the Helidon injection in your applications.                                                                                                              |
| [JSON Schema][json-schema]             | Creation of the JSON Schema in your applications.                                                                                                               |
| [Metrics][metrics]                     | Instrumentation to expose metrics of your applications.                                                                                                         |
| [OpenAPI][openapi]                     | Support OpenAPI from your application.                                                                                                                          |
| [Reactive Messaging][reactive-messagi] | Use prepared tools for repetitive use case scenarios.                                                                                                           |
| [Reactive Streams][reactive-streams]   | APIs to work with reactive streams in Helidon.                                                                                                                  |
| [Security][security]                   | A tool-chain to handle authentication, authorization and context propagation.                                                                                   |
| [Tracing][tracing]                     | Profile and monitor your applications across multiple services.                                                                                                 |
| [WebClient][webclient]                 | HTTP client that handles responses to the HTTP requests.                                                                                                        |
| [WebServer][webserver]                 | A programmatic HTTP API that uses virtual threads to handle nearly unlimited concurrent requests without blocking a platform thread or starving other requests. |
| [WebSocket][websocket]                 | Enables Java applications to participate in WebSocket interactions as both servers and clients.                                                                 |
<!--@mdc :: -->

## Upgrade

In case you need to upgrade the version of Helidon, follow the upgrade guides:

| Name                                             | Description                                                            |
|--------------------------------------------------|------------------------------------------------------------------------|
| [Helidon SE 4x Upgrade Guide][helidon-se-4x-up]  | Follow this guide to migrate your application from Helidon 3.x to 4.x. |
| [Helidon SE 3x Upgrade Guide][helidon-se-3x-up]  | Follow this guide to migrate your application from Helidon 2.x to 3.x. |
| [Helidon SE 2.x Upgrade Guide][helidon-se-2-x-u] | Follow this guide to migrate your application from Helidon 1.x to 2.x. |

## Next Steps

Try the Helidon SE quickstart guides to get your first Helidon SE application up
and running in minutes.

| Name                 | Description                                                             |
|----------------------|-------------------------------------------------------------------------|
| [Guides][guides]     | Follow step-by-step guides to build your applications using Helidon SE. |
| [Javadocs][javadocs] | Browse the Helidon Javadocs.                                            |

[config]: config/introduction.md
[cors]: cors.md
[db-client]: dbclient.md
[graphql]: graphql.md
[grpc]: grpc/README.md
[health-checks]: health.md
[injection]: injection/injection.md
[json-schema]: json/schema.md
[metrics]: metrics/metrics.md
[openapi]: openapi/openapi.md
[reactive-messagi]: reactive-messaging.md
[reactive-streams]: reactivestreams/README.md
[security]: security/introduction.md
[tracing]: tracing.md
[webclient]: webclient.md
[webserver]: webserver/webserver.md
[websocket]: websocket.md
[helidon-se-4x-up]: guides/upgrade-4x.md
[helidon-se-3x-up]: guides/upgrade-3x.md
[helidon-se-2-x-u]: guides/upgrade.md
[guides]: guides/overview.md
[javadocs]: https://helidon.io/docs/v4/apidocs
