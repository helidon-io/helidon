# io.helidon.webserver.spi.ServerFeature

## Description

This type is a provider contract.

## Usages

- [`server.features`](io_helidon_webserver_WebServer.md#ae9df6-features)

## Implementations

| Key | Type | Description |
|----|----|----|
| <span id="ae442f-eureka"></span> [`eureka`](io_helidon_integrations_eureka_EurekaRegistrationServerFeature.md) | `i.h.i.e.EurekaRegistrationServerFeature` | A `Prototype.Api prototype` for `EurekaRegistrationServerFeature` `io.helidon.builder.api.RuntimeType.Api runtime type` instances |
| <span id="a582c4-openapi"></span> [`openapi`](io_helidon_openapi_OpenApiFeature.md) | `i.h.o.OpenApiFeature` | `OpenApiFeature` prototype |
| <span id="a42c97-access-log"></span> [`access-log`](io_helidon_webserver_accesslog_AccessLogFeature.md) | `i.h.w.a.AccessLogFeature` | Configuration of access log feature |
| <span id="ab4b8f-limits"></span> [`limits`](io_helidon_webserver_concurrency_limits_LimitsFeature.md) | `i.h.w.c.l.LimitsFeature` | Server feature that adds limits as filters |
| <span id="a57af2-context"></span> [`context`](io_helidon_webserver_context_ContextFeature.md) | `i.h.w.c.ContextFeature` | Configuration of context feature |
| <span id="a9ee5f-cors"></span> [`cors`](io_helidon_webserver_cors_CorsFeature.md) | `i.h.w.c.CorsFeature` | Configuration of CORS feature |
| <span id="af2058-grpc-reflection"></span> [`grpc-reflection`](io_helidon_webserver_grpc_GrpcReflectionFeature.md) | `i.h.w.g.GrpcReflectionFeature` | Configuration of gRPC Reflection feature |
| <span id="a03f1c-observe"></span> [`observe`](io_helidon_webserver_observe_ObserveFeature.md) | `i.h.w.o.ObserveFeature` | Configuration for observability feature itself |
| <span id="ad5f1a-security"></span> [`security`](io_helidon_webserver_security_SecurityFeature.md) | `i.h.w.s.SecurityFeature` | Configuration of security feature fow webserver |
| <span id="a2c874-static-content"></span> [`static-content`](io_helidon_webserver_staticcontent_StaticContentFeature.md) | `i.h.w.s.StaticContentFeature` | Configuration of Static content feature |

See the [manifest](../config/manifest.md) for all available types.
