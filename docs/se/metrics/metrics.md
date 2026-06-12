# Metrics in Helidon SE

## Overview

Helidon SE metrics is a neutral metrics API which provides

- a unified way for Helidon servers to export monitoring data—telemetry—to management agents, and
- a unified Java API which all application programmers can use to register and update meters to expose telemetry data from their services.

Metrics is one of the Helidon observability features.

> [!NOTE]
> Beginning with Helidon 4.1, strongly consider assigning the config setting
>
> ``` properties
> metrics.gc-time-type = gauge
> ```
>
> See the [longer discussion below][longer-discussio] in the Configuration section.

### Terminology

Helidon SE uses the term "metrics" to refer to the subsystem in Helidon which manages the registration of, updates to, and
reporting of aggregate statistical measurements about the service. The term "meter" refers to an entity which collects these
measurements, such as a counter or a timer.

## Maven Coordinates

To enable metrics, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../managing-dependencies.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics</groupId>
  <artifactId>helidon-metrics-api</artifactId>
</dependency>
```

This dependency adds the metrics API and a no-op implementation of that API to your project. The no-op implementation:

- does not register meters in a registry
- does not update meter values
- does not expose the metrics endpoint for reporting meter values.

To include the full-featured metrics implementation and support for the metrics endpoint, add the following dependency to your project:

Packaging the metrics endpoint support and a full-featured metrics implementation:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-metrics</artifactId>
</dependency>
```

Adding this dependency packages the full-featured metrics implementation and support for the metrics endpoint with your service.

You might notice the transitive dependency `io.helidon.metrics.providers:helidon-metrics-providers-micrometer` in your project. This component contains an implementation of the Helidon metrics API that uses Micrometer as the underlying metrics technology.

Helidon provides several built-in meters in a separate artifact. To include the build-in meters, add the following dependency to your project:

Packaging the built-in meters:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics</groupId>
  <artifactId>helidon-metrics-system-meters</artifactId>
  <scope>runtime</scope>
</dependency>
```

## Instrumenting Your Service

You add meters to your service by writing code which explicitly invokes the metrics API to register meters, retrieve previously-registered meters, and update meter values.

Later sections of this document describe how to do this.

## Meter Types

Helidon supports meters inspired by [Micrometer](https://micrometer.io) and summarized in the following table:

| Meter Type                                    | Description                                                                                                                                                                                                                                                                               | Micrometer reference                       |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| [`Counter`][counter]                          | Monotonically-increasing `long` value.                                                                                                                                                                                                                                                    | [Counters][counters]                       |
| [`DistributionSummary`][distributionsumm]     | Summary of samples each with a `long` value. Reports aggregate information over all samples (count, total, mean, max) as well as the distribution of sample values using percentiles and bucket counts.                                                                                   | [Distribution summaries][distribution-sum] |
| [`Timer`][timer]                              | Accumulation of short-duration (typically under a minute) intervals. Typically updated using a Java [`Duration`][duration] or by recording the time taken by a method invocation or lambda. Reports the count, total time, max, and mean; provides a distribution summary of the samples. | [Timers][timers]                           |
| [`Gauge<? extends Number>`][gauge-extends-nu] | View of a value that is assignment-compatible with a subtype of Java [`Number`][number]. The underlying value is updated by code elsewhere in the system, not by invoking methods on the gauge itself.                                                                                    | [Gauges][gauges]                           |

Types of Meters

## Meters Category

Helidon distinguishes among *scopes*, or categories, of meters.

Helidon includes meters in the built-in scopes described below. Applications often register their own meters in the `application` scope but can create their own scopes and register meters within them.

| Built-in Scope | Typical Usage                                                                                                                                |
|----------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `base`         | OS or Java runtime measurements (available heap, disk space, etc.).                                                                          |
| `vendor`       | Implemented by vendors, including the `REST.request` metrics and other key performance indicator measurements (described in later sections). |
| `application`  | Declared via annotations or programmatically registered by your service code.                                                                |

When an application creates a new meter it can specify which scope the meter belongs to. If the application does not specify a scope for a new meter, the default scope is `application`.

## Meter Registry

Helidon stores all meters in a *meter registry*. Typically, applications use the global meter registry which is the registry where Helidon stores built-in meters. Application code refers to the global registry using `Metrics.globalRegistry()`.

## Publishing Metrics

Helidon’s Micrometer-based metrics implementation includes these ways of publishing metrics data to external systems:

- Prometheus/OpenMetrics
- OTLP (OpenTelemetry Protocol)

> [!NOTE]
> The configuration of metrics publishers as described below is a [preview feature][preview-feature] which Helidon intends
> to keep, but its external interface or behavior might evolve between dot releases.

You can configure publishers in the `publishers` configuration section under the top level `metrics` node or under `server.features.observe.observers.metrics`. If you do not set up publishers explicitly, Helidon uses an inferred Prometheus publisher for backward compatibility. See [this later section][this-later-secti] for details.

Publishers in Helidon’s Micrometer-based metrics implementation use Micrometer `MeterRegistry` implementations. For each enabled publisher, Helidon adds the corresponding meter registry to Micrometer’s global registry. This has these important effects:

- Meters which Helidon or your code registers using the Helidon metrics API are registered in all active Micrometer meter registries.
- Each Helidon meter registered has an implementation in every active Micrometer meter registry.
- When Helidon or your code updates a Helidon meter, Micrometer applies the change to every corresponding meter from each active meter registry.

As a result, configuring more than one active meter registry can affect performance.

> [!NOTE]
> Make sure at least one of the configured publishers is enabled. If not, Micrometer does not have any active meter registry implementations and the registered metrics are no-ops. Helidon logs a warning in this case during the metrics observer initialization.

## OpenTelemetry Protocol

If you configure an OTLP publisher, Helidon exports metrics data periodically to a backend system you configure.

### Configuration options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options](../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options).
<!--/include-->


The configuration directly mirrors the Micrometer `OtlpMeterRegistry` settings so you can control all behavior which Micrometer exposes for the meter registry.

The following example sets up an OTLP publisher to transmit metrics data every 30 seconds.

Example OTLP publisher settings:

```yaml
metrics:
  publishers:         
    otlp:  
      interval: PT30S
      url: 'http://somehost.com:4318/v1/metrics'
