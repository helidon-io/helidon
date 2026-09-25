<!--@frontmatter
description: "Helidon Metrics"
-->
# Metrics

## Overview

Helidon metrics is a neutral metrics API which provides

- a unified way for Helidon servers to export monitoring data—telemetry—to
  management agents, and
- a unified Java API which all application programmers can use to register and
  update meters to expose telemetry data from their services.

Metrics is one of the Helidon observability features.

### Terminology

Helidon uses the term "metrics" to refer to the subsystem in Helidon which
manages the registration of, updates to, and reporting of aggregate statistical
measurements about the service. The term "meter" refers to an entity which
collects these measurements, such as a counter or a timer.

## Maven Coordinates

To use the metrics API, add the following dependency to your project’s `pom.xml`
(see [Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics</groupId>
  <artifactId>helidon-metrics-api</artifactId>
</dependency>
```

This dependency adds the metrics API and a no-op implementation of that API to
your project. The no-op implementation:

- does not register meters in a registry
- does not update meter values
- does not expose the metrics endpoint for reporting meter values.

To expose the metrics endpoint, add the metrics observer dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-metrics</artifactId>
</dependency>
```

Neither `helidon-metrics-api`, `helidon-metrics`, nor
`helidon-webserver-observe-metrics` selects a metrics provider. Add one of the
following providers explicitly to record and report metrics. Without a provider,
the metrics API uses its no-op implementation.

The Helidon provider implements the metrics API without Micrometer and supports
Prometheus/OpenMetrics and JSON endpoint output:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics.providers</groupId>
  <artifactId>helidon-metrics-providers-helidon</artifactId>
  <scope>runtime</scope>
</dependency>
```

Alternatively, choose the Micrometer provider for direct Micrometer integration
or Micrometer-specific publisher settings:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics.providers</groupId>
  <artifactId>helidon-metrics-providers-micrometer</artifactId>
  <scope>runtime</scope>
</dependency>
```

No dependency exclusions are needed to choose a provider. If both providers are
present, the Micrometer provider takes precedence. To publish OTLP metrics using
the Helidon provider, add the [native OTLP publisher](#native-otlp-publisher).

Helidon provides several built-in meters in a separate artifact. To include the
build-in meters, add the following dependency to your project:

Packaging the built-in meters:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics</groupId>
  <artifactId>helidon-metrics-system-meters</artifactId>
  <scope>runtime</scope>
</dependency>
```

## Instrumenting Your Service

You add meters to your service by writing code which explicitly invokes the
metrics API to register meters, retrieve previously-registered meters, and
update meter values.

Later sections of this document describe how to do this.

## Meter Types

Helidon supports meters inspired by [Micrometer](https://micrometer.io) and
summarized in the following table:

| Meter Type                                    | Description                                                                                                                                                                                                                                                                               | Micrometer reference                       |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| [`Counter`][counter]                          | Monotonically-increasing `long` value.                                                                                                                                                                                                                                                    | [Counters][counters]                       |
| [`DistributionSummary`][distributionsumm]     | Summary of samples each with a `long` value. Reports aggregate information over all samples (count, total, mean, max) as well as the distribution of sample values using percentiles and bucket counts.                                                                                   | [Distribution summaries][distribution-sum] |
| [`Timer`][timer]                              | Accumulation of short-duration (typically under a minute) intervals. Typically updated using a Java [`Duration`][duration] or by recording the time taken by a method invocation or lambda. Reports the count, total time, max, and mean; provides a distribution summary of the samples. | [Timers][timers]                           |
| [`Gauge<? extends Number>`][gauge-extends-nu] | View of a value that is assignment-compatible with a subtype of Java [`Number`][number]. The underlying value is updated by code elsewhere in the system, not by invoking methods on the gauge itself.                                                                                    | [Gauges][gauges]                           |

Types of Meters

## Meter Registry

Helidon stores all meters in a *meter registry*. Typically, applications use the
global meter registry which is the registry where Helidon stores built-in
meters. Application code obtains the global registry by injecting
`MeterRegistry` or, for imperative code, using
`Services.get(MeterRegistry.class)`.

## Publishing Metrics

Helidon can make metrics data available to external systems in these ways:

- Pulling data from the metrics endpoint using Prometheus/OpenMetrics or JSON.
- Publishing OTLP (OpenTelemetry Protocol) metrics from the Helidon provider
  using the optional native OTLP publisher.
- Publishing data through Micrometer-backed publishers when the optional
  Micrometer provider is present.

The Helidon provider supports metrics endpoint output without a publisher.
Adding the native OTLP publisher also enables periodic export; the
Prometheus/OpenMetrics and JSON endpoints remain available.

For an application using `helidon-webserver-observe-metrics`, choose the
additional dependency according to the publishing behavior you need:

| Behavior | Additional dependency |
|----------|-----------------------|
| Prometheus/OpenMetrics and JSON endpoint only | `io.helidon.metrics.providers:helidon-metrics-providers-helidon` |
| OTLP over HTTP/JSON, with the Helidon provider | `io.helidon.metrics.providers:helidon-metrics-providers-helidon` and `io.helidon.metrics.publishers:helidon-metrics-publishers-otlp` |
| Direct Micrometer integration or Micrometer publisher settings | `io.helidon.metrics.providers:helidon-metrics-providers-micrometer` |

If both providers are present, Helidon selects the Micrometer metrics
provider and its `otlp` publisher. The two OTLP publishers have different
configuration options: the native publisher uses `endpoint`, while Micrometer
uses `url`.

> [!NOTE]
> The configuration of metrics publishers as described below is an
> [`@Features.Preview`][preview-feature] feature which Helidon intends to keep,
> but its external interface or behavior might evolve between dot releases.

Configure publishers in the `publishers` section under the top-level `metrics`
node or under `server.features.observe.observers.metrics`. Configure an `otlp`
entry explicitly to enable native OTLP export; adding the module alone does not
start exporting.

If you do not set up publishers explicitly, the Micrometer provider uses an
inferred Prometheus publisher for backward compatibility. See
[this later section][this-later-secti] for details.

### Micrometer Publishing

Publishers in Helidon’s Micrometer-based metrics implementation use Micrometer
`MeterRegistry` implementations. Each Helidon meter registry owns a composite
Micrometer registry containing one registry for each enabled publisher. This has
these important effects:

- Meters which Helidon or your code registers in a Helidon meter registry are
  registered in all active publisher registries owned by that registry.
- Each Helidon meter has an implementation in every active publisher registry
  belonging to its Helidon registry.
- When Helidon or your code updates a Helidon meter, Micrometer applies the
  change to every corresponding meter in the publisher registries belonging to
  that Helidon registry.

As a result, configuring more than one active publisher for a Helidon meter
registry can affect performance.

> [!NOTE]
> When using the Micrometer provider, make sure at least one publisher
> configured for each Helidon meter registry is enabled. Otherwise, that
> Helidon registry has no active publisher registries, its registered metrics
> are no-ops, and Helidon logs a warning when the registry is created.

## OpenTelemetry Protocol

Helidon provides a native OTLP publisher for the Helidon provider and an OTLP
publisher backed by Micrometer. Both export metrics periodically to a configured
backend.

### Native OTLP Publisher

Add the native publisher dependency alongside the metrics observer and Helidon
provider dependencies shown above:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics.publishers</groupId>
  <artifactId>helidon-metrics-publishers-otlp</artifactId>
  <scope>runtime</scope>
</dependency>
```

This publisher uses OTLP over HTTP with JSON encoding. It does not require
Micrometer or the OpenTelemetry SDK. The receiving collector or backend must
accept OTLP/HTTP JSON metrics at the configured URL.

For a local collector listening on port 4318, the following configuration
exports every 30 seconds. The custom header is optional.

```yaml [application.yaml]
metrics:
  publishers:
    otlp:
      enabled: true
      endpoint: 'http://localhost:4318/v1/metrics'
      interval: PT30S
      timeout: PT10S
      max-request-size: 64 MiB
      service-name: greeting-service
      resource-attributes:
        deployment.environment.name: development
      headers:
        X-Application: greeting-service
```

The endpoint is the complete metrics URL; Helidon does not append `/v1/metrics`.
Its default is `http://localhost:4318/v1/metrics`. The default export interval is
`PT60S`, and the default request timeout is `PT10S`. `service-name` defaults to
`unknown_service`; a `service.name` entry in `resource-attributes` overrides it.
Set these options through Helidon configuration. The publisher does not apply
OpenTelemetry SDK environment-variable autoconfiguration.

`max-request-size` limits the uncompressed JSON request body, measured in UTF-8
encoded bytes, and defaults to `64 MiB`. The size must be positive and at most
2,147,483,647 bytes. Requests above the limit are discarded with a warning before
sending an HTTP request. This applies to periodic and final shutdown exports.
Later exports resume when the collected metrics fit within the limit. The limit
does not bound the memory used to collect metrics.

Counters and functional counters export as cumulative sums, gauges as gauges,
and timers and distribution summaries as cumulative histograms. Timer values and
bucket boundaries use seconds. The native publisher does not support OTLP/gRPC,
delta temporality, or trace exemplars.

Each meter registry owns its publisher sessions. The first export occurs after
one interval. Closing the registry stops periodic publishing and performs a
final cumulative export bounded by the configured timeout. Helidon manages the
service-owned registry; application code must close registries it creates when
they are no longer in use. Setting `enabled: false` prevents the publisher from
starting its client and scheduler.

With the local example, update an application meter and wait one export interval
to check the receiving collector for resource `service.name=greeting-service`.
The metrics observer continues to serve `/observe/metrics` while OTLP publishing
is enabled; no `prometheus` publisher entry is needed with Helidon.

#### Configuration Options

<!--@include ../../config/io.helidon.metrics.publishers.otlp.OtlpPublisher.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [native OTLP configuration options](../../config/io.helidon.metrics.publishers.otlp.OtlpPublisher.md#configuration-options).
<!--/include-->

### Micrometer OTLP Publisher

With the Micrometer provider, configure its OTLP publisher using the settings
below. These settings mirror Micrometer's `OtlpMeterRegistry`.

#### Configuration Options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-metri].
<!--/include-->

The following example sets up the Micrometer OTLP publisher to transmit metrics
data every 30 seconds.

Example OTLP publisher settings:

<!--@mdc ::code-callout -->
```yaml
metrics:
  publishers:         # <1>
    otlp:  # <2>
      interval: PT30S
      url: 'http://somehost.com:4318/v1/metrics'
```
1. Introduces the configured publishers.
2. Configures an OTLP publisher to transmit every 30 seconds to the given
   endpoint.
<!--@mdc :: -->

## Prometheus Publisher

If you configure a Micrometer Prometheus publisher or rely on the inferred one,
the Micrometer provider can make metrics data available in the
Prometheus/OpenMetrics format. This publisher is separate from the endpoint
formatting provided by the Helidon provider. To serve the data at the metrics
endpoint, your project must also depend on the Helidon metrics observer component.

### Configuration options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-metri-2].
<!--/include-->

### Inferred Publisher

As described earlier, the Micrometer provider prepares an inferred Prometheus
publisher if you do not set up any publishers.

Note that Helidon uses the inferred publisher *only* if you add *no* publishers
explicitly, either in the configuration or programmatically. If you specify any
publishers explicitly, Helidon uses only the ones you set up.

In particular, Helidon *does not* use the inferred Prometheus publisher if you
create a `metrics.publishers` section containing only an OTLP publisher.

With only an OTLP publisher, the Micrometer provider does not expose
Prometheus/OpenMetrics output at the metrics endpoint. JSON output remains
available. Add an explicit `prometheus` publisher when you need both OTLP
publishing and Prometheus/OpenMetrics endpoint output.

You can configure other publishers and still have Helidon use the default one by
simply adding the `prometheus` publisher entry. You do not need to specify
further settings for it.

Using an OTLP publisher **and** the default Prometheus publisher:

```yaml [application.yaml]
metrics:
  publishers:
    prometheus:
    otlp:
      interval: PT20S
```

## Metrics Endpoint

When you add the `helidon-webserver-observe-metrics` dependency and a metrics
provider to your project, Helidon provides a built-in REST endpoint
`/observe/metrics` which responds with a report of the registered meters and
their values.

The Helidon provider supports Prometheus/OpenMetrics text output for this
endpoint without a Micrometer publisher.

Clients can request a particular output format from the endpoint.

Formats for `/observe/metrics` output:

| Format                   | Requested by                      |
|--------------------------|-----------------------------------|
| OpenMetrics (Prometheus) | default (`text/plain`)            |
| JSON                     | Header `Accept: application/json` |

Clients can narrow the report to a specific meter name using the `name` query
parameter, such as `/observe/metrics?name=myCount`.

Example Reporting: Prometheus format:

```shell [Terminal]
curl -s -H 'Accept: text/plain' -X GET http://localhost:8080/observe/metrics
```

```text
# HELP classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
# TYPE classloader_loadedClasses_count gauge
classloader_loadedClasses_count 5297.0
```

See the summary of the [OpenMetrics and Prometheus Format](#format) for more
information.

Example Reporting: JSON format:

```shell [Terminal]
curl -s -H 'Accept: application/json' -X GET http://localhost:8080/observe/metrics
```

```json [Response]
{
  "memory.maxHeap" : 3817865216,
  "memory.committedHeap" : 335544320
}
```

In addition to your application meters, the reports contain other meters of
interest such as system and VM information.

### Format

The [OpenMetrics format][openmetrics-form] and the [Prometheus exposition
format][prometheus-expos] are very similar in most important respects but are
not identical. This brief summary treats them as the same.

The OpenMetrics/Prometheus format represents each meter using three lines of
output as summarized in the following table.

| Line prefix | Purpose                                              | Format                                        |
|-------------|------------------------------------------------------|-----------------------------------------------|
| `# TYPE`    | Displays the name and type of the meter              | `TYPE <output-name> <meter-type>`             |
| `# HELP`    | Displays the name and description of the meter       | `HELP <output-name> <registered description>` |
| (none)      | Displays the meter ID and current value of the meter | `<output-name> <current value>`               |

The OpenMetrics/Prometheus output converts meter IDs in these ways:

- Names in camel case are converted to "snake case" and dots are converted to
  underscores.
- Names include any units specified for the meter.
- For percentiles, the ID includes a tag identifying which percentile the line
  of output describes.

As the earlier example output showed, for a meter with multiple values, the
OpenMetrics/Prometheus output reports a "metric family" which includes a
separate family member meter for each of the multiple values.

The name for each member in the family is derived from the registered name for
the meter plus a suffix indicating which one of the meter’s multiple values the
line refers to.

The following table summarizes the naming for each meter type:

<table>
<thead>
<tr>
<th>Meter Type</th>
<th>Example registered name</th>
<th>Meter family member</th>
<th>Name Suffix</th>
<th>Example displayed name</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>Counter</code></td>
<td><code>requests.<wbr>count</code></td>
<td>count</td>
<td><code>_total</code></td>
<td><code>requests_<wbr>count_<wbr>total</code></td>
</tr>
<tr>
<td rowspan="4"><code>Distribution<wbr>Summary</code></td>
<td rowspan="4"><code>nameLengths</code></td>
<td>count</td>
<td><code>_count</code></td>
<td><code>name<wbr>Lengths_<wbr>count</code></td>
</tr>
<tr>
<td>sum</td>
<td><code>_sum</code></td>
<td><code>nameLengths_<wbr>sum</code></td>
</tr>
<tr>
<td>max</td>
<td><code>_max</code></td>
<td><code>nameLengths_<wbr>max</code></td>
</tr>
<tr>
<td>percentile</td>
<td>none</td>
<td><code>nameLengths{<wbr>quantile="0.5",<wbr>}</code></td>
</tr>
<tr>
<td><code>Gauge</code></td>
<td><code>classloader.<wbr>loadedClasses.<wbr>count</code></td>
<td>value</td>
<td>none</td>
<td><code>classloader_<wbr>loadedClasses_<wbr>count</code></td>
</tr>
<tr>
<td rowspan="4"><code>Timer</code> <sup>1</sup></td>
<td rowspan="4"><code>vthreads.<wbr>recentPinned</code></td>
<td>count</td>
<td><code>_count</code></td>
<td><code>vthreads_<wbr>recentPinned_<wbr>seconds_<wbr>count</code></td>
</tr>
<tr>
<td>sum</td>
<td><code>_sum</code></td>
<td><code>vthreads_<wbr>recentPinned_<wbr>seconds_<wbr>sum</code></td>
</tr>
<tr>
<td>max</td>
<td><code>_max</code></td>
<td><code>vthreads_<wbr>recentPinned_<wbr>seconds_<wbr>max</code></td>
</tr>
<tr>
<td>percentile</td>
<td>none</td>
<td><code>vthreads_<wbr>recentPinned_<wbr>seconds{<wbr>quantile="0.5",<wbr>}</code></td>
</tr>
</tbody>
</table>

<sup>1</sup> The OpenMetrics/Prometheus output format reports a timer as a `summary` with units of `seconds`.

#### JSON Format

Unlike OpenMetrics/Prometheus output, which combines the data and the metadata
in a single response, you use an HTTP `GET` request to retrieve metrics JSON
*data* and an `OPTIONS` request to retrieve *metadata* in JSON format.

Helidon reports meters directly in a single JSON object.

JSON metrics metadata output (partial):

```json
{
  "getTimer": {
    "type": "timer",
    "unit": "seconds",
    "description": "Timer for getting the default greeting"
  },
  "requests.count": {
    "type": "counter",
    "description": "Each request (regardless of HTTP method) will increase this counter"
  },
  "cpu.systemLoadAverage": {
    "type": "gauge",
    "description": "Displays the system load average for the last minute."
  },
  "classloader.loadedClasses.count": {
    "type": "gauge",
    "description": "Displays the number of classes that are currently loaded in the Java virtual machine."
  }
}
```

#### Understanding the JSON Metrics Data Format

The Helidon JSON format expresses each meter as either a single value (for
example, a counter) or a structure with multiple values (for example, a timer).

JSON output for a single-valued meter (for example, Counter):

```json
"requests.count": 5
```

JSON output for a multivalued meter (for example, Timer):

```json
"getTimer": {
  "count": 3,
  "max": 0.0030455,
  "mean": 0.0011060836666666666,
  "elapsedTime": 0.003318251,
  "p0.5": 0.000151552,
  "p0.75": 0.003141632,
  "p0.95": 0.003141632,
  "p0.98": 0.003141632,
  "p0.99": 0.003141632,
  "p0.999": 0.003141632
}
```

By default, Helidon formats time values contained in JSON output as seconds. You
can change this behavior [as described below](#controlling-json-timer-output).

#### Understanding the JSON Metrics Metadata Format

Access the metrics endpoint with an HTTP `OPTIONS` request and the `Accept:
application/json` header to retrieve metadata in JSON format.

Example Counter metadata:

```json
"requests.count": {
  "type": "counter",
  "description": "Each request (regardless of HTTP method) will increase this counter"
    }
```

Example Timer metadata:

```json
"getTimer": {
  "type": "timer",
  "unit": "seconds",
  "description": "Timer for getting the default greeting"
}
```

Generally, the output for a given meter reflects only the metadata that the
application or Helidon code explicitly set on that meter.

One exception is that metadata for a timer always includes the `unit` field. By
default, Helidon formats timer data in JSON output as seconds, regardless of any
explicit `baseUnit` setting applied to the timers. But as [described
below](#controlling-json-timer-output) you can change this behavior which can
lead to different timers being formatted using different units. Checking the
metadata is the only way to know for sure what units Helidon used to express a
given timer, so Helidon always includes `unit` in timer metadata.

#### Controlling JSON Timer Output

By default, Helidon expresses timer data as seconds.

You can change this using configuration:

Setting default timer units for JSON in `application.yaml`:

```yaml [application.yaml]
metrics:
  timers:
    json-units-default: units
```

- For *units* specify any valid name for a [`TimeUnit`][timeunit] value
  (`SECONDS`, `MILLISECONDS`, etc.)

If you have configured `json-units-default`, Helidon formats each timer’s data
as follows:

1.  If code set `baseUnit` on the timer, Helidon uses those units for that
    timer.
2.  Otherwise, Helidon uses the default units you configured.

To enable the JSON output behavior from Helidon 3, specify `json-units-default`
as `NANOSECONDS`.

### Enabling the Metrics REST Service

If you add the dependencies described above, your service automatically supports
the metrics REST endpoint as long as the `WebServer` is configured to discover
features automatically.

If you disable auto-discovery, you can add the metrics observer explicitly.

1.  Create an instance of `MetricsObserver`, either directly as shown below or
    using its builder.
2.  Include the `MetricsObserver` instance in your application’s
    `ObserveFeature`.
3.  Register your `ObserveFeature` with your `WebServer`.

```java
ObserveFeature observe = ObserveFeature.builder()
        .config(config.get("server.features.observe"))
        .addObserver(MetricsObserver.create())
        .build();

WebServer server = WebServer.builder()
        .config(Config.global().get("server"))
        .featuresDiscoverServices(false)
        .addFeature(observe)
        .routing(Main::routing)
        .build()
        .start();
```

## API

To work with Helidon Metrics in your code, follow these steps:

1.  Use injection or `Services.get(MeterRegistry.class)` to get a reference to
    the global [`MeterRegistry`][meterregistry] instance.
2.  Use the `MeterRegistry` instance and builders from its
    [`MetricsFactory`][metricsfactory] to register new meters and look up
    previously registered meters.
3.  Use the meter reference returned from the `MeterRegistry` to update the
    meter or get its value.

You can also use the `MeterRegistry` to remove an existing meter.

Helidon 27 removes the former static convenience methods from `Metrics`. Use
these replacements instead:

- Replace `Metrics.globalRegistry()` with injection of `MeterRegistry` or
  `Services.get(MeterRegistry.class)`.
- Replace `Metrics.createMeterRegistry()` with
  `Services.get(MetricsFactory.class).createMeterRegistry(MetricsConfig.create())`.
- Replace `Metrics.createMeterRegistry(metricsConfig)` with
  `Services.get(MetricsFactory.class).createMeterRegistry(metricsConfig)`.
- Replace `Metrics.getOrCreate(builder)` with
  `meterRegistry.getOrCreate(builder)`.
- Replace the `Metrics.getCounter`, `Metrics.getSummary`, `Metrics.getGauge`,
  `Metrics.getTimer`, and `Metrics.get` lookup helpers with the corresponding
  `MeterRegistry` lookup methods.
- Replace `Metrics.tag(key, value)` with
  `Services.get(MetricsFactory.class).tagCreate(key, value)`, and create
  multiple tags using the same `MetricsFactory` instance.

### Helidon Metrics API

The Helidon Metrics API defines the classes and interfaces for meter types and
other related items.

The following table summarizes the meter types.

| Meter Type                                | Usage                                                                           |
|-------------------------------------------|---------------------------------------------------------------------------------|
| [`Counter`][counter]                      | Monotonically increasing count of events.                                       |
| [`Gauge`][gauge-extends-nu]               | Access to a value managed by other code in the service.                         |
| [`DistributionSummary`][distributionsumm] | Calculates the distribution of a value.                                         |
| [`Timer`][timer]                          | Frequency of invocations and the distribution of how long the invocations take. |

Meter Types

Each meter type has its own set of methods for updating and retrieving the
value.

### `MeterRegistry`

To register or look up meters programmatically, your service code uses the
global `MeterRegistry`. Inject `MeterRegistry` into your service or invoke
`Services.get(MeterRegistry.class)` to get a reference to it.

To locate an existing meter or register a new one, your code:

1.  Obtains the registry-owned `MetricsFactory` using
    `meterRegistry.metricsFactory()` and creates a builder of the appropriate
    meter type, setting the name and possibly other characteristics.
2.  Invokes the `MeterRegistry.getOrCreate` method, passing the builder.

The meter registry returns a reference to a previously-registered meter with the
specified name and tags or, if none exists, a newly-registered meter. Your code
can then operate on the returned meter as needed to record new measurements or
retrieve existing data.

The example code in the [Examples](#examples) section below illustrates how to
register, retrieve, and update meters.

#### Understanding Timers, Units, and Output

Your application can assign the meter builder’s [`Meter.Builder
baseUnit`][meter-builder-ba] setting for any meter your application creates. In
particular, the [`Timer.Builder baseUnit`][timer-builder-ba] method allows code
to assign a `baseUnit` for a timer, passing a Java [`TimeUnit`][timeunit] value.
The timer builder also has the `String` variant of the `baseUnit` method and
enforces that the value corresponds (case-insensitively) to one of the
`TimeUnit` enum values.

Note that, regardless of the `baseUnit` setting for a `Timer`, by convention and
specification Prometheus output expresses time values in `seconds`.

By default, the same is true of Helidon’s JSON format: timer values are
displayed in `seconds` regardless of any timer’s `baseUnit` setting. You can
override this as described in the [Controlling Timer
Output](#controlling-json-timer-output) section, in which case the JSON output
for each timer reflects its `baseUnit` setting.

### Accessing the Underlying Implementation: `unwrap`

The neutral Helidon metrics API is an abstraction of common metrics behavior
independent of any given implementation. As such, we intentionally excluded some
implementation-specific behavior from the API.

Sometimes you might want access to methods that are present in a particular
metrics implementation but not in the Helidon API. Helidon allows that via the
`unwrap` method on the meter types and on their builders. Each full
implementation of the Helidon meter types and their builders refers to a
delegate meter or delegate builder internally. The `unwrap` method lets you
obtain the delegate, cast to the type you want.

Of course, using this technique binds your code to a particular metrics
implementation.

The [`Wrapper`][wrapper] interface declares the `unwrap` method which accepts a
class parameter to which the delegate is cast. You can then invoke any method
declared on the implementation-specific type.

## Configuration options

<!--@include ../../config/io.helidon.metrics.api.MetricsConfig.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-metri-3].
<!--/include-->

| Key                | Default Value |
|--------------------|---------------|
| `app-tag-name`     | `app`         |

## Metrics Configuration Migration

### Metrics Scopes

Metrics scopes were a MicroProfile-specific feature and are no longer part of
the Helidon core metrics model in Helidon 27. The scope APIs remain temporarily
for compatibility, are deprecated for removal, and have no effect. In
particular:

- meter and meter builder scope methods return no scope or ignore the provided
  scope;
- `metrics.scoping` configuration can still be parsed but does not affect
  meters or output;
- the `scope` query parameter is ignored; and
- the legacy `/observe/metrics/application`, `/observe/metrics/base`, and
  `/observe/metrics/vendor` paths expose the same unscoped metrics as
  `/observe/metrics`.

An integration which needs equivalent classification can implement it using
ordinary meter tags:

1. Choose an integration-owned tag name and values.
2. Provide a [`MeterBuilderCustomizer`][meter-builder-customizer] service.
   Override `customize(Meter.Builder)` and inspect `Meter.Builder.origin()` to
   select a tag value based on the fully-qualified name of the type which
   originated a meter. Helidon supplies the provider class name automatically
   for meters contributed by a `MetersProvider`.
3. Create the tag using `MetricsFactory.tagCreate` and add it to the builder
   before registration. Other meter producers can provide origin information
   using `Meter.Builder.origin(String)` and then register the builder using
   `MeterRegistry.getOrCreate(builder)`. Do not use the deprecated scope methods.
4. Translate any integration-specific selection into a tag-selection map and
   pass it to the provider-neutral
   [`MeterRegistryFormatterProvider.formatter`][formatter-provider] overload.
   The integration owns its routes, query parameters, and response semantics.

Helidon 27 always reports the system-provided `gc.time` meter as a `Gauge`.
The former `metrics.gc-time-type` compatibility setting is no longer
supported; remove it from configuration. Application code that looked up
`gc.time` as a `Counter` should use the `MeterRegistry` gauge lookup APIs
instead.

The deprecated `metrics.rest-request-enabled` compatibility setting is also
no longer supported in Helidon 27. Replace it with
`metrics.rest-request.enabled`.

The Helidon provider formats Prometheus/OpenMetrics output directly.
The Micrometer provider uses Prometheus Java Client 1.7.0 instead of
the legacy Prometheus simpleclient integration. Both providers preserve letter
case, replace unsupported characters with underscores, and avoid repeating
type suffixes. Reserved suffixes such as `_total`, `_created`, `_bucket`, and
`_info` are removed from counter and gauge base names so the formatter can add
type-appropriate suffixes.

With the Helidon provider, distinct metrics must not produce colliding
family or sample names after normalization, unit suffixes, and generated
suffixes such as `_total`, `_count`, `_sum`, `_bucket`, and `_max` are applied.
Helidon checks the enabled meters selected for each Prometheus/OpenMetrics
scrape and rejects a colliding scrape with a diagnostic identifying the
exported name and both conflicting meters. Tag variants of the same metric
remain valid.

For example, two counters without a base unit named `a.b` and `a_b` both
produce `a_b_total`, so a scrape selecting both fails. Rename one of the
metrics so their exported names remain distinct after these transformations.

To retain the Prometheus names emitted by earlier Helidon releases for counters,
functional counters, gauges, timers, and distribution summaries, add the
Micrometer provider and configure its legacy non-letter prefix:

```yaml [application.yaml]
metrics:
  publishers:
    prometheus:
      naming-convention:
        non-letter-prefix: "m_"
```

Setting `non-letter-prefix` selects legacy normalization as a whole, including
preserving user-supplied reserved suffixes. The value must match
`[A-Za-z][A-Za-z0-9_]*`; `m_` reproduces the earlier Helidon prefix, while
another valid value uses the same legacy rules with that prefix. The
`naming-convention.timer-suffix` setting remains available in both modes. The
publisher's existing `prefix` setting controls Micrometer property lookup and
does not prefix metric names.

The former `metrics.prometheus.histogramFlavor` property is accepted so existing
configuration continues to load, but the new integration ignores it and logs a
warning.

Code which accesses Prometheus-specific Helidon APIs or implements the exemplar
SPI needs these type changes:

- `PrometheusPublisher.prometheusRegistry()` now supplies
  `io.micrometer.prometheusmetrics.PrometheusMeterRegistry` instead of
  `io.micrometer.prometheus.PrometheusMeterRegistry`.
- `SpanContextSupplierProvider.get()` now returns
  `io.prometheus.metrics.tracer.common.SpanContext` instead of
  `io.prometheus.client.exemplars.tracer.common.SpanContextSupplier`; the new
  type reports trace ID, span ID, sampled state, and when the current span is
  selected as an exemplar.

## Metrics Observer

Helidon can make the registered meters and their current values available
externally at an endpoint (`/observe/metrics` by default). You can control
aspects of how Helidon furnishes this information under the
`server.features.observe.observers.metrics` configuration section.

| key        | type                                      | default value      | description                                             |
|------------|-------------------------------------------|--------------------|---------------------------------------------------------|
| `auto`     | [AutoHttpMetricsConfig][autohttpmetricsc] |                    | Automatic metrics collection settings.                  |
| `enabled`  | boolean                                   | `true`             | Whether this observer is enabled.                       |
| `endpoint` | string                                    | `/observe/metrics` | Path at which clients can retrieve metrics information. |

See the [Helidon OpenTelemetry documentation][helidon-opentele] for more
information.

#### Selecting REST Endpoints for Automatic Measurement

You can choose which endpoints to include in Helidon’s automatic measurements
using the `auto-http-metrics` config section.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][auto-http-metrics].
<!--/include-->


The `paths` section contains zero or more entries, each entry having the
following settings:

<!--@include ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][auto-http-metrics-path].
<!--/include-->

Helidon decides whether to measure incoming requests as follows:

- If you omit the `auto-http-metrics` configuration, Helidon measures all
  endpoints.
- If you specify the `auto-http-metrics` configuration, by default Helidon does
  not measure built-in endpoints such as metrics, health, and openapi. You can
  add items under `auto-http-metrics.paths` to control more exactly which
  endpoints to measure.
- If you include the `paths` section, Helidon checks a request against the path
  entries in order. A given request matches an entry if its path matches the
  path pattern and its HTTP method is in the `methods` list. If there is no
  `methods` list for an entry, all HTTP methods match the entry.
- Method names configured in path entries are matched exactly.
- If a request matches an entry, the entry’s `enabled` setting determines if the
  request should be measured.
- If a request matches multiple entries, the first match wins.
- If a request matches no entry, it is measured.

The `auto-http-metrics.sockets` setting controls which sockets are included in
the measurements; if not set, Helidon measures requests on all sockets.

##### Controlling HTTP Method Attribute Values

For the OpenTelemetry `http.server.request.duration` metric, the
`auto-http-metrics.known-methods` setting controls the values Helidon records
for the `http.request.method` attribute. This setting does not control which
requests Helidon measures; use the `methods` setting in a `paths` entry for that
purpose.

By default, Helidon records `CONNECT`, `DELETE`, `GET`, `HEAD`, `OPTIONS`,
`PATCH`, `POST`, `PUT`, `QUERY`, and `TRACE` using those exact method names.
Helidon records any other method, including case variants, as `_OTHER`. Method
names in the configuration and incoming requests must match exactly, and the
configured case is preserved in the recorded attribute.

Assigning `known-methods` replaces the entire default list. Include every
default method you want to retain.

Configuring HTTP Method Attribute Values:

```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        metrics:
          auto-http-metrics:
            known-methods: ["GET", "HEAD", "POST", "PROPFIND"]
```

With this configuration, Helidon records the four listed methods using their
configured names and records every other HTTP method, including default methods
omitted from the list, as `_OTHER`.

> [!NOTE]
> The `auto-http-metrics.use-updated-http-metrics` setting is deprecated and
> retained only for configuration compatibility with Helidon 4.x. Helidon 27
> ignores its value and always records OpenTelemetry HTTP request duration in
> seconds after the response is sent, preserves the final response status, and
> includes `http.route` only when a non-blank matching route is available.

Including and Excluding Endpoints from Automatic Measurement:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        metrics:
          auto-http-metrics:
            paths:
              - path: "/greet"              # <1>
                methods: ["GET","HEAD"]
              - path: "/greet/{name}"       # <2>
                enabled: false
            sockets: ["@default","private"] # <3>
```
1. Measure `/greet` for only `GET` and `HEAD` requests.
2. Do not measure the personalized greeting requests.
3. Measure only endpoints on the default socket and the socket named `private`.
   Endpoints on other sockets (such as if you had an `admin` socket) are not
   measured.
<!--@mdc :: -->

The [AutoHttpMetricsConfig documentation][autohttpmetricsc] describes the
configuration more fully.

## Examples

Helidon includes several pre-written example applications illustrating
aspects of metrics:

- [Filtering reported meters by name][filtering-meters] using the metrics endpoint
- [Controlling key performance indicator metrics][controlling-key] using
  configuration and `KeyPerformanceIndicatorMetricsSettings`.

### Custom Meter

The following example, based on the Helidon QuickStart application, shows how
to register and update a new `Counter` in application code. The counter tracks
the number of times any of the service endpoints is accessed.

Define and use a Counter:

<!--@mdc ::code-callout{collapsed} -->
```java
public class GreetService implements HttpService {

    private final Counter accessCtr;

    GreetService() {
        var meterRegistry = Services.get(MeterRegistry.class); // <1>
        var metricsFactory = meterRegistry.metricsFactory();

        this.accessCtr = meterRegistry
                .getOrCreate(metricsFactory.counterBuilder("accessctr")); // <2>
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .any(this::countAccess) // <3>
                .get("/", this::getDefaultMessageHandler)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);

    }

    void countAccess(ServerRequest request,
                     ServerResponse response) {

        accessCtr.increment(); // <4>
        response.next();
    }

    void getDefaultMessageHandler(ServerRequest request,
                                  ServerResponse response) {
        // ...
    }

    void getMessageHandler(ServerRequest request,
                           ServerResponse response) {
        // ...
    }

    void updateGreetingHandler(ServerRequest request,
                               ServerResponse response) {
        // ...
    }
}
```
1. Get the global meter registry from the service registry.
2. Create (or find) a counter named "accessctr" in the global registry using a
   counter builder from `MetricsFactory`.
3. Route every request to the `countAccess` method.
4. Increment the access counter for every request.
<!--@mdc :: -->

Perform the following steps to see the new counter in action.

Build and run the application:

```shell [Terminal]
mvn package
java -jar target/helidon-quickstart-se.jar
```

Retrieve the new counter:

<!--@mdc ::code-callout -->
```shell [Terminal]
curl 'http://localhost:8080/observe/metrics?name=accessctr' # <1>
```
1. Access the metrics endpoint, selecting the counter by name.
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```log [Response]
# HELP accessctr_total
# TYPE accessctr_total counter
accessctr_total 0.0 # <2>
```
2. Note the counter is zero; we have not accessed a service endpoint yet.
<!--@mdc :: -->

Access a service endpoint to retrieve a greeting:

```shell [Terminal]
curl http://localhost:8080/greet
```

```json [Response]
{"message":"Hello World"}
```

Retrieve the counter again:

```shell [Terminal]
curl 'http://localhost:8080/observe/metrics?name=accessctr'
```

<!--@mdc ::code-callout -->
```text [Response]
# HELP accessctr_total
# TYPE accessctr_total counter
accessctr_total 1.0 # <1>
```
1. The counter now reports 1, reflecting our earlier access to the `/greet`
   endpoint.
<!--@mdc :: -->

## Configuration Examples

Disabling metrics entirely:

```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        metrics:
          enabled: false
```

Helidon does not update metrics, and the `/observe/metrics` endpoints respond
with `404`.

### Virtual Threads Meters

Gathering data to compute the meters for virtual threads is designed to be as
efficient as possible, but doing so still imposes a load on the server and by
default Helidon does not report meters related to virtual threads.

Enabling virtual thread meters:

```yaml [application.yaml]
metrics:
  virtual-threads:
    enabled: true
```

###  Pinned Virtual Threads

Helidon measures pinned virtual threads only when the thread is pinned for a
length of time at or above a threshold. Control the threshold as shown in the
example below.

Setting virtual thread pinning threshold to 100 ms:

```yaml [application.yaml]
metrics:
  virtual-threads:
    pinned:
      threshold: PT0.100S
```

The threshold value is a `Duration` string, such as `PT0.100S` for 100
milliseconds.

### Key Performance Indicator (KPI) Meters

When you include the metrics observer and a metrics provider in your application,
Helidon tracks a basic performance indicator meter: a `Counter` of all requests
received (`requests.count`).

Helidon also includes additional, extended KPI meters which are disabled by
default:

- current number of requests in-flight - a `Gauge` (`requests.inFlight`) of
  requests currently being processed
- long-running requests - a `Counter` (`requests.longRunning`) measuring the
  total number of requests which take at least a given amount of time to
  complete; configurable, defaults to 10000 milliseconds (10 seconds)
- load - a `Counter` (`requests.load`) measuring the number of requests worked
  on (as opposed to received)
- deferred - a `Gauge` (`requests.deferred`) measuring delayed request
  processing (work on a request was delayed after Helidon received the request)

The names above use the default `CAMEL` built-in meter name format. With the
`SNAKE` format, Helidon reports the in-flight and long-running metrics as
`requests.in_flight` and `requests.long_running`, respectively.

You can enable and control these meters using configuration:

Controlling extended KPI meters:

```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        metrics:
          key-performance-indicators:
            extended: true
            long-running:
              threshold-ms: 2000
```

## Reference

- [Micrometer Metrics concepts documentation][micrometer-metri]
- [OpenMetrics format][openmetrics-form]
- [Prometheus exposition format][prometheus-expos]

[counter]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Counter.html
[counters]: https://docs.micrometer.io/micrometer/reference/concepts/counters.html
[distributionsumm]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/DistributionSummary.html
[distribution-sum]: https://docs.micrometer.io/micrometer/reference/concepts/distribution-summaries.html
[timer]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.html
[duration]: https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/time/Duration.html
[timers]: https://docs.micrometer.io/micrometer/reference/concepts/timers.html
[gauge-extends-nu]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Gauge.html
[number]: https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/lang/Number.html
[gauges]: https://docs.micrometer.io/micrometer/reference/concepts/gauges.html
[preview-feature]: https://helidon.io/docs/core/v28/apidocs/io.helidon.common.features.api/io/helidon/common/features/api/Features.Preview.html
[this-later-secti]: #inferred-publisher
[openmetrics-form]: https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
[prometheus-expos]: https://github.com/prometheus/docs/blob/main/docs/instrumenting/exposition_formats.md
[timeunit]: https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/concurrent/TimeUnit.html
[meterregistry]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/MeterRegistry.html
[metricsfactory]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/MetricsFactory.html
[meter-builder-customizer]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/spi/MeterBuilderCustomizer.html
[formatter-provider]: https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/spi/MeterRegistryFormatterProvider.html
[meter-builder-ba]: <https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Meter.Builder.html#baseUnit(java.lang.String)>
[timer-builder-ba]: <https://helidon.io/docs/core/v28/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.Builder.html#baseUnit(java.util.concurrent.TimeUnit)>
[wrapper]: https://helidon.io/docs/core/v28/apidocs/io.helidon.common/io/helidon/common/Wrapper.html
[autohttpmetricsc]: ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md
[helidon-opentele]: ../telemetry/opentelemetry.md#maven-coordinates
[filtering-meters]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/metrics/filtering/se
[controlling-key]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/metrics/kpi
[micrometer-metri]: https://docs.micrometer.io/micrometer/reference/concepts
[io-helidon-metri]: ../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options
[io-helidon-metri-2]: ../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options
[io-helidon-metri-3]: ../../config/io.helidon.metrics.api.MetricsConfig.md#configuration-options
[auto-http-metrics]: ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#configuration-options
[auto-http-metrics-path]: ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig.md
