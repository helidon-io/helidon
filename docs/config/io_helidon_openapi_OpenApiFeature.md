# io.helidon.openapi.OpenApiFeature

## Description

OpenApiFeature

prototype.

## Usages

- [`openapi`](../config/config_reference.md#a937cb-openapi)
- [`server.features.openapi`](../config/io_helidon_webserver_spi_ServerFeature.md#a582c4-openapi)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9052a-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Sets whether the feature should be enabled |
| <span id="a2bdbe-manager"></span> [`manager`](../config/io_helidon_openapi_OpenApiManager.md) | `VALUE` | `i.h.o.OpenApiManager` |   | OpenAPI manager |
| <span id="a8b0d5-manager-discover-services"></span> `manager-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `manager` |
| <span id="a99850-permit-all"></span> `permit-all` | `VALUE` | `Boolean` | `true` | Whether to allow anybody to access the endpoint |
| <span id="ad5281-roles"></span> `roles` | `LIST` | `String` | `openapi` | Hints for role names the user is expected to be in |
| <span id="a8653c-services"></span> [`services`](../config/io_helidon_openapi_OpenApiService.md) | `LIST` | `i.h.o.OpenApiService` |   | OpenAPI services |
| <span id="ae938a-services-discover-services"></span> `services-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `services` |
| <span id="a47a74-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a0169c-static-file"></span> `static-file` | `VALUE` | `String` |   | Path of the static OpenAPI document file |
| <span id="ac378f-web-context"></span> `web-context` | `VALUE` | `String` | `/openapi` | Web context path for the OpenAPI endpoint |
| <span id="adb903-weight"></span> `weight` | `VALUE` | `Double` | `90.0` | Weight of the OpenAPI feature |

### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="ab0d30-cors"></span> [`cors`](../config/io_helidon_cors_CrossOriginConfig.md) | `VALUE` | `i.h.c.CrossOriginConfig` | CORS config |

See the [manifest](../config/manifest.md) for all available types.
