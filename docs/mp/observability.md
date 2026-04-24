# Observability

## Overview

In Helidon 4 all observability features were moved to one logical module: `observe`. Observability features specified by MicroProfile—​such as metrics and health—​keep their familiar endpoints. The endpoints for other observability features are grouped together under a single context root which defaults to `/observe`.

## Maven Coordinates

You do not need to explicitly add any observability dependency in your Helidon MP project `pom.xml` file for MicroProfile technologies. Adding a dependency on Helidon’s MP metrics or health component, for example, brings along the necessary observability components automatically.

To include other observability features in your Helidon MP application, add one or more of the following dependencies.

For Info Observability features:

```xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-info</artifactId>
</dependency>
```

For Logging Observability features:

```xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-log</artifactId>
</dependency>
```

For Configuration Observability features:

```xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-config</artifactId>
</dependency>
```

## Usage

The MicroProfile observability features use top-level endpoints (such as `/health` and `/metrics`) which you can customize if you wish. See the [configuration](#configuration) section below for more information.

Other observability features add endpoints under the `/observe` path

### Feature Weight and Endpoint Conflicts

In some ways Helidon treats all types of observers as a single observability *feature*. In particular, you can use configuration to control the *weight* of the various Helidon features, and the weight prescribes the order in which Helidon handles routing for those features.

The Helidon-provided feature for processing your application endpoints has weight 100 by default, and the observability feature has default weight 80. This means that Helidon normally prioritizes routing for your application endpoints over the endpoints for the observers such as metrics and health.

This can have unexpected results if your application declares a resource path `/{name}`. Because Helidon normally prioritizes the routing of *your* endpoints, Helidon routes requests for `/metrics` and `/health` to *your* `/{name}` endpoint instead of to the actual metrics and health endpoints.

One way to avoid this is to assign a weight from 101 to 200 to the observe feature in your configuration. Then Helidon prioritizes the routing of the observe feature ahead of routing your application endpoints.

*Configuration in `META-INF/microprofile-config.properties` Assigning Feature Weight to Control Routing*

```properties
server.features.observe.weight = 120
```

Helidon does not enforce the weight range 101-200 for observability, but you should use a value in this range for the observe weight to avoid problems with other features such as security, CORS, and others; their relative ordering is important.

### Endpoints

Some observer endpoints—​metrics and health—​were present in earlier releases of Helidon MP. By default those continue to use their customary paths (`/metrics`, `/health`). You can customize the endpoint for each of those observers are described in the documentation for each observer.

Other observers have no counterpart in the MicroProfile spec and they respond by default at subpaths of `/observe` as described below.

#### Configuration Observability

Configuration observability allows reading the current application configuration values. Configuration observability defines the following endpoints:

| Endpoint | Method | Action |
|----|----|----|
| `/config/profile` | `GET` | Returns the current configuration profile |
| `/config/values` | `GET` | Returns the current configuration values |
| `/config/values/{name}` | `GET` | Returns specified by `name` configuration value |

> [!NOTE]
> All secrets and passwords are obfuscated with "\*" characters.

#### Health Observability

Health observability allows reading application readiness to serve requests, whether the services are alive. Health observability defines the following endpoints:

| Endpoint | Method | Action |
|----|----|----|
| `/health/ready` | `GET` | Returns Service Readiness |
| `/health/live` | `GET` | Returns whether the service is alive |
| `/health/started` | `GET` | Returns whether the service is started |
| `/health/ready/{name}` | `GET` | Returns Service `name` Readiness |
| `/health/live/{name}` | `GET` | Returns whether the service `name` is alive |
| `/health/started/{name}` | `GET` | Returns whether the service `name` is started |
| `/health/check/{name}` | `GET` | Returns all checks for service `name` |
| `/health/ready` | `HEAD` | Returns Service Readiness without details |
| `/health/live` | `HEAD` | Returns whether the service is alive without details |
| `/health/started` | `HEAD` | Returns whether the service is started without details |
| `/health/ready/{name}` | `HEAD` | Returns Service `name` Readiness without details |
| `/health/live/{name}` | `HEAD` | Returns whether the service `name` is alive without details |
| `/health/started/{name}` | `HEAD` | Returns whether the service `name` is started without details |
| `/health/check/{name}` | `HEAD` | Returns all checks for service `name` without details |

For more information, please, check [Health](../se/health.md) documentation.

#### Information Observability

Info observability allows configuration of custom properties to be available to users. Information observability defines the following endpoints:

| Endpoint | Method | Action |
|----|----|----|
| `/info` | `GET` | Returns the Application information |
| `/info/{name}` | `GET` | Returns the Application information for the specified `name` |

#### Logger Observability

Log observability allows reading and configuring of log levels of various loggers and reading log messages. Logger Observability defines the following endpoints:

| Endpoint                    | Method   | Action                              |
|-----------------------------|----------|-------------------------------------|
| `/log`                      | `GET`    | Stream logs (if enabled)            |
| `/log/loggers`              | `GET`    | Returns all logger handlers         |
| `/log/log/loggers/{logger}` | `GET`    | Returns the Logger by name `logger` |
| `/log/loggers/{logger}`     | `POST`   | Set Logger level by name `logger`   |
| `/log/loggers/{logger}`     | `DELETE` | Unset the specified logger `logger` |

#### Metrics Observability

Helidon distinguishes among three general *types*, or scopes, of metrics.

| Type/scope | Typical Usage |
|----|----|
| base | OS or Java runtime measurements (available heap, disk space, etc.). |
| vendor | Implemented by vendors, including the `REST.request` metrics and other key performance indicator measurements. |
| application | Declared via annotations or programmatically registered by your service code. |

Types (scopes) of metrics

When you add the metrics dependency to your project, Helidon automatically provides a built-in REST endpoint `/observe/metrics` which responds with a report of the registered metrics and their values.

Clients can request a particular output format.

| Format                   | Requested by                      |
|--------------------------|-----------------------------------|
| OpenMetrics (Prometheus) | default (`text/plain`)            |
| JSON                     | Header `Accept: application/json` |

Formats for `/observe/metrics` output

Clients can also limit the report by appending the metric type to the path:

- `/observe/metrics/base`
- `/observe/metrics/vendor`
- `/observe/metrics/application`

For more information see [Metrics](../se/metrics/metrics.md) documentation.

## Configuration

To customize the endpoint of an observer:

1.  For MicroProfile technologies (metrics, health) refer to the Helidon MP documentation for them:
    - [metrics config](../mp/metrics/metrics.md#config-intro) documentation
    - [health config](../mp/health.md#_configuration) documentation
2.  For other observers, assign a custom endpoint using a config setting such as `server.features.observe.info.endpoint`.

To control the observability features as a whole, add config settings under `server.features.observe`.

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a42c15-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the observe support is enabled |
| <span id="a0d210-endpoint"></span> `endpoint` | `VALUE` | `String` | `/observe` | Root endpoint to use for observe providers |
| <span id="a837f0-observers"></span> [`observers`](../config/io_helidon_webserver_observe_spi_Observer.md) | `LIST` | `i.h.w.o.s.Observer` |   | Observers to use with this observe features |
| <span id="a1e406-observers-discover-services"></span> `observers-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `observers` |
| <span id="ace9c3-sockets"></span> `sockets` | `LIST` | `String` |   | Sockets the observability endpoint should be exposed on |
| <span id="a1bc81-weight"></span> `weight` | `VALUE` | `Double` | `80.0` | Change the weight of this feature |

#### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a25a02-cors"></span> [`cors`](../config/io_helidon_cors_CrossOriginConfig.md) | `VALUE` | `i.h.c.CrossOriginConfig` | Cors support inherited by each observe provider, unless explicitly configured |

## Additional Information

The Observability features are now implemented with `HttpFeature` and can be registered with `HttpRouting.Builder#addFeature(java.util.function.Supplier)`. Such a feature encapsulates a set of endpoints, services and/or filters.

Feature is similar to `HttpService` but gives more freedom in setup. Main difference is that a feature can add `Filter` filters and it cannot be registered on a path (that is left to the discretion of the feature developer).

- Features are not registered immediately - each feature can define a `Weight` or implement `Weighted` to order features according to their weight. Higher weighted features are registered first.
- This is to allow ordering of features in a meaningful way (e.g. Context should be first, Tracing second, Security third etc).

## Reference

- [MicroProfile Metrics Specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.0.0/microprofile-metrics-spec-5.0.0.pdf)
- [Metrics](../se/metrics/metrics.md) documentation.
- [Health](../se/health.md) documentation.
