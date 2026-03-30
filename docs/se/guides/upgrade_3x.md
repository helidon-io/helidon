# Helidon SE 3.x Upgrade Guide

In Helidon 3 we have made some changes to APIs and runtime behavior. This guide will help you upgrade a Helidon SE 2.x application to 3.x.

## Java 17 Runtime

Java 11 is no longer supported in Helidon 3. Java 17 or newer is required. Please follow the instructions in [Prerequisites](../../about/prerequisites.md) for proper installation.

## New Routing

Handling routes based on the protocol version is now possible by registering specific routes on routing builder.

For further information check [WebServer Documentation](../webserver/webserver.md)

## Http/2 Support

Helidon support of Http/2 is no longer experimental.

Http/2 needed to be explicitly enabled by configuration in Helidon 2:

*Enabling Http/2 support in Helidon 2*

``` yaml
server:
  port: 8080
  host: 0.0.0.0
  experimental:
    enable-http2: true
    http2-max-content-length: 16384
```

In Helidon 3 Http/2 is automatically enabled when artifact with Http/2 support is available on the classpath.

*Enabling Http/2 support in Helidon 3 by adding dependency*

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-http2</artifactId>
</dependency>
```

With above dependency Helidon 3 supports Http/2 upgrade from Http/1, cleartext Http/2 without prior knowledge and Http/2 with ALPN over TLS.

In Helidon 2, max content length was configurable with `server.experimental.http2-max-content-length`, in Helidon 3 can be configured with `server.max-upgrade-content-length` globally or per socket with the same `max-upgrade-content-length` key.

*Max upgrade content length in Helidon 3*

``` yaml
server:
  port: 8080
  host: 0.0.0.0
  max-upgrade-content-length: 16384
```

For further information check [WebServer Documentation](../webserver/webserver.md)

## WebSocket

Helidon SE support is now based on the `WebSocketRouting` class which enables Helidon application to configure routing for both annotated and programmatic WebSocket endpoints. `TyrusSupport` is now deprecated. Websocket support in now placed in different artifact.

*Helidon 2 WebSocket support dependency*

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-tyrus</artifactId>
</dependency>
```

*Helidon 3 WebSocket support dependency*

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-websocket</artifactId>
</dependency>
```

In Helidon 2, WebSocket routing is defined by registering `TyrusSupport` as additional service:

*Helidon 2 WebSocket route registering*

``` java
WebServer.builder(Routing.builder()
                          .register("/rest", new SomeRestService()) 
                          .register("/websocket", TyrusSupport.builder() 
                                  .register(ServerEndpointConfig.Builder
                                                    .create(MessageBoardEndpoint.class, "/")
                                                    .encoders(encoders)
                                                    .build())
                                  .build()
                          ))
        .port(8080)
        .build();
```

- Traditional REST routing service registration
- WebSocket setup with Tyrus service

In Helidon 3, WebSocket routing is defined by adding another routing:

*Helidon 3 WebSocket route registering*

``` java
WebServer.builder()
        .routing(r -> r
                .register("/rest", new SomeRestService()) 
        )
        .addRouting(WebSocketRouting.builder() 
                            .endpoint("/websocket", ServerEndpointConfig.Builder
                                    .create(MessageBoardEndpoint.class, "/board")
                                    .encoders(encoders)
                                    .build())
                            .build())
        .port(8080)
```

- Traditional REST routing service registration
- WebSocket routing setup

## Deprecations

- The custom Helidon OCI clients have been deprecated ([See PR](https://github.com/oracle/helidon/pull/4015)).

Use the OCI Java SDK instead. If you use Helidon MP you can inject OCI SDK clients by adding the dependency `io.helidon.integrations.oci.sdk:helidon-integrations-oci-sdk-cdi`.

> [!NOTE]
> See [Resolving compatibility issue with OCI SDK](../../se/integrations/oci.md#oci-compatibility) for detailed information on how to work around this issue.

- The MultiPart buffered readers have been deprecated ([See PR](https://github.com/oracle/helidon/pull/4096)). Use the MultiPart stream readers instead.

### Helidon Common

Deprecations in the following classes:

- `Resource` - old configuration approach (since 2.0)
- `ThreadPoolSupplier` - Named thread pools (since 2.4.2)

More information in the following [Task](https://github.com/oracle/helidon/issues/4363).

### Media Common

Deprecations in the following classes:

- `ContentReaders` - Methods with alternatives (since 2.0)
- `ContentTypeCharset` - Class with alternative (since 2.0)
- `ContentWriters` - Methods with alternatives (since 2.0)
- `MessageBodyReaderContext` - Methods with alternatives (since 2.0)
- `MessageBodyWriterContext` - Methods with alternatives (since 2.0)
- `ReadableByteChannelPublisher` - Class with alternative (since 2.0)

More information in the following [Task](https://github.com/oracle/helidon/issues/4364).

### Metrics

Deprecations in the following classes:

- `MetricsSupport` - 3 methods, replacing Config with metrics settings
- `KeyPerformanceIndicatorMetricsSettings` - New class in metrics API, for backward compatibility only
- `RegistryFactory` - New class in metrics API, for backward compatibility only

More information in the following [Task](https://github.com/oracle/helidon/issues/4365).

### Common Context

Deprecations in the following class:

- `DataPropagationProvider` - clearData should use new method

More information in the following [Task](https://github.com/oracle/helidon/issues/4366).

### GRPC core

Deprecations:

- `JavaMarshaller` - removed support for JavaMarshaller

More information in the following [Task](https://github.com/oracle/helidon/issues/4367).

gRPC scope is temporarily smaller in Helidon, please follow issue <https://github.com/helidon-io/helidon/issues/5418>

### LRA

Deprecations in the following class:

- `CoordinatorClient` - multiple methods
- `Headers`

More information in the following [Task](https://github.com/oracle/helidon/issues/4368).

### MP Messaging

Deprecations in the following class:

- `MessagingCdiExtension` - Alternative methods used

More information in the following [Task](https://github.com/oracle/helidon/issues/4369).

### JWT

Deprecations in the following class:

- `Jwt` - Audience can be a list (since 2.4.0)

More information in the following [Task](https://github.com/oracle/helidon/issues/4370).

### MP Metrics

Deprecations in the following class:

- `MetricUtil` - multiple methods
- `MetricsCdiExtension` - multiple methods

More information in the following [Task](https://github.com/oracle/helidon/issues/4371).

### HTTP Signature Security Provider

- `backwardCompatibleEol` - set to false

More information in the following [Task](https://github.com/oracle/helidon/issues/4372).

### Service Common

Deprecations in the following class:

- `HelidonRestServiceSupport` - method *configureEndpoint(Rules)*

More information in the following [Task](https://github.com/oracle/helidon/issues/4371).

### WebServer

- `Static content support` in `WebServer` - moved to a separate module. Fully removed from `WebServer` module.

More information in the following [Task](https://github.com/oracle/helidon/issues/4374).