```

- Introduces the configured publishers.
- Configures an OTLP publisher to transmit every 30 seconds to the given endpoint.

## Prometheus Publisher

If you configure a Prometheus publisher or rely on the inferred one, Helidon can make the metrics data available in the Prometheus/OpenMetrics format. (To serve the data at the metrics endpoint in your service, your project must also depend on the Helidon metrics observer component.)

### Configuration options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options](../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options).
<!--/include-->

### Inferred Publisher

As described earlier, Helidon prepares an inferred Prometheus publisher if you do not set up any publishers.

Note that Helidon uses the inferred publisher *only* if you add *no* publishers explicitly, either in the configuration or programmatically. If you specify any publishers explicitly, Helidon uses only the ones you set up.

In particular, Helidon *does not* use the inferred Prometheus publisher if you create a `metrics.publishers` section containing only an OTLP publisher.

You can configure other publishers and still have Helidon use the default one by simply adding the `prometheus` publisher entry. You do not need to specify further settings for it.

Using an OLTP publisher **and** the default Prometheus publisher:

```yaml [application.yaml]
metrics:
  publishers:
    prometheus:
    otlp:
      interval: PT20S
```

## Additional Publishers

You can write other publishers by following these steps:

1.  Choose one of the Micrometer `MeterRegistry` implementations for the type of publishing you want to support. (for example [`DatadogMeterRegistry`][datadogmeterregi])
2.  Create a config blueprint which exposes the meter registry’s [settable properties from `DatadogConfig`][settable-propert].
3.  Write a `DatadogPublisher` class which implements Helidon’s `MetricsPublisher` for Datadog.
4.  Write a `DatadogPublisherProvider` class which implements Helidon’s `MetricsPublisherProvider` for your publisher.
5.  Advertise your provider so Java service loading can find it, creating a `META-INF/services/io.helidon.metrics.spi.PublisherProvider` file listing your implementation class.

Look at Helidon’s [OTLP publisher blueprint][otlp-publisher-b] and the related types as an example.

Refer to your publisher in configuration using the config key you set up in the publisher provider.

Example config using a hypothetical Datadog publisher:

```yaml [application.yaml]
metrics:
  publishers:
    micrometer-datadog:
      interval: PT15S
