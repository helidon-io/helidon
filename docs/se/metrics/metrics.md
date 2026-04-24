# Metrics in Helidon SE

## Overview

Helidon SE metrics is a neutral metrics API which provides

- a unified way for Helidon servers to export monitoring data—​telemetry—​to management agents, and
- a unified Java API which all application programmers can use to register and update meters to expose telemetry data from their services.

Metrics is one of the Helidon observability features.

> [!NOTE]
> Beginning with Helidon 4.1, strongly consider assigning the config setting
>
> ``` properties
> metrics.gc-time-type = gauge
> ```
>
> See the [longer discussion below](#controlling-the-meter-type-for-gctime) in the Configuration section.

### A Word about Terminology

Helidon SE uses the term "metrics" to refer to the subsystem in Helidon which manages the registration of, updates to, and reporting of aggregate statistical measurements about the service. The term "meter" refers to an entity which collects these measurements, such as a counter or a timer.

## Maven Coordinates

To enable metrics, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

*Packaging the metrics API*

```xml
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

*Packaging the metrics endpoint support and a full-featured metrics implementation*

```xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-metrics</artifactId>
</dependency>
```

Adding this dependency packages the full-featured metrics implementation and support for the metrics endpoint with your service.

You might notice the transitive dependency `io.helidon.metrics.providers:helidon-metrics-providers-micrometer` in your project. This component contains an implementation of the Helidon metrics API that uses Micrometer as the underlying metrics technology.

Helidon provides several built-in meters in a separate artifact. To include the build-in meters, add the following dependency to your project:

*Packaging the built-in meters*

```xml
<dependency>
    <groupId>io.helidon.metrics</groupId>
    <artifactId>helidon-metrics-system-meters</artifactId>
    <scope>runtime</scope>
</dependency>
```

## Usage

### Instrumenting Your Service

You add meters to your service by writing code which explicitly invokes the metrics API to register meters, retrieve previously-registered meters, and update meter values.

Later sections of this document describe how to do this.

### Meter Types

Helidon supports meters inspired by [Micrometer](https://micrometer.io) and summarized in the following table:

|  |  |  |
|----|----|----|
| Meter Type | Description | Micrometer reference |
| [`Counter`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Counter.html) | Monotonically-increasing `long` value. | [Counters](https://docs.micrometer.io/micrometer/reference/concepts/counters.html) |
| [`DistributionSummary`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/DistributionSummary.html) | Summary of samples each with a `long` value. Reports aggregate information over all samples (count, total, mean, max) as well as the distribution of sample values using percentiles and bucket counts. | [Distribution summaries](https://docs.micrometer.io/micrometer/reference/concepts/distribution-summaries.html) |
| [`Timer`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.html) | Accumulation of short-duration (typically under a minute) intervals. Typically updated using a Java [`Duration`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Duration.html) or by recording the time taken by a method invocation or lambda. Reports the count, total time, max, and mean; provides a distribution summary of the samples. | [Timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) |
| [`Gauge<? extends Number>`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Gauge.html) | View of a value that is assignment-compatible with a subtype of Java [`Number`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java.lang.Number.html). The underlying value is updated by code elsewhere in the system, not by invoking methods on the gauge itself. | [Gauges](https://docs.micrometer.io/micrometer/reference/concepts/gauges.html) |

Types of Meters

### Categorizing Types of Meters

Helidon distinguishes among *scopes*, or categories, of meters.

Helidon includes meters in the built-in scopes described below. Applications often register their own meters in the `application` scope but can create their own scopes and register meters within them.

| Built-in Scope | Typical Usage |
|----|----|
| `base` | OS or Java runtime measurements (available heap, disk space, etc.). |
| `vendor` | Implemented by vendors, including the `REST.request` metrics and other key performance indicator measurements (described in later sections). |
| `application` | Declared via annotations or programmatically registered by your service code. |

Built-in meter scopes

When an application creates a new meter it can specify which scope the meter belongs to. If the application does not specify a scope for a new meter, the default scope is `application`.

### Meter Registry

Helidon stores all meters in a *meter registry*. Typically, applications use the global meter registry which is the registry where Helidon stores built-in meters. Application code refers to the global registry using `Metrics.globalRegistry()`.

### Publishing Metrics for External Access

Helidon’s Micrometer-based metrics implementation includes these ways of publishing metrics data to external systems:

- Prometheus/OpenMetrics
- OTLP (OpenTelemetry Protocol)

#### Configuring Publishers

> [!NOTE]
> The configuration of metrics publishers as described below is a [preview feature](/apidocs/io.helidon.common.features.api/io/helidon/common/features/api/Preview.html) which Helidon intends to keep, but its external interface or behavior might evolve between dot releases.

You can configure publishers in the `publishers` configuration section under the top level `metrics` node or under `server.features.observe.observers.metrics`. If you do not set up publishers explicitly, Helidon uses an inferred Prometheus publisher for backward compatibility. See [this later section](#understanding-the-inferred-prometheus-publisher) for details.

Publishers in Helidon’s Micrometer-based metrics implementation use Micrometer `MeterRegistry` implementations. For each enabled publisher, Helidon adds the corresponding meter registry to Micrometer’s global registry. This has these important effects:

- Meters which Helidon or your code registers using the Helidon metrics API are registered in all active Micrometer meter registries.
- Each Helidon meter registered has an implementation in every active Micrometer meter registry.
- When Helidon or your code updates a Helidon meter, Micrometer applies the change to every corresponding meter from each active meter registry.

As a result, configuring more than one active meter registry can affect performance.

> [!NOTE]
> Make sure at least one of the configured publishers is enabled. If not, Micrometer does not have any active meter registry implementations and the registered metrics are no-ops. Helidon logs a warning in this case during the metrics observer initialization.

##### Configuring an OTLP Publisher

If you configure an OTLP publisher, Helidon exports metrics data periodically to a backend system you configure.

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5a031-aggregation-temporality"></span> [`aggregation-temporality`](../../config/io_micrometer_registry_otlp_AggregationTemporality.md) | `VALUE` | `i.m.r.o.AggregationTemporality` | `CUMULATIVE` | Algorithm to use for adjusting values before transmission |
| <span id="a726ba-base-time-unit"></span> [`base-time-unit`](../../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` | `java.util.concurrent.TimeUnit.MILLISECONDS` | Base time unit for timers |
| <span id="ace1fb-batch-size"></span> `batch-size` | `VALUE` | `Integer` | `10000` | Number of measurements to send in a single request to the backend |
| <span id="a6b5d5-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the configured publisher is enabled |
| <span id="a821e5-headers"></span> `headers` | `MAP` | `String` |   | Headers to add to each transmission message |
| <span id="afbfb5-interval"></span> `interval` | `VALUE` | `Duration` | `PT60s` | Interval between successive transmissions of metrics data |
| <span id="a65cf0-max-bucket-count"></span> `max-bucket-count` | `VALUE` | `Integer` | `160` | Maximum bucket count to apply to statistical histogram |
| <span id="a11feb-max-buckets-per-meter"></span> `max-buckets-per-meter` | `MAP` | `Integer` |   | Maximum number of buckets to use for specific meters |
| <span id="a52180-max-scale"></span> `max-scale` | `VALUE` | `Integer` | `20` | Maximum scale value to apply to statistical histogram |
| <span id="a00636-name"></span> `name` | `VALUE` | `String` |   | `N/A` |
| <span id="a64095-prefix"></span> `prefix` | `VALUE` | `String` | `otlp` | The prefix for settings |
| <span id="afb329-properties"></span> `properties` | `MAP` | `String` |   | Property values to be returned by the OTLP meter registry configuration |
| <span id="a5f081-resource-attributes"></span> `resource-attributes` | `MAP` | `String` |   | Attribute name/value pairs to be associated with all metrics transmissions |
| <span id="a1f8b3-url"></span> `url` | `VALUE` | `String` | `http://localhost:4318/v1/metrics` | URL to which to send metrics telemetry |

The configuration directly mirrors the Micrometer `OtlpMeterRegistry` settings so you can control all behavior which Micrometer exposes for the meter registry.

The following example sets up an OTLP publisher to transmit metrics data every 30 seconds.

*Example OTLP publisher settings*

```yaml
metrics:
  publishers:         
    otlp:  
      interval: PT30S
      url: 'http://somehost.com:4318/v1/metrics'
```

- Introduces the configured publishers.
- Configures an OTLP publisher to transmit every 30 seconds to the given endpoint.

##### Configuring a Prometheus Publisher

If you configure a Prometheus publisher or rely on the inferred one, Helidon can make the metrics data available in the Prometheus/OpenMetrics format. (To serve the data at the metrics endpoint in your service, your project must also depend on the Helidon metrics observer component.)

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6614e-descriptions"></span> `descriptions` | `VALUE` | `Boolean` |   | Whether to include meter descriptions in Prometheus output |
| <span id="a248f8-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the configured publisher is enabled |
| <span id="ae8bbc-interval"></span> `interval` | `VALUE` | `Duration` |   | Step size used in computing "windowed" statistics |
| <span id="abd446-name"></span> `name` | `VALUE` | `String` |   | `N/A` |
| <span id="a3221e-prefix"></span> `prefix` | `VALUE` | `String` |   | Property name prefix |

##### Understanding the Inferred Prometheus Publisher

As described earlier, Helidon prepares an inferred Prometheus publisher if you do not set up any publishers.

Note that Helidon uses the inferred publisher *only* if you add *no* publishers explicitly, either in the configuration or programmatically. If you specify any publishers explicitly, Helidon uses only the ones you set up.

In particular, Helidon *does not* use the inferred Prometheus publisher if you create a `metrics.publishers` section containing only an OTLP publisher.

You can configure other publishers and still have Helidon use the default one by simply adding the `prometheus` publisher entry. You do not need to specify further settings for it.

*Using an OLTP publisher **and** the default Prometheus publisher*

```yaml
metrics:
  publishers:
    prometheus:
    otlp:
      interval: PT20S
```

#### Writing Additional Publishers

You can write other publishers by following these steps:

1.  Choose one of the Micrometer `MeterRegistry` implementations for the type of publishing you want to support. (for example [`DatadogMeterRegistry`](https://github.com/micrometer-metrics/micrometer/tree/main/implementations/micrometer-registry-datadog))
2.  Create a config blueprint which exposes the meter registry’s [settable properties from `DatadogConfig`](https://github.com/micrometer-metrics/micrometer/blob/main/implementations/micrometer-registry-datadog/src/main/java/io/micrometer/datadog/DatadogConfig.java).
3.  Write a `DatadogPublisher` class which implements Helidon’s `MetricsPublisher` for Datadog.
4.  Write a `DatadogPublisherProvider` class which implements Helidon’s `MetricsPublisherProvider` for your publisher.
5.  Advertise your provider so Java service loading can find it, creating a `META-INF/services/io.helidon.metrics.spi.PublisherProvider` file listing your implementation class.

Look at Helidon’s [OTLP publisher blueprint]({https://github.com/helidon-io/helidon/tree/main/metrics/providers/micrometer/src/main/java/io/helidon/metrics/providers/micrometer/OtlpPublisherConfigBlueprint.java) and the related types as an example.

Refer to your publisher in configuration using the config key you set up in the publisher provider.

*Example config using a hypothetical Datadog publisher*

```yaml
metrics:
  publishers:
    micrometer-datadog:
      interval: PT15S
```

#### Using and Controlling the Metrics Endpoint

When you add the `helidon-webserver-observe-metrics` dependency to your project, and if you explicitly set up a Prometheus publisher or use the default one, Helidon provides a built-in REST endpoint `/observe/metrics` which responds with a report of the registered meters and their values.

Clients can request a particular output format from the endpoint.

| Format                   | Requested by                      |
|--------------------------|-----------------------------------|
| OpenMetrics (Prometheus) | default (`text/plain`)            |
| JSON                     | Header `Accept: application/json` |

Formats for `/observe/metrics` output

<a id="scope-specific-retrieval"></a>
Clients can also limit the report by specifying the scope as a query parameter in the request URL:

- `/observe/metrics?scope=base`
- `/observe/metrics?scope=vendor`
- `/observe/metrics?scope=application`

Further, clients can narrow down to a specific metric name by adding the name as another query parameter, such as `/observe/metrics?scope=application&name=myCount`.

*Example Reporting: Prometheus format*

```bash
curl -s -H 'Accept: text/plain' -X GET http://localhost:8080/observe/metrics
```

```text
# HELP classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
# TYPE classloader_loadedClasses_count gauge
classloader_loadedClasses_count{scope="base",} 5297.0
```

See the summary of the [OpenMetrics and Prometheus Format](#openmetrics-and-prometheus-format) for more information.

*Example Reporting: JSON format*

```bash
curl -s -H 'Accept: application/json' -X GET http://localhost:8080/observe/metrics
```

*JSON response:*

```json
{
   "base" : {
      "memory.maxHeap" : 3817865216,
      "memory.committedHeap" : 335544320
    }
}
```

In addition to your application meters, the reports contain other meters of interest such as system and VM information.

#### OpenMetrics and Prometheus Format

The [OpenMetrics format](https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md) and the [Prometheus exposition format](https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md) are very similar in most important respects but are not identical. This brief summary treats them as the same.

The OpenMetrics/Prometheus format represents each meter using three lines of output as summarized in the following table.

| Line prefix | Purpose | Format |
|----|----|----|
| `# TYPE` | Displays the scope, name, and type of the meter | `TYPE <scope>:<output-name> <meter-type>` |
| `# HELP` | Displays the scope, name, and description of the meter | `HELP <scope>:<output-name> <registered description>` |
| (none) | Displays the scope, meter ID, and current value of the meter | `<scope>:<output-name> <current value>` |

OpenMetrics/Prometheus format

The OpenMetrics/Prometheus output converts meter IDs in these ways:

- Names in camel case are converted to "snake case" and dots are converted to underscores.
- Names include any units specified for the meter.
- For percentiles, the ID includes a tag identifying which percentile the line of output describes.

As the earlier example output showed, for a meter with multiple values, such as a timer or a distribution summary, (with, among others, `max`, `mean`, and `count`), the OpenMetrics/Prometheus output reports a "metric family" which includes a separate family member meter for each of the multiple values. The name for each member in the family is derived from the registered name for the meter plus a suffix indicating which one of the meter’s multiple values the line refers to.

The following table summarizes the naming for each meter type.

<table>
<caption>OpenMetrics/Prometheus Meter Naming</caption>
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Meter Type</th>
<th style="text-align: left;">Example registered name</th>
<th style="text-align: left;">Meter family member</th>
<th style="text-align: left;">Name Suffix</th>
<th style="text-align: left;">Example displayed name</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>Counter</code></p></td>
<td style="text-align: left;"><p><code>requests.count</code></p></td>
<td style="text-align: left;"><p>count</p></td>
<td style="text-align: left;"><p><code>_total</code></p></td>
<td style="text-align: left;"><p><code>requests_count_total</code></p></td>
</tr>
<tr>
<td rowspan="4" style="text-align: left;"><p><code>DistributionSummary</code></p></td>
<td rowspan="4" style="text-align: left;"><p><code>nameLengths</code></p></td>
<td style="text-align: left;"><p>count</p></td>
<td style="text-align: left;"><p><code>_count</code></p></td>
<td style="text-align: left;"><p><code>nameLengths_count</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p>sum</p></td>
<td style="text-align: left;"><p><code>_sum</code></p></td>
<td style="text-align: left;"><p><code>nameLengths_sum</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p>max</p></td>
<td style="text-align: left;"><p><code>_max</code></p></td>
<td style="text-align: left;"><p><code>nameLengths_max</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p>percentile</p></td>
<td style="text-align: left;"><p>none</p></td>
<td style="text-align: left;"><p><code>nameLengths{scope="base",quantile="0.5",}</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>Gauge</code></p></td>
<td style="text-align: left;"><p><code>classloader.loadedClasses.count</code></p></td>
<td style="text-align: left;"><p>value</p></td>
<td style="text-align: left;"><p>none</p></td>
<td style="text-align: left;"><p><code>classloader_loadedClasses_count</code></p></td>
</tr>
<tr>
<td rowspan="4" style="text-align: left;"><p><code>Timer</code> <sup>1</sup></p></td>
<td rowspan="4" style="text-align: left;"><p><code>vthreads.recentPinned</code></p></td>
<td style="text-align: left;"><p>count</p></td>
<td style="text-align: left;"><p><code>_count</code></p></td>
<td style="text-align: left;"><p><code>vthreads_recentPinned_seconds_count</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p>sum</p></td>
<td style="text-align: left;"><p><code>_sum</code></p></td>
<td style="text-align: left;"><p><code>vthreads_recentPinned_seconds_sum</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p>max</p></td>
<td style="text-align: left;"><p><code>_max</code></p></td>
<td style="text-align: left;"><p><code>vthreads_recentPinned_seconds_max</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p>percentile</p></td>
<td style="text-align: left;"><p>none</p></td>
<td style="text-align: left;"><p><code>vthreads_recentPinned_seconds{scope="base",quantile="0.5",}</code></p></td>
</tr>
</tbody>
</table>

<sup>1</sup> The OpenMetrics/Prometheus output format reports a timer as a `summary` with units of `seconds`.

#### JSON Format

Unlike OpenMetrics/Prometheus output, which combines the data and the metadata in a single response, you use an HTTP `GET` request to retrieve metrics JSON *data* and an `OPTIONS` request to retrieve *metadata* in JSON format.

Helidon groups meters in the same scope together in JSON output as shown in the following example.

*JSON metrics output structured by scope (partial)*

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

If an HTTP request [selects by scope](#scope-specific-retrieval), the output omits the extra level of structure that identifies the scope as shown in the following example.

*JSON metrics output for the `base` scope (partial)*

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

*JSON output for a single-valued meter (for example, `Counter`)*

```json
"requests.count": 5
```

*JSON output for a multi-valued meter (for example, `Timer`)*

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

*Example `Counter` metadata*

```json
"requests.count": {
  "type": "counter",
  "description": "Each request (regardless of HTTP method) will increase this counter"
    }
```

*Example `Timer` metadata*

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

*Setting default timer units for JSON in `application.yaml`*

```yaml
metrics:
  timers:
    json-units-default: units 
```

- For *units* specify any valid name for a [`TimeUnit`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html) value (`SECONDS`, `MILLISECONDS`, etc.)

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

1.  Use the static `globalRegistry` method on the [`Metrics`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Metrics.html) interface to get a reference to the global [`MeterRegistry`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/MeterRegistry.html) instance.
2.  Use the `MeterRegistry` instance to register new meters and look up previously-registered meters.
3.  Use the meter reference returned from the `MeterRegistry` to update the meter or get its value.

You can also use the `MeterRegistry` to remove an existing meter.

### Helidon Metrics API

The Helidon Metrics API defines the classes and interfaces for meter types and other related items.

The following table summarizes the meter types.

| Meter Type | Usage |
|----|----|
| [`Counter`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Counter.html) | Monotonically increasing count of events. |
| [`Gauge`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Gauge.html) | Access to a value managed by other code in the service. |
| [`DistributionSummary`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/DistributionSummary.html) | Calculates the distribution of a value. |
| [`Timer`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.html) | Frequency of invocations and the distribution of how long the invocations take. |

Meter Types

Each meter type has its own set of methods for updating and retrieving the value.

### The `MeterRegistry` API

To register or look up meters programmatically, your service code uses the global `MeterRegistry`. Simply invoke `Metrics.globalRegistry()` to get a reference to the global meter registry.

To locate an existing meter or register a new one, your code:

1.  Creates a builder of the appropriate type of meter, setting the name and possibly other characteristics of the meter.
2.  Invokes the `MeterRegistry.getOrCreate` method, passing the builder.

The meter registry returns a reference to a previously-registered meter with the specified name and tags or, if none exists, a newly-registered meter. Your code can then operate on the returned meter as needed to record new measurements or retrieve existing data.

The example code in the [Examples](#examples) section below illustrates how to register, retrieve, and update meters.

#### Understanding Timers, Units, and Output

Your application can assign the meter builder’s [`Meter.Builder baseUnit`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Meter.Builder.html#baseUnit(java.lang.String)) setting for any meter your application creates. In particular, the [`Timer.Builder baseUnit`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Timer.Builder.html#baseUnit(java.util.concurrent.TimeUnit)) method allows code to assign a `baseUnit` for a timer, passing a Java [`TimeUnit`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html) value. The timer builder also has the `String` variant of the `baseUnit` method and enforces that the value corresponds (case-insensitively) to one of the `TimeUnit` enum values.

Note that, regardless of the `baseUnit` setting for a `Timer`, by convention and specification Prometheus output expresses time values in `seconds`.

By default, the same is true of Helidon’s JSON format: timer values are displayed in `seconds` regardless of any timer’s `baseUnit` setting. You can override this as described in the [Controlling Timer Output](#controlling-json-timer-output) section, in which case the JSON output for each timer reflects its `baseUnit` setting.

### Accessing the Underlying Implementation: `unwrap`

The neutral Helidon metrics API is an abstraction of common metrics behavior independent from any given implementation. As such, we intentionally excluded some implementation-specific behavior from the API.

Sometimes you might want access to methods that are present in a particular metrics implementation but not in the Helidon API. Helidon allows that via the `unwrap` method on the meter types and on their builders. Each full implementation of the Helidon meter types and their builders refers to a delegate meter or delegate builder internally. The `unwrap` method lets you obtain the delegate, cast to the type you want.

Of course, using this technique binds your code to a particular metrics implementation.

The [`Wrapper`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/Wrapper.html) interface declares the `unwrap` method which accepts a class parameter to which the delegate is cast. You can then invoke any method declared on the implementation-specific type.

## Configuration

To control how the Helidon metrics subsystem behaves, add a `metrics` section to your configuration file, such as `application.yaml`.

Certain default configuration values depend on the fact that you are using Helidon SE as described in the [second table below](#flavor-specific-defaults).

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ab3e25-app-name"></span> `app-name` | `VALUE` | `String` |   | Value for the application tag to be added to each meter ID |
| <span id="a0590e-app-tag-name"></span> `app-tag-name` | `VALUE` | `String` |   | Name for the application tag to be added to each meter ID |
| <span id="a24eaf-built-in-meter-name-format"></span> [`built-in-meter-name-format`](../../config/io_helidon_metrics_api_BuiltInMeterNameFormat.md) | `VALUE` | `i.h.m.a.BuiltInMeterNameFormat` | `CAMEL` | Output format for built-in meter names |
| <span id="aac68d-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether metrics functionality is enabled |
| <span id="a90f80-key-performance-indicators"></span> [`key-performance-indicators`](../../config/io_helidon_metrics_api_KeyPerformanceIndicatorMetricsConfig.md) | `VALUE` | `i.h.m.a.KeyPerformanceIndicatorMetricsConfig` |   | Key performance indicator metrics settings |
| <span id="acbf94-permit-all"></span> `permit-all` | `VALUE` | `Boolean` | `true` | Whether to allow anybody to access the endpoint |
| <span id="ae7437-publishers"></span> [`publishers`](../../config/io_helidon_metrics_api_MetricsPublisher.md) | `LIST` | `i.h.m.a.MetricsPublisher` |   | Metrics publishers which make the metrics data available to external systems |
| <span id="af1711-publishers-discover-services"></span> `publishers-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `publishers` |
| <span id="a1e75d-rest-request-enabled"></span> `rest-request.enabled` | `VALUE` | `Boolean` | `false` | Whether automatic REST request metrics should be measured |
| <span id="a3d689-roles"></span> `roles` | `LIST` | `String` | `observe` | Hints for role names the user is expected to be in |
| <span id="ae0eb0-scoping"></span> [`scoping`](../../config/io_helidon_metrics_api_ScopingConfig.md) | `VALUE` | `i.h.m.a.ScopingConfig` |   | Settings related to scoping management |
| <span id="a995f3-tags"></span> `tags` | `LIST` | `i.h.m.a.MetricsConfigSupport` |   | Global tags |
| <span id="a977e0-timers-json-units-default"></span> [`timers.json-units-default`](../../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` |   | Default units for timer output in JSON if not specified on a given timer |
| <span id="a9178f-virtual-threads-enabled"></span> `virtual-threads.enabled` | `VALUE` | `Boolean` | `false` | Whether Helidon should expose meters related to virtual threads |
| <span id="a628d9-virtual-threads-pinned-threshold"></span> `virtual-threads.pinned.threshold` | `VALUE` | `Duration` | `PT0.020S` | Threshold for sampling pinned virtual threads to include in the pinned threads meter |
| <span id="adb8b4-warn-on-multiple-registries"></span> `warn-on-multiple-registries` | `VALUE` | `Boolean` | `true` | Whether to log warnings when multiple registries are created |

#### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a12103-gc-time-type"></span> [`gc-time-type`](../../config/io_helidon_metrics_api_GcTimeType.md) | `VALUE` | `i.h.m.a.GcTimeType` | `COUNTER` | Whether the `gc.time` meter should be registered as a gauge (vs |
| <span id="aa1220-rest-request-enabled"></span> `rest-request-enabled` | `VALUE` | `Boolean` |   | Whether automatic REST request metrics should be measured (as indicated by the deprecated config key `rest-request-enabled`, the config key using a hyphen instead of a dot separator) |

<a id="flavor-specific-defaults"></a>
| Key                | Default Value |
|--------------------|---------------|
| `app-tag-name`     | `app`         |
| `scoping.tag-name` | `scope`       |
| `scoping.default`  | `application` |

Default Values Specific to Helidon SE

### Controlling the Meter Type for `gc.time`

To date Helidon 4 releases have implemented the system-provided meter `gc.time` as a counter. In fact, a gauge is more suitable for the approximate time the JVM has spent doing garbage collection.

Helidon 4.4.0-SNAPSHOT continues to use a counter by default to preserve backward compatibility, but you can choose to use a gauge by setting the configuration property `metrics.gc-time-type` to `gauge`. You can also set the config property to `counter` which is the default.

Why should you care? In fact, this distinction might not make a difference for many users. But for others the differences between the programmatic APIs for `Counter` and `Gauge` would affect application code that works directly with the `gc-time` meter. Further, the difference in output—​particularly in the OpenMetrics/Prometheus format—​might affect their application or downstream monitoring tools.

The ability to choose the meter type for `gc.time` is deprecated and is planned for removal in a future major release of Helidon at which time Helidon will always use a gauge.

### Controlling the Metrics Observer

Helidon can make the registered meters and their current values available externally at an endpoint (/observe/metrics by default). You can control aspects of how Helidon furnishes this information under the `server.features.observe.observers.metrics` configuration section.

| key | type | default value | description |
|----|----|----|----|
| `auto` | [AutoHttpMetricsConfig](../../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsConfig.md) |   | Automatic metrics collection settings. |
| `enabled` | boolean | `true` | Whether this observer is enabled. |
| `endpoint` | string | `/observe/metrics` | Path at which clients can retrieve metrics information. |

Optional configuration options

See the [Helidon OpenTelemetry documentation](../../se/telemetry/open-telemetry.md#enabling-automatic-metrics-for-inbound-http) for more information.

#### Selecting REST Endpoints for Automatic Measurement

You can choose which endpoints to include in Helidon’s automatic measurements using the `auto-http-metrics` config section.

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9ac57-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether automatic metrics collection as a whole is enabled |
| <span id="a61ecb-opt-in"></span> `opt-in` | `LIST` | `String` |   | Elective attribute for which to opt in |
| <span id="a6fb0d-paths"></span> [`paths`](../../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsPathConfig.md) | `LIST` | `i.h.w.o.m.AutoHttpMetricsPathConfig` |   | Automatic metrics collection settings |
| <span id="af4ffb-sockets"></span> `sockets` | `LIST` | `String` |   | Socket names for sockets to be instrumented with automatic metrics |

The `paths` section contains zero or more entries, each entry having the following settings:

<table>
<caption><code>path</code> entry settings</caption>
<colgroup>
<col style="width: 13%" />
<col style="width: 6%" />
<col style="width: 13%" />
<col style="width: 66%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Key</th>
<th style="text-align: left;">Required</th>
<th style="text-align: left;">Default Value</th>
<th style="text-align: left;">Usage</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>path</code></p></td>
<td style="text-align: left;"><p>yes</p></td>
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p>Path-matching expression:</p>
<ul>
<li><p>an exact match (<code>/greet</code>)</p></li>
<li><p>a prefix match (<code>/greet/*</code>)</p></li>
<li><p>a pattern match (<code>/greet/{name}</code>)</p></li>
</ul></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>methods</code></p></td>
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p>all HTTP method types</p></td>
<td style="text-align: left;"><p>Which HTTP methods match this entry</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>enabled</code></p></td>
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p><code>true</code></p></td>
<td style="text-align: left;"><p>Whether requests that match this entry should be measured</p></td>
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

*Including and Excluding Endpoints from Automatic Measurement*

```yaml
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

The [AutoHttpMetricsConfig documentation](../../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsConfig.md) describes the configuration more fully.

## Examples

Helidon SE includes several pre-written example applications illustrating aspects of metrics:

- [Enabling/disabling meters](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/metrics/filtering/se) using `MetricsObserver` and `MetricsConfig`
- [Controlling key performance indicator metrics](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/metrics/kpi) using configuration and `KeyPerformanceIndicatorMetricsSettings`.

The rest of this section shows how to add a custom meter to your code and how to configure the Helidon metrics subsystem.

### Example Application Code

The following example, based on the Helidon SE QuickStart application, shows how to register and update a new `Counter` in application code. The counter tracks the number of times any of the service endpoints is accessed.

*Define and use a `Counter`*

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

- Get the global meter registry.
- Create (or find) a counter named "accessctr" in the global registry.
- Route every request to the `countAccess` method.
- Increment the access counter for every request.

Perform the following steps to see the new counter in action.

*Build and run the application*

```bash
mvn package
java -jar target/helidon-quickstart-se.jar
```

*Retrieve `application` metrics*

```bash
curl 'http://localhost:8080/observe/metrics?scope=application' 
```

*Response*

```text
# HELP accessctr_total
# TYPE accessctr_total counter
accessctr_total{scope="application",} 0.0 
```

- Access the metrics endpoint, selecting only application meters.
- Note the counter is zero; we have not accessed a service endpoint yet.

*Access a service endpoint to retrieve a greeting*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{"message":"Hello World"}
```

*Retrieve `application` metrics again*

```bash
curl 'http://localhost:8080/observe/metrics?scope=application'
```

*Response*

```text
# HELP accessctr_total
# TYPE accessctr_total counter
accessctr_total{scope="application",} 1.0 
```

- The counter now reports 1, reflecting our earlier access to the `/greet` endpoint.

### Example Configuration

Metrics configuration is quite extensive and powerful and, therefore, a bit complicated. The rest of this section illustrates some of the most common scenarios:

- [Disable metrics entirely.](#disable-metrics-subsystem)
- [Choose whether to report virtual threads meters](#configuring-virtual-threads-meters).
- [Choose whether to collect extended key performance indicator metrics.](#collecting-basic-and-extended-key-performance-indicator-kpi-meters)

#### Disable Metrics Subsystem

*Disabling metrics entirely*

```yaml
server:
  features:
    observe:
      observers:
        metrics:
          enabled: false
```

Helidon does not update metrics, and the `/observe/metrics` endpoints respond with `404`.

#### Configuring Virtual Threads Meters

##### Enabling Virtual Threads Meters

Gathering data to compute the meters for virtual threads is designed to be as efficient as possible, but doing so still imposes a load on the server and by default Helidon does not report meters related to virtual threads.

To enable the meters describing virtual threads include a config setting as shown in the following example.

*Enabling virtual thread meters*

```yaml
metrics:
  virtual-threads:
    enabled: true
```

##### Controlling Measurements of Pinned Virtual Threads

Helidon measures pinned virtual threads only when the thread is pinned for a length of time at or above a threshold. Control the threshold as shown in the example below.

*Setting virtual thread pinning threshold to 100 ms*

```yaml
metrics:
  virtual-threads:
    pinned:
      threshold: PT0.100S
```

The threshold value is a `Duration` string, such as `PT0.100S` for 100 milliseconds.

#### Collecting Basic and Extended Key Performance Indicator (KPI) Meters

Any time you include the Helidon metrics module in your application, Helidon tracks a basic performance indicator meter: a `Counter` of all requests received (`requests.count`)

Helidon SE also includes additional, extended KPI meters which are disabled by default:

- current number of requests in-flight - a `Gauge` (`requests.inFlight`) of requests currently being processed
- long-running requests - a `Counter` (`requests.longRunning`) measuring the total number of requests which take at least a given amount of time to complete; configurable, defaults to 10000 milliseconds (10 seconds)
- load - a `Counter` (`requests.load`) measuring the number of requests worked on (as opposed to received)
- deferred - a `Gauge` (`requests.deferred`) measuring delayed request processing (work on a request was delayed after Helidon received the request)

You can enable and control these meters using configuration:

*Controlling extended KPI meters*

```yaml
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

## Additional Information

### References

[Micrometer Metrics concepts documentation](https://docs.micrometer.io/micrometer/reference/concepts)

[OpenMetrics format](https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md)

[Prometheus exposition format](https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md)

### Support for the Prometheus Metrics API

- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [API](#api)

Helidon provides optional support for the Prometheus metrics API.

To use it, your service registers Prometheus support with your routing set-up. You can customize its configuration. For information about using Prometheus, see the Prometheus documentation: <https://prometheus.io/docs/introduction/overview/>.

> [!NOTE]
> Helidon’s fully-functional, built-in metrics implementation supports Prometheus (OpenMetrics) output. Use the optional support described in *this* section only if you want to use the Prometheus *API* from your application code.

#### Maven Coordinates

*Dependency for Helidon Prometheus API support*

```xml
<dependency>
    <groupId>io.helidon.metrics</groupId>
    <artifactId>helidon-metrics-prometheus</artifactId>
</dependency>
```

#### Usage

Your application code uses the Prometheus API to manage metrics. To expose those metrics to clients via a REST endpoint, your code uses the `PrometheusSupport` interface which Helidon provides.

#### API

Your code creates a [`PrometheusSupport`](/apidocs/io.helidon.metrics.prometheus/io/helidon/metrics/prometheus/PrometheusSupport.html) object either using a static factory method (shown in the following example) or by using its [`Builder`](/apidocs/io.helidon.metrics.prometheus/io/helidon/metrics/prometheus/PrometheusSupport.Builder.html).

```java
routing
        .addFeature(PrometheusSupport.create())
        .register("/myapp", new MyService());
```

This example uses the default Prometheus `CollectorRegistry`. By default, the `PrometheusSupport` and exposes its REST endpoint at the path `/metrics`. Use the builder obtained by `PrometheusSupport.builder()` to configure a different `CollectorRegistry` or a different path.
