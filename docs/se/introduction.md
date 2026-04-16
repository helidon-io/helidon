# Introduction

Helidon SE is Helidon’s foundational set of APIs and, as of Helidon 4, it uses virtual threads to enable these APIs to change from asynchronous to blocking.

## Components

The REST framework for Helidon SE is the Helidon WebServer. It was built from the ground up to take full advantage of Java 21’s virtual threads.

Helidon SE supports a number of additional Helidon features:

| Name | Description |
| --- | --- |
| [Config](config/introduction.md) | A flexible configuration framework with support for multiple sources and formats. |
| [CORS](cors.md) | Add support for CORS to your application using a Helidon module. |
| [DB Client](dbclient.md) | Provides a unified, reactive API for working with databases in non-blocking way. |
| [GraphQL](graphql.md) | Build GraphQL servers. |
| [gRPC](grpc/README.md) | Build gRPC servers and clients. |
| [Health Checks](health.md) | Expose health statuses of your applications. |
| [Injection](injection/injection.md) | Use of the Helidon injection in your applications. |
| [JSON Schema](json/schema.md) | Creation of the JSON Schema in your applications. |
| [Metrics](metrics/metrics.md) | Instrumentation to expose metrics of your applications. |
| [OpenAPI](openapi/openapi.md) | Support OpenAPI from your application. |
| [Reactive Messaging](reactive-messaging.md) | Use prepared tools for repetitive use case scenarios. |
| [Reactive Streams](reactivestreams/README.md) | APIs to work with reactive streams in Helidon. |
| [Security](security/introduction.md) | A tool-chain to handle authentication, authorization and context propagation. |
| [Tracing](tracing.md) | Profile and monitor your applications across multiple services. |
| [WebClient](webclient.md) | HTTP client that handles responses to the HTTP requests. |
| [WebServer](webserver/webserver.md) | A programmatic HTTP API that uses virtual threads to handle nearly unlimited concurrent requests without blocking a platform thread or starving other requests. |
| [WebSocket](websocket.md) | Enables Java applications to participate in WebSocket interactions as both servers and clients. |

## Upgrade

In case you need to upgrade the version of Helidon, follow the upgrade guides:

| Name | Description |
| --- | --- |
| [Helidon SE 4x Upgrade Guide](guides/upgrade_4x.md) | Follow this guide to migrate your application from Helidon 3.x to 4.x. |
| [Helidon SE 3x Upgrade Guide](guides/upgrade_3x.md) | Follow this guide to migrate your application from Helidon 2.x to 3.x. |
| [Helidon SE 2.x Upgrade Guide](guides/upgrade.md) | Follow this guide to migrate your application from Helidon 1.x to 2.x. |

## Next Steps

Try the Helidon SE quickstart guides to get your first Helidon SE application up and running in minutes.

| Name | Description |
| --- | --- |
| [Guides](guides/overview.md) | Follow step-by-step guides to build your applications using Helidon SE. |
| [Javadocs](/apidocs) | Browse the Helidon Javadocs. |