```

## Metrics Endpoint

When you add the `helidon-webserver-observe-metrics` dependency to your project, Helidon provides a built-in REST endpoint
`/observe/metrics` which responds with a report of the registered meters and their values.

Clients can request a particular output format from the endpoint.

| Format                   | Requested by                      |
|--------------------------|-----------------------------------|
| OpenMetrics (Prometheus) | default (`text/plain`)            |
| JSON                     | Header `Accept: application/json` |

Formats for `/observe/metrics` output

Clients can also limit the report by specifying the scope as a query parameter in the request URL:

- `/observe/metrics?scope=base`
- `/observe/metrics?scope=vendor`
- `/observe/metrics?scope=application`

Further, clients can narrow down to a specific metric name by adding the name as another query parameter, such as `/observe/metrics?scope=application&name=myCount`.

Example Reporting: Prometheus format:

```shell [Terminal]
curl -s -H 'Accept: text/plain' -X GET http://localhost:8080/observe/metrics
```

```text
# HELP classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
# TYPE classloader_loadedClasses_count gauge
classloader_loadedClasses_count{scope="base",} 5297.0
```

See the summary of the [OpenMetrics and Prometheus Format](#format) for more information.

Example Reporting: JSON format:

```shell [Terminal]
curl -s -H 'Accept: application/json' -X GET http://localhost:8080/observe/metrics
```

```json [Response]
{
   "base" : {
      "memory.maxHeap" : 3817865216,
      "memory.committedHeap" : 335544320
    }
}
```

In addition to your application meters, the reports contain other meters of interest such as system and VM information.

### Format

The [OpenMetrics format][openmetrics-form] and the [Prometheus exposition format][prometheus-expos] are very similar in most important respects but are not identical. This brief summary treats them as the same.

The OpenMetrics/Prometheus format represents each meter using three lines of output as summarized in the following table.

| Line prefix | Purpose                                                      | Format                                                |
|-------------|--------------------------------------------------------------|-------------------------------------------------------|
| `# TYPE`    | Displays the scope, name, and type of the meter              | `TYPE <scope>:<output-name> <meter-type>`             |
| `# HELP`    | Displays the scope, name, and description of the meter       | `HELP <scope>:<output-name> <registered description>` |
| (none)      | Displays the scope, meter ID, and current value of the meter | `<scope>:<output-name> <current value>`               |

The OpenMetrics/Prometheus output converts meter IDs in these ways:

- Names in camel case are converted to "snake case" and dots are converted to underscores.
- Names include any units specified for the meter.
- For percentiles, the ID includes a tag identifying which percentile the line of output describes.

As the earlier example output showed, for a meter with multiple values, the OpenMetrics/Prometheus output reports a
"metric family" which includes a separate family member meter for each of the multiple values.

The name for each member in the family is derived from the
registered name for the meter plus a suffix indicating which one of the meter’s multiple values the line refers to.

The following table summarizes the naming for each meter type.

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
<td><p><code>Counter</code></p></td>
<td><p><code>requests.<wbr>count</code></p></td>
<td><p>count</p></td>
<td><p><code>_total</code></p></td>
<td><p><code>requests_<wbr>count_<wbr>total</code></p></td>
</tr>
<tr>
<td rowspan="4"><p><code>Distribution<wbr>Summary</code></p></td>
<td rowspan="4"><p><code>nameLengths</code></p></td>
<td><p>count</p></td>
<td><p><code>_count</code></p></td>
<td><p><code>name<wbr>Lengths_<wbr>count</code></p></td>
</tr>
<tr>
<td><p>sum</p></td>
<td><p><code>_sum</code></p></td>
<td><p><code>nameLengths_<wbr>sum</code></p></td>
</tr>
<tr>
<td><p>max</p></td>
<td><p><code>_max</code></p></td>
<td><p><code>nameLengths_<wbr>max</code></p></td>
</tr>
<tr>
<td><p>percentile</p></td>
<td><p>none</p></td>
<td><p><code>nameLengths{<wbr>scope="base",<wbr>quantile="0.5",<wbr>}</code></p></td>
</tr>
<tr>
<td><p><code>Gauge</code></p></td>
<td><p><code>classloader.<wbr>loadedClasses.<wbr>count</code></p></td>
<td><p>value</p></td>
<td><p>none</p></td>
<td><p><code>classloader_<wbr>loadedClasses_<wbr>count</code></p></td>
</tr>
<tr>
<td rowspan="4"><p><code>Timer</code> <sup>1</sup></p></td>
<td rowspan="4"><p><code>vthreads.<wbr>recentPinned</code></p></td>
<td><p>count</p></td>
<td><p><code>_count</code></p></td>
<td><p><code>vthreads_<wbr>recentPinned_<wbr>seconds_<wbr>count</code></p></td>
</tr>
<tr>
<td><p>sum</p></td>
<td><p><code>_sum</code></p></td>
<td><p><code>vthreads_<wbr>recentPinned_<wbr>seconds_<wbr>sum</code></p></td>
</tr>
<tr>
<td><p>max</p></td>
<td><p><code>_max</code></p></td>
<td><p><code>vthreads_<wbr>recentPinned_<wbr>seconds_<wbr>max</code></p></td>
</tr>
<tr>
<td><p>percentile</p></td>
<td><p>none</p></td>
<td><p><code>vthreads_<wbr>recentPinned_<wbr>seconds{<wbr>scope="base",<wbr>quantile="0.5",<wbr>}</code></p></td>
</tr>
</tbody>
</table>

<sup>1</sup> The OpenMetrics/Prometheus output format reports a timer as a `summary` with units of `seconds`.

#### JSON Format

Unlike OpenMetrics/Prometheus output, which combines the data and the metadata in a single response, you use an HTTP `GET` request to retrieve metrics JSON *data* and an `OPTIONS` request to retrieve *metadata* in JSON format.

Helidon groups meters in the same scope together in JSON output as shown in the following example.

JSON metrics output structured by scope (partial):

```json
{
  "application": {  
    "getTimer": {
      "type": "timer",
      "unit": "seconds",
      "description": "Timer for getting the default greeting"
    }
  },
  "vendor": {       
    "requests.count": {
      "type": "counter",
      "description": "Each request (regardless of HTTP method) will increase this counter"
    }
  },
  "base": {         
    "cpu.systemLoadAverage": {
      "type": "gauge",
      "description": "Displays the system load average for the last minute."
    },
    "classloader.loadedClasses.count": {
      "type": "gauge",
      "description": "Displays the number of classes that are currently loaded in the Java virtual machine."
    }
  }
}
```

- Note the `application`, `vendor`, and `base` sections.

If an HTTP request [selects by scope](#metrics-endpoint), the output omits the extra level of structure that identifies the scope as shown in the following example.

JSON metrics output for the base scope (partial):

```json
{
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

##### Understanding the JSON Metrics Data Format

The Helidon JSON format expresses each meter as either a single value (for example, a counter) or a structure with multiple values (for example, a timer).

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

By default, Helidon formats time values contained in JSON output as seconds. You can change this behavior [as described below](#controlling-json-timer-output).

##### Understanding the JSON Metrics Metadata Format

Access the metrics endpoint with an HTTP `OPTIONS` request and the `Accept: application/json` header to retrieve metadata in JSON format.

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

Generally, the output for a given meter reflects only the metadata that the application or Helidon code explicitly set on that meter.

One exception is that metadata for a timer always includes the `unit` field. By default, Helidon formats timer data in JSON output as seconds, regardless of any explicit `baseUnit` setting applied to the timers. But as [described below](#controlling-json-timer-output) you can change this behavior which can lead to different timers being formatted using different units. Checking the metadata is the only way to know for sure what units Helidon used to express a given timer, so Helidon always includes `unit` in timer metadata.

##### Controlling JSON Timer Output

By default, Helidon expresses timer data as seconds.

You can change this using configuration:

Setting default timer units for JSON in `application.yaml`:

```yaml [application.yaml]
metrics:
  timers:
    json-units-default: units 
```

- For *units* specify any valid name for a [`TimeUnit`][timeunit] value (`SECONDS`, `MILLISECONDS`, etc.)

If you have configured `json-units-default`, Helidon formats each timer’s data as follows:

1.  If code set `baseUnit` on the timer, Helidon uses those units for that timer.
2.  Otherwise, Helidon uses the default units you configured.

To enable the JSON output behavior from Helidon 3, specify `json-units-default` as `NANOSECONDS`.

### Enabling the Metrics REST Service

If you add the dependencies described above, your service automatically supports the metrics REST endpoint as long as the `WebServer` is configured to discover features automatically.

If you disable auto-discovery, you can add the metrics observer explicitly.

1.  Create an instance of `MetricsObserver`, either directly as shown below or using its builder.
2.  Include the `MetricsObserver` instance in your application’s `ObserveFeature`.
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

1.  Use the static `globalRegistry` method on the [`Metrics`][metrics] interface to get a reference to the global [`MeterRegistry`][meterregistry] instance.
2.  Use the `MeterRegistry` instance to register new meters and look up previously-registered meters.
3.  Use the meter reference returned from the `MeterRegistry` to update the meter or get its value.

You can also use the `MeterRegistry` to remove an existing meter.

### Helidon Metrics API

The Helidon Metrics API defines the classes and interfaces for meter types and other related items.

The following table summarizes the meter types.

| Meter Type                                | Usage                                                                           |
|-------------------------------------------|---------------------------------------------------------------------------------|
| [`Counter`][counter]                      | Monotonically increasing count of events.                                       |
| [`Gauge`][gauge-extends-nu]               | Access to a value managed by other code in the service.                         |
| [`DistributionSummary`][distributionsumm] | Calculates the distribution of a value.                                         |
| [`Timer`][timer]                          | Frequency of invocations and the distribution of how long the invocations take. |

Meter Types

Each meter type has its own set of methods for updating and retrieving the value.

### `MeterRegistry`

To register or look up meters programmatically, your service code uses the global `MeterRegistry`. Simply invoke `Metrics.globalRegistry()` to get a reference to the global meter registry.

To locate an existing meter or register a new one, your code:

1.  Creates a builder of the appropriate type of meter, setting the name and possibly other characteristics of the meter.
2.  Invokes the `MeterRegistry.getOrCreate` method, passing the builder.

The meter registry returns a reference to a previously-registered meter with the specified name and tags or, if none exists, a newly-registered meter. Your code can then operate on the returned meter as needed to record new measurements or retrieve existing data.

The example code in the [Examples](#examples) section below illustrates how to register, retrieve, and update meters.

#### Understanding Timers, Units, and Output

Your application can assign the meter builder’s [`Meter.Builder baseUnit`][meter-builder-ba] setting for any meter your application creates. In particular, the [`Timer.Builder baseUnit`][timer-builder-ba] method allows code to assign a `baseUnit` for a timer, passing a Java [`TimeUnit`][timeunit] value. The timer builder also has the `String` variant of the `baseUnit` method and enforces that the value corresponds (case-insensitively) to one of the `TimeUnit` enum values.

Note that, regardless of the `baseUnit` setting for a `Timer`, by convention and specification Prometheus output expresses time values in `seconds`.

By default, the same is true of Helidon’s JSON format: timer values are displayed in `seconds` regardless of any timer’s `baseUnit` setting. You can override this as described in the [Controlling Timer Output](#controlling-json-timer-output) section, in which case the JSON output for each timer reflects its `baseUnit` setting.

### Accessing the Underlying Implementation: `unwrap`

The neutral Helidon metrics API is an abstraction of common metrics behavior independent of any given implementation. As such, we intentionally excluded some implementation-specific behavior from the API.

Sometimes you might want access to methods that are present in a particular metrics implementation but not in the Helidon API. Helidon allows that via the `unwrap` method on the meter types and on their builders. Each full implementation of the Helidon meter types and their builders refers to a delegate meter or delegate builder internally. The `unwrap` method lets you obtain the delegate, cast to the type you want.

Of course, using this technique binds your code to a particular metrics implementation.

The [`Wrapper`][wrapper] interface declares the `unwrap` method which accepts a class parameter to which the delegate is cast. You can then invoke any method declared on the implementation-specific type.

## Configuration options

<!--@include ../../config/io.helidon.metrics.api.MetricsConfig.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../../config/io.helidon.metrics.api.MetricsConfig.md#configuration-options).
<!--/include-->

| Key                | Default Value |
|--------------------|---------------|
| `app-tag-name`     | `app`         |
| `scoping.tag-name` | `scope`       |
| `scoping.default`  | `application` |

## Meter Type for `gc.time`

To date Helidon 4 releases have implemented the system-provided meter `gc.time` as a counter. In fact, a gauge is more suitable for the approximate time the JVM has spent doing garbage collection.

Helidon uses a counter by default to preserve backward compatibility, but you can choose to use a gauge by setting the configuration property `metrics.gc-time-type` to `gauge`. You can also set the config property to `counter` which is the default.

Why should you care? In fact, this distinction might not make a difference for many users. But for others the differences between the programmatic APIs for `Counter` and `Gauge` would affect application code that works directly with the `gc-time` meter. Further, the difference in output—​particularly in the OpenMetrics/Prometheus format—​might affect their application or downstream monitoring tools.

The ability to choose the meter type for `gc.time` is deprecated and is planned for removal in a future major release of Helidon at which time Helidon will always use a gauge.

## Metrics Observer

Helidon can make the registered meters and their current values available externally at an endpoint (`/observe/metrics` by default). You can control aspects of how Helidon furnishes this information under the `server.features.observe.observers.metrics` configuration section.

| key        | type                                      | default value      | description                                             |
|------------|-------------------------------------------|--------------------|---------------------------------------------------------|
| `auto`     | [AutoHttpMetricsConfig][autohttpmetricsc] |                    | Automatic metrics collection settings.                  |
| `enabled`  | boolean                                   | `true`             | Whether this observer is enabled.                       |
| `endpoint` | string                                    | `/observe/metrics` | Path at which clients can retrieve metrics information. |

See the [Helidon OpenTelemetry documentation][helidon-opentele] for more information.

#### Selecting REST Endpoints for Automatic Measurement

You can choose which endpoints to include in Helidon’s automatic measurements using the `auto-http-metrics` config section.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options](../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#configuration-options).
<!--/include-->


The `paths` section contains zero or more entries, each entry having the following settings:

<table>
<caption><code>path</code> entry settings</caption>
<thead>
<tr>
<th>Key</th>
<th>Required</th>
<th>Default Value</th>
<th>Usage</th>
</tr>
</thead>
<tbody>
<tr>
<td><p><code>path</code></p></td>
<td><p>yes</p></td>
<td><p> </p></td>
<td><p>Path-matching expression:</p>
<ul>
<li><p>an exact match (<code>/greet</code>)</p></li>
<li><p>a prefix match (<code>/greet/*</code>)</p></li>
<li><p>a pattern match (<code>/greet/{name}</code>)</p></li>
</ul></td>
</tr>
<tr>
<td><p><code>methods</code></p></td>
<td><p> </p></td>
<td><p>all HTTP method types</p></td>
<td><p>Which HTTP methods match this entry</p></td>
</tr>
<tr>
<td><p><code>enabled</code></p></td>
<td><p> </p></td>
<td><p><code>true</code></p></td>
<td><p>Whether requests that match this entry should be measured</p></td>
</tr>
</tbody>
</table>

Helidon decides whether to measure incoming requests as follows:

- If you omit the `auto-http-metrics` configuration, Helidon measures all endpoints.
- If you specify the `auto-http-metrics` configuration, by default Helidon does not measure built-in endpoints such as metrics, health, and openapi. You can add items under `auto-http-metrics.paths` to control more exactly which endpoints to measure.
- If you include the `paths` section, Helidon checks a request against the path entries in order. A given request matches an entry if its path matches the path pattern and its HTTP method is in the `methods` list. If there is no `methods` list for an entry, all HTTP methods match the entry.
- If a request matches an entry, the entry’s `enabled` setting determines if the request should be measured.
- If a request matches multiple entries, the first match wins.
- If a request matches no entry, it is measured.

The `auto-http-metrics.sockets` setting controls which sockets are included in the measurements; if not set, Helidon measures requests on all sockets.

Including and Excluding Endpoints from Automatic Measurement:

```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        metrics:
          auto-http-metrics:
            paths:
              - path: "/greet"              
                methods: ["GET","HEAD"]
              - path: "/greet/{name}"       
                enabled: false
            sockets: ["@default","private"] 
```

- Measure `/greet` for only `GET` and `HEAD` requests.
- Do not measure the personalized greeting requests.
- Measure only endpoints on the default socket and the socket named `private`. Endpoints on other sockets (such as if you had an `admin` socket) are not measured.

The [AutoHttpMetricsConfig documentation][autohttpmetricsc] describes the configuration more fully.

## Examples

Helidon SE includes several pre-written example applications illustrating aspects of metrics:

- [Enabling/disabling meters][enabling-disabli] using `MetricsObserver` and `MetricsConfig`
- [Controlling key performance indicator metrics][controlling-key] using configuration and `KeyPerformanceIndicatorMetricsSettings`.

### Custom Meter

The following example, based on the Helidon SE QuickStart application, shows how to register and update a new `Counter` in application code. The counter tracks the number of times any of the service endpoints is accessed.

Define and use a Counter:

<!--@mdc ::code-collapse -->
```java
public class GreetService implements HttpService {

    private final Counter accessCtr = Metrics.globalRegistry() 
            .getOrCreate(Counter.builder("accessctr")); 

    @Override
    public void routing(HttpRules rules) {
        rules
                .any(this::countAccess) 
                .get("/", this::getDefaultMessageHandler)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);

    }

    void countAccess(ServerRequest request,
                     ServerResponse response) {

        accessCtr.increment(); 
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
<!--@mdc :: -->

- Get the global meter registry.
- Create (or find) a counter named "accessctr" in the global registry.
- Route every request to the `countAccess` method.
- Increment the access counter for every request.

Perform the following steps to see the new counter in action.

Build and run the application:

```shell [Terminal]
mvn package
java -jar target/helidon-quickstart-se.jar
```

Retrieve application metrics:

```shell [Terminal]
curl 'http://localhost:8080/observe/metrics?scope=application' 
```

```text [Response]
# HELP accessctr_total
# TYPE accessctr_total counter
accessctr_total{scope="application",} 0.0 
```

- Access the metrics endpoint, selecting only application meters.
- Note the counter is zero; we have not accessed a service endpoint yet.

Access a service endpoint to retrieve a greeting:

```shell [Terminal]
curl http://localhost:8080/greet
```

```json [Response]
{"message":"Hello World"}
```

Retrieve application metrics again:

```shell [Terminal]
curl 'http://localhost:8080/observe/metrics?scope=application'
```

```text [Response]
# HELP accessctr_total
# TYPE accessctr_total counter
accessctr_total{scope="application",} 1.0 
```

- The counter now reports 1, reflecting our earlier access to the `/greet` endpoint.

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

Helidon does not update metrics, and the `/observe/metrics` endpoints respond with `404`.

### Virtual Threads Meters

Gathering data to compute the meters for virtual threads is designed to be as efficient as possible, but doing so still imposes
a load on the server and by default Helidon does not report meters related to virtual threads.

Enabling virtual thread meters:

```yaml [application.yaml]
metrics:
  virtual-threads:
    enabled: true
```

###  Pinned Virtual Threads

Helidon measures pinned virtual threads only when the thread is pinned for a length of time at or above a threshold. Control the threshold as shown in the example below.

Setting virtual thread pinning threshold to 100 ms:

```yaml [application.yaml]
metrics:
  virtual-threads:
    pinned:
      threshold: PT0.100S
```

The threshold value is a `Duration` string, such as `PT0.100S` for 100 milliseconds.

### Key Performance Indicator (KPI) Meters

Any time you include the Helidon metrics module in your application, Helidon tracks a basic performance indicator meter: a `Counter` of all requests received (`requests.count`)

Helidon SE also includes additional, extended KPI meters which are disabled by default:

- current number of requests in-flight - a `Gauge` (`requests.inFlight`) of requests currently being processed
- long-running requests - a `Counter` (`requests.longRunning`) measuring the total number of requests which take at least a given amount of time to complete; configurable, defaults to 10000 milliseconds (10 seconds)
- load - a `Counter` (`requests.load`) measuring the number of requests worked on (as opposed to received)
- deferred - a `Gauge` (`requests.deferred`) measuring delayed request processing (work on a request was delayed after Helidon received the request)

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

## Prometheus Support

Helidon provides optional, deprecated support for the Prometheus metrics API.

> [!WARNING]
> `helidon-metrics-prometheus` is deprecated as of Helidon 4.5.0 and will be removed in a future major release.
> Prefer Helidon’s built-in metrics implementation, which already exposes Prometheus (OpenMetrics) output without requiring
> direct use of the Prometheus client API.

If you are maintaining an existing application that already uses the Prometheus client API, your service can still register Prometheus support with your routing set-up. You can customize its configuration. For information about using Prometheus, see the Prometheus documentation: <https://prometheus.io/docs/introduction/overview/>.

### Maven Coordinates

Dependency for Helidon Prometheus API support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.metrics</groupId>
  <artifactId>helidon-metrics-prometheus</artifactId>
</dependency>
```

### Usage

If you are maintaining an existing application, your code uses the Prometheus API to manage metrics.

To expose those metrics to clients via a REST endpoint, your code uses the `PrometheusSupport` interface which Helidon provides.

Your code creates a [`PrometheusSupport`][prometheussuppor] object either using a static factory method (shown in the following example) or by using its [`Builder`][builder].

```java
routing
    .addFeature(PrometheusSupport.create())
    .register("/myapp", new MyService());
```

This example uses the default Prometheus `CollectorRegistry`. By default, the `PrometheusSupport` and exposes its REST endpoint
at the path `/metrics`.

Use the builder obtained by `PrometheusSupport.builder()` to configure a different `CollectorRegistry` or a different path.

## References

- [Micrometer Metrics concepts documentation][micrometer-metri]
- [OpenMetrics format][openmetrics-form]
- [Prometheus exposition format][prometheus-expos]

[longer-discussio]: #meter-type-for-gctime
[counter]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Counter.html
[counters]: https://docs.micrometer.io/micrometer/reference/concepts/counters.html
[distributionsumm]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/DistributionSummary.html
[distribution-sum]: https://docs.micrometer.io/micrometer/reference/concepts/distribution-summaries.html
[timer]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.html
[duration]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Duration.html
[timers]: https://docs.micrometer.io/micrometer/reference/concepts/timers.html
[gauge-extends-nu]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Gauge.html
[number]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java.lang.Number.html
[gauges]: https://docs.micrometer.io/micrometer/reference/concepts/gauges.html
[preview-feature]: https://helidon.io/docs/v4/apidocs/io.helidon.common.features.api/io/helidon/common/features/api/Preview.html
[this-later-secti]: #inferred-publisher
[datadogmeterregi]: https://github.com/micrometer-metrics/micrometer/tree/main/implementations/micrometer-registry-datadog
[settable-propert]: https://github.com/micrometer-metrics/micrometer/blob/main/implementations/micrometer-registry-datadog/src/main/java/io/micrometer/datadog/DatadogConfig.java
[otlp-publisher-b]: {https://github.com/helidon-io/helidon/tree/main/metrics/providers/micrometer/src/main/java/io/helidon/metrics/providers/micrometer/OtlpPublisherConfigBlueprint.java
[openmetrics-form]: https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
[prometheus-expos]: https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md
[timeunit]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html
[metrics]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.html
[meterregistry]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/MeterRegistry.html
[meter-builder-ba]: <https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Meter.Builder.html#baseUnit(java.lang.String)>
[timer-builder-ba]: <https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.Builder.html#baseUnit(java.util.concurrent.TimeUnit)>
[wrapper]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Wrapper.html
[autohttpmetricsc]: ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md
[helidon-opentele]: ../../se/telemetry/open-telemetry.md#maven-coordinates
[enabling-disabli]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/metrics/filtering/se
[controlling-key]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/metrics/kpi
[choose-whether-t]: #virtual-threads-meters
[choose-whether-t-2]: #key-performance-indicator-kpi-meters
[micrometer-metri]: https://docs.micrometer.io/micrometer/reference/concepts
[prometheussuppor]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.prometheus/io/helidon/metrics/prometheus/PrometheusSupport.html
[builder]: https://helidon.io/docs/v4/apidocs/io.helidon.metrics.prometheus/io/helidon/metrics/prometheus/PrometheusSupport.Builder.html
