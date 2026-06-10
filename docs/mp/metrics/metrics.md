# Metrics in Helidon MP

## Overview

Helidon MP metrics implements the MicroProfile Metrics specification, providing:

- a unified way for MicroProfile servers to export monitoring data—​telemetry—​to management agents, and
- a unified Java API which all application programmers can use to register and update metrics to expose telemetry data from their services.
- support for metrics-related annotations.

Learn more about the [MicroProfile Metrics specification][microprofile-met].

Metrics is one of the Helidon observability features.

> [!NOTE]
> Beginning with Helidon 4.1, strongly consider assigning the config setting
>
> ``` properties
> metrics.gc-time-type = gauge
> ```
>
> so your service complies with the MicroProfile Metrics 5.1 specification. See the [longer discussion below][longer-discussio] in the Configuration section.

## Maven Coordinates

To enable metrics, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../managing-dependencies.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.metrics</groupId>
  <artifactId>helidon-microprofile-metrics</artifactId>
</dependency>
```

Adding this dependency packages the full-featured metrics implementation with your service.

## Usage

### Instrumenting Your Service

You add metrics to your service in these ways:

- Annotate bean methods—​typically your REST resource endpoint methods (the Java code that receives incoming REST requests); Helidon automatically registers these metrics and updates them when the annotated methods are invoked via CDI.
- Write code which explicitly invokes the metrics API to register metrics, retrieve previously-registered metrics, and update metric values.
- Configure some simple `REST.request` metrics which Helidon automatically registers and updates for all REST resource endpoints.

Later sections of this document describe how to do each of these.

### Metric Types

Helidon supports meters described by the [MicroProfile Metrics][microprofile-met-2] spec and summarized in the following table:

|  |  |  |
|----|----|----|
| Metric Type | Description | Related MicroProfile annotation |
| [`Counter`][counter] | Monotonically-increasing `long` value. | [`@Counted`][counted] |
| [`Histogram`][histogram] | Summary of samples each with a `long` value. Reports aggregate information over all samples (count, total, mean, max) as well as the distribution of sample values using percentiles and bucket counts. | (none) |
| [`Timer`][timer] | Accumulation of short-duration (typically under a minute) intervals. Typically updated using a Java [`Duration`][duration] or by recording the time taken by a method invocation or lambda. Reports the count, total time, max, and mean; provides a histogram of the samples. | [`@Timed`][timed] |
| [`Gauge<? extends Number>`][gauge-extends-nu] | View of a value that is assignment-compatible with a subtype of Java [`Number`][number]. The underlying value is updated by code elsewhere in the system, not by invoking methods on the gauge itself. | [`@Gauge`][gauge] |

Types of Metrics

### Categorizing Types of Metrics

Helidon distinguishes among *scopes*, or categories, of metrics as described in the [MP metrics specification][microprofile-met-2].

Helidon includes metrics in the built-in scopes described below. Applications often register their own metrics in the `application` scope but can create their own scopes and register metrics within them.

| Built-in Scope | Typical Usage |
|----|----|
| `base` | OS or Java runtime measurements (available heap, disk space, etc.). Mandated by the MP metrics specification |
| `vendor` | Implemented by vendors, including the `REST.request` metrics and other key performance indicator measurements (described in later sections). |
| `application` | Declared via annotations or programmatically registered by your service code. |

Built-in metric scopes

When you add metrics annotations to your service code, Helidon registers the resulting metrics in the `application` scope.

### Metric Registries

A *metric registry* collects registered metrics of a given scope. Helidon supports one metrics registry for each scope.

When you add code to your service to create a metric programmatically, the code first locates the appropriate registry and then registers the metric with that registry.

### Publishing Metrics for External Access

Helidon’s Micrometer-based metrics implementation includes these ways of publishing metrics data to external systems:

- Prometheus/OpenMetrics
- OTLP (OpenTelemetry Protocol)

#### Configuring Publishers

> [!NOTE]
> The configuration of metrics publishers as described below is a [preview feature][preview-feature] which Helidon intends to keep, but its external interface or behavior might evolve between dot releases.

You can configure publishers in the `publishers` configuration section under the top level `metrics` node or under `server.features.observe.observers.metrics`. If you do not set up publishers explicitly, Helidon uses an inferred Prometheus publisher for backward compatibility. See [this later section][this-later-secti] for details.

Publishers in Helidon’s Micrometer-based metrics implementation use Micrometer `MeterRegistry` implementations. For each enabled publisher, Helidon adds the corresponding meter registry to Micrometer’s global registry. This has these important effects:

- {meters_uc} which Helidon or your code registers using the Helidon metrics API are registered in all active Micrometer meter registries.
- Each Helidon meter registered has an implementation in every active Micrometer meter registry.
- When Helidon or your code updates a Helidon {meter}, Micrometer applies the change to every corresponding {meter} from each active meter registry.

As a result, configuring more than one active meter registry can affect performance.

> [!NOTE]
> Make sure at least one of the configured publishers is enabled. If not, Micrometer does not have any active meter registry implementations and the registered metrics are no-ops. Helidon logs a warning in this case during the metrics observer initialization.

##### Configuring an OTLP Publisher

If you configure an OTLP publisher, Helidon exports metrics data periodically to a backend system you configure.

#### Configuration options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Headers to add to each transmission message</td>
</tr>
<tr>
<td>
<a id="base-time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>base-time-unit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">TimeUnit</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="java.util.concurrent.TimeUnit.MILLISECONDS">java.util.concurrent.TimeUnit.MILLISECONDS</code>
</td>
<td>Base time unit for timers</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">otlp</code>
</td>
<td>The prefix for settings</td>
</tr>
<tr>
<td>
<a id="aggregation-temporality"></a>
<a href="io.micrometer.registry.otlp.AggregationTemporality.md">
<code>aggregation-temporality</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="AggregationTemporality">AggregationTemporality</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">CUMULATIVE</code>
</td>
<td>Algorithm to use for adjusting values before transmission</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the configured publisher is enabled</td>
</tr>
<tr>
<td>
<code>url</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="http://localhost:4318/v1/metrics">http://localhost:4318/v1/metrics</code>
</td>
<td>URL to which to send metrics telemetry</td>
</tr>
<tr>
<td>
<code>max-scale</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">20</code>
</td>
<td>Maximum scale value to apply to statistical histogram</td>
</tr>
<tr>
<td>
<code>batch-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10000</code>
</td>
<td>Number of measurements to send in a single request to the backend</td>
</tr>
<tr>
<td>
<code>max-bucket-count</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">160</code>
</td>
<td>Maximum bucket count to apply to statistical histogram</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT60s</code>
</td>
<td>Interval between successive transmissions of metrics data</td>
</tr>
<tr>
<td>
<code>max-buckets-per-meter</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, Integer&gt;">Map&lt;String, Integer&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Maximum number of buckets to use for specific meters</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Property values to be returned by the OTLP meter registry configuration</td>
</tr>
<tr>
<td>
<code>resource-attributes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Attribute name/value pairs to be associated with all metrics transmissions</td>
</tr>
</tbody>
</table>
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

##### Configuring a Prometheus Publisher

If you configure a Prometheus publisher or rely on the inferred one, Helidon can make the metrics data available in the Prometheus/OpenMetrics format. (To serve the data at the metrics endpoint in your service, your project must also depend on the Helidon metrics observer component.)

#### Configuration options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Property name prefix</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Step size used in computing "windowed" statistics</td>
</tr>
<tr>
<td>
<code>descriptions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Whether to include meter descriptions in Prometheus output</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the configured publisher is enabled</td>
</tr>
</tbody>
</table>
<!--/include-->


##### Understanding the Inferred Prometheus Publisher

As described earlier, Helidon prepares an inferred Prometheus publisher if you do not set up any publishers.

Note that Helidon uses the inferred publisher *only* if you add *no* publishers explicitly, either in the configuration or programmatically. If you specify any publishers explicitly, Helidon uses only the ones you set up.

In particular, Helidon *does not* use the inferred Prometheus publisher if you create a `metrics.publishers` section containing only an OTLP publisher.

You can configure other publishers and still have Helidon use the default one by simply adding the `prometheus` publisher entry. You do not need to specify further settings for it.

Using an OLTP publisher **and** the default Prometheus publisher:

```yaml
metrics:
  publishers:
    prometheus:
    otlp:
      interval: PT20S
```

#### Writing Additional Publishers

You can write other publishers by following these steps:

1.  Choose one of the Micrometer `MeterRegistry` implementations for the type of publishing you want to support. (for example [`DatadogMeterRegistry`][datadogmeterregi])
2.  Create a config blueprint which exposes the meter registry’s [settable properties from `DatadogConfig`][settable-propert].
3.  Write a `DatadogPublisher` class which implements Helidon’s `MetricsPublisher` for Datadog.
4.  Write a `DatadogPublisherProvider` class which implements Helidon’s `MetricsPublisherProvider` for your publisher.
5.  Advertise your provider so Java service loading can find it, creating a `META-INF/services/io.helidon.metrics.spi.PublisherProvider` file listing your implementation class.

Look at Helidon’s [OTLP publisher blueprint][otlp-publisher-b] and the related types as an example.

Refer to your publisher in configuration using the config key you set up in the publisher provider.

Example config using a hypothetical Datadog publisher:

```yaml
metrics:
  publishers:
    micrometer-datadog:
      interval: PT15S
```

#### Using and Controlling the Metrics Endpoint

When you add the metrics dependency to your project, and if you explicitly set up a Prometheus publisher or use the default one, Helidon provides a built-in REST endpoint `/metrics` which responds with a report of the registered metrics and their values.

Clients can request a particular output format from the endpoint.

| Format                   | Requested by                      |
|--------------------------|-----------------------------------|
| OpenMetrics (Prometheus) | default (`text/plain`)            |
| JSON                     | Header `Accept: application/json` |

Formats for `/metrics` output

<a id="scope-specific-retrieval"></a>

Clients can also limit the report by specifying the scope as a query parameter in the request URL:

- `/metrics?scope=base`
- `/metrics?scope=vendor`
- `/metrics?scope=application`

Further, clients can narrow down to a specific metric name by adding the name as another query parameter, such as `/metrics?scope=application&name=myCount`.

Example Reporting: Prometheus format:

```shell [Terminal]
curl -s -H 'Accept: text/plain' -X GET http://localhost:8080/metrics
```

```text
# HELP classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
# TYPE classloader_loadedClasses_count gauge
classloader_loadedClasses_count{mp_scope="base",} 5297.0
```

See the summary of the [OpenMetrics and Prometheus Format][openmetrics-and] for more information.

Example Reporting: JSON format:

```shell [Terminal]
curl -s -H 'Accept: application/json' -X GET http://localhost:8080/metrics
```

JSON response:

```json
{
   "base" : {
      "memory.maxHeap" : 3817865216,
      "memory.committedHeap" : 335544320
    }
}
```

In addition to your application metrics, the reports contain other metrics of interest such as system and VM information.

#### OpenMetrics and Prometheus Format

The [OpenMetrics format][openmetrics-form] and the [Prometheus exposition format][prometheus-expos] are very similar in most important respects but are not identical. This brief summary treats them as the same.

The OpenMetrics/Prometheus format represents each metric using three lines of output as summarized in the following table.

| Line prefix | Purpose | Format |
|----|----|----|
| `# TYPE` | Displays the scope, name, and type of the metric | `TYPE <scope>:<output-name> <metric-type>` |
| `# HELP` | Displays the scope, name, and description of the metric | `HELP <scope>:<output-name> <registered description>` |
| (none) | Displays the scope, metric ID, and current value of the metric | `<scope>:<output-name> <current value>` |

OpenMetrics/Prometheus format

The OpenMetrics/Prometheus output converts metric IDs in these ways:

- Names in camel case are converted to "snake case" and dots are converted to underscores.
- Names include any units specified for the metric.
- For percentiles, the ID includes a tag identifying which percentile the line of output describes.

As the earlier example output showed, for a metric with multiple values, such as a timer or a histogram, (with, among others, `max`, `mean`, and `count`), the OpenMetrics/Prometheus output reports a "metric family" which includes a separate family member metric for each of the multiple values. The name for each member in the family is derived from the registered name for the metric plus a suffix indicating which one of the metric’s multiple values the line refers to.

The following table summarizes the naming for each metric type.

<table>
<caption>OpenMetrics/Prometheus Metric Naming</caption>
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Metric Type</th>
<th style="text-align: left;">Example registered name</th>
<th style="text-align: left;">Metric family member</th>
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
<td rowspan="4" style="text-align: left;"><p><code>Histogram</code></p></td>
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
<td style="text-align: left;"><p><code>nameLengths{mp_scope="base",quantile="0.5",}</code></p></td>
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
<td style="text-align: left;"><p><code>vthreads_recentPinned_seconds{mp_scope="base",quantile="0.5",}</code></p></td>
</tr>
</tbody>
</table>

<sup>1</sup> The OpenMetrics/Prometheus output format reports a timer as a `summary` with units of `seconds`.

#### JSON Format

Unlike OpenMetrics/Prometheus output, which combines the data and the metadata in a single response, you use an HTTP `GET` request to retrieve metrics JSON *data* and an `OPTIONS` request to retrieve *metadata* in JSON format.

Helidon groups metrics in the same scope together in JSON output as shown in the following example.

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

If an HTTP request [selects by scope](#scope-specific-retrieval), the output omits the extra level of structure that identifies the scope as shown in the following example.

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

The Helidon JSON format expresses each metric as either a single value (for example, a counter) or a structure with multiple values (for example, a timer).

JSON output for a single-valued metric (for example, Counter):

```json
"requests.count": 5
```

JSON output for a multi-valued metric (for example, Timer):

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

Generally, the output for a given metric reflects only the metadata that the application or Helidon code explicitly set on that metric.

One exception is that metadata for a timer always includes the `unit` field. By default, Helidon formats timer data in JSON output as seconds, regardless of any explicit `baseUnit` setting applied to the timers. But as [described below](#controlling-json-timer-output) you can change this behavior which can lead to different timers being formatted using different units. Checking the metadata is the only way to know for sure what units Helidon used to express a given timer, so Helidon always includes `unit` in timer metadata.

##### Controlling JSON Timer Output

By default, Helidon expresses timer data as seconds.

You can change this using configuration:

*Setting default timer units for JSON in `META-INF/microprofile-config.properties`*

metrics.timers.json-units-default=units

- For *units* specify any valid name for a [`TimeUnit`][timeunit] value (`SECONDS`, `MILLISECONDS`, etc.)

If you have configured `json-units-default`, Helidon formats each timer’s data as follows:

1.  If code set `baseUnit` on the timer, Helidon uses those units for that timer.
2.  Otherwise, Helidon uses the default units you configured.

To enable the JSON output behavior from Helidon 3, specify `json-units-default` as `NANOSECONDS`.

## API

The [MicroProfile Metrics API][microprofile-met-3] prescribes all the standard interfaces related to metrics. This section summarizes a few key points about using that API and explains some Helidon-specific interfaces.

### Metrics Annotations

You can very easily instrument your service and refer to registered metrics by annotating methods to be measured and injecting metrics which your code needs to observe.

#### Metric-defining Annotations

The MicroProfile Metrics specification describes several metric types you can create using annotations, summarized in the following table:

| Annotation | Usage |
|----|----|
| [`@Counted`][counted] | Automatically registers a monotonically-increasing `Counter` and increments it with each invocation of the annotated constructor or method. <sup>1</sup> |
| [`@Gauge`][gauge] | Automatically registers a `Gauge` whose value is provided by the annotated method. Code elsewhere in the system updates the underlying value. |
| [`@Timed`][timed] | Automatically registers a `Timer` and updates it with each invocation of the annotated constructor or method. <sup>1</sup> |

Metrics Annotations

<sup>1</sup> Place annotations on constructors or methods to measure those specific executables. If you annotate the class instead, Helidon applies that annotation to all constructors and methods which the class declares.

#### Metric-referencing Annotations

To get a reference to a specific metric, use a metric-referencing annotation in any bean, including your REST resource classes.

You can `@Inject` a field of the correct type. Helidon uses the MicroProfile Metrics naming conventions to select which specific metric to inject. Use the [`@Metric`][metric] annotation to control that selection.

You can also add `@Metric` on a constructor or method parameter to trigger injection there.

Helidon automatically looks up the metric referenced from any injection site and provides a reference to the metric. Your code then simply invokes methods on the injected metric.

### The `MetricRegistry` API

To register or look up metrics programmatically, your service code uses the [`MetricRegistry`][metricregistry] instance for the scope of interest: `base`, `vendor`, `application`, or a custom scope.

Either of the following techniques gets a `MetricRegistry` reference. Remember that injection works only if the class is a bean so CDI can inject into it.

- `@Inject MetricRegistry`, optionally using [`@RegistryScope`][registryscope] to indicate the registry scope.

  Injecting the default MetricRegistry (for the application scope):

  ```java
  class Example {

    @Inject
    private MetricRegistry applicationRegistry;
  }
  ```

  Injecting a non-default MetricRegistry:

  ```java
  class Example {

    @RegistryScope(scope = "myCustomScope")
    @Inject
    private MetricRegistry myCustomRegistry;
  }
  ```

- Get a Helidon [`RegistryFactory`][registryfactory] instance and invoke its `getRegistry` method.

  Obtain the `RegistryFactory` using either of the following techniques:

  - `@Inject RegistryFactory`.

    *Getting the `RegistryFactory` using injection*

```java
class InjectExample {

    @Inject
    private RegistryFactory registryFactory;

    private MetricRegistry findRegistry(String scope) {
        return registryFactory.getRegistry(scope);
    }
}
```

  - Invoke the static `getInstance()` method on the `RegistryFactory` class.

    *Getting the `RegistryFactory` programmatically*

```java
class Example {

    private MetricRegistry findRegistry(String scope) {
        return RegistryFactory.getInstance().getRegistry(scope);
    }
}
```

Once it has a reference to a `MetricRegistry` your code can use the reference to register new metrics, look up previously-registered metrics, and remove metrics.

### Working with Metrics in CDI Extensions

You can work with metrics inside your own CDI extensions, but be careful to do so at the correct point in the CDI lifecycle. Configuration can influence how the metrics system behaves, as the [configuration](#configuration) section below explains. Your code should work with metrics only after the Helidon metrics system has initialized itself using configuration. One way to accomplish this is to deal with metrics in a method that observes the Helidon `RuntimeStart` CDI event, which the [extension example below](#extension_example) illustrates.

## Configuration

To control how the Helidon metrics subsystem behaves, add a `metrics` section to your `META-INF/microprofile-config.properties` file.

Certain default configuration values depend on the fact that you are using Helidon MP as described in the [second table below](#flavor-specific-defaults).

### Configuration options

<!--@include ../../config/io.helidon.metrics.api.MetricsConfig.md#configuration-options offset=1 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>publishers-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>publishers</code></td>
</tr>
<tr>
<td>
<code>rest-request.enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether automatic REST request metrics should be measured</td>
</tr>
<tr>
<td>
<code>warn-on-multiple-registries</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to log warnings when multiple registries are created</td>
</tr>
<tr>
<td>
<code>roles</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">observe</code>
</td>
<td>Hints for role names the user is expected to be in</td>
</tr>
<tr>
<td>
<code>virtual-threads.pinned.threshold</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0.020S</code>
</td>
<td>Threshold for sampling pinned virtual threads to include in the pinned threads meter</td>
</tr>
<tr>
<td>
<a id="built-in-meter-name-format"></a>
<a href="io.helidon.metrics.api.BuiltInMeterNameFormat.md">
<code>built-in-meter-name-format</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="BuiltInMeterNameFormat">BuiltInMeterNameFormat</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">CAMEL</code>
</td>
<td>Output format for built-in meter names</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether metrics functionality is enabled</td>
</tr>
<tr>
<td>
<code>app-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Value for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td>
<code>tags</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;MetricsConfigSupport&gt;">List&lt;MetricsConfigSupport&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Global tags</td>
</tr>
<tr>
<td>
<code>app-tag-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td>
<code>virtual-threads.enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether Helidon should expose meters related to virtual threads</td>
</tr>
<tr>
<td>
<a id="publishers"></a>
<a href="io.helidon.metrics.api.MetricsPublisher.md">
<code>publishers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;MetricsPublisher&gt;">List&lt;MetricsPublisher&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Metrics publishers which make the metrics data available to external systems</td>
</tr>
<tr>
<td>
<a id="key-performance-indicators"></a>
<a href="io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig.md">
<code>key-performance-indicators</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="KeyPerformanceIndicatorMetricsConfig">KeyPerformanceIndicatorMetricsConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Key performance indicator metrics settings</td>
</tr>
<tr>
<td>
<code>permit-all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to allow anybody to access the endpoint</td>
</tr>
<tr>
<td>
<a id="scoping"></a>
<a href="io.helidon.metrics.api.ScopingConfig.md">
<code>scoping</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ScopingConfig">ScopingConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Settings related to scoping management</td>
</tr>
<tr>
<td>
<a id="timers-json-units-default"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>timers.json-units-default</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">TimeUnit</code>
</td>
<td class="cm-default-cell">
</td>
<td>Default units for timer output in JSON if not specified on a given timer</td>
</tr>
</tbody>
</table>


### Deprecated Options


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>rest-request-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Whether automatic REST request metrics should be measured (as indicated by the deprecated config key <code>rest-request-enabled</code>, the config key using a hyphen instead of a dot separator)</td>
</tr>
<tr>
<td>
<a id="gc-time-type"></a>
<a href="io.helidon.metrics.api.GcTimeType.md">
<code>gc-time-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">GcTimeType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">COUNTER</code>
</td>
<td>Whether the <code>gc.time</code> meter should be registered as a gauge (vs</td>
</tr>
</tbody>
</table>
<!--/include-->


<a id="flavor-specific-defaults"></a>
| Key                | Default Value |
|--------------------|---------------|
| `app-tag-name`     | `mp_app`      |
| `scoping.tag-name` | `mp_scope`    |
| `scoping.default`  | `application` |

Default Values Specific to Helidon MP

<a id="controlling-the-metric-type-for-gctime"></a>
### Controlling the Metric Type for `gc.time`

To date Helidon 4 releases have implemented the system-provided metric `gc.time` as a counter. In fact, a gauge is more suitable for the approximate time the JVM has spent doing garbage collection, and beginning with MicroProfile Metrics 5.1 the TCK relies on `gc.time` being a gauge.

Helidon 4.4.0-SNAPSHOT continues to use a counter by default to preserve backward compatibility, but you can choose to use a gauge by setting the configuration property `metrics.gc-time-type` to `gauge`. You can also set the config property to `counter` which is the default.

Why should you care? In fact, this distinction might not make a difference for many users. But for others the differences between the programmatic APIs for `Counter` and `Gauge` would affect application code that works directly with the `gc-time` metric. Further, the difference in output—​particularly in the OpenMetrics/Prometheus format—​might affect their application or downstream monitoring tools.

The ability to choose the metric type for `gc.time` is deprecated and is planned for removal in a future major release of Helidon at which time Helidon will always use a gauge.

### Controlling the Metrics Observer

Helidon can make the registered metrics and their current values available externally at an endpoint (/metrics by default). You can control aspects of how Helidon furnishes this information under the `server.features.observe.observers.metrics` configuration section.

| key | type | default value | description |
|----|----|----|----|
| `auto` | [AutoHttpMetricsConfig][autohttpmetricsc] |   | Automatic metrics collection settings. |
| `enabled` | boolean | `true` | Whether this observer is enabled. |
| `endpoint` | string | `/metrics` | Path at which clients can retrieve metrics information. |

Optional configuration options

#### Selecting REST Endpoints for Automatic Measurement

You can choose which endpoints to include in Helidon’s automatic measurements using the `auto-http-metrics` config section.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>opt-in</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Elective attribute for which to opt in</td>
</tr>
<tr>
<td>
<a id="paths"></a>
<a href="io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig.md">
<code>paths</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;AutoHttpMetricsPathConfig&gt;">List&lt;AutoHttpMetricsPathConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Automatic metrics collection settings</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Socket names for sockets to be instrumented with automatic metrics</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether automatic metrics collection as a whole is enabled</td>
</tr>
</tbody>
</table>
<!--/include-->


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
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p>Path-matching expression:</p>
<ul>
<li><p>an exact match (<code>/greet</code>)</p></li>
<li><p>a prefix match (<code>/greet/*</code>)</p></li>
<li><p>a pattern match (<code>/greet/{name}</code>)</p></li>
</ul></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>methods</code></p></td>
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p>all HTTP method types</p></td>
<td style="text-align: left;"><p>Which HTTP methods match this entry</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>enabled</code></p></td>
<td style="text-align: left;"><p> </p></td>
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

Including and Excluding Endpoints from Automatic Measurement:

```properties
server.features.observe.observers.metrics.auto-http-metrics.paths.0.path=/greet        
server.features.observe.observers.metrics.auto-http-metrics.paths.0.methods=GET,HEAD

server.features.observe.observers.metrics.auto-http-metrics.paths.1.path=/greet/{name} 
server.features.observe.observers.metrics.auto-http-metrics.paths.1.enabled=false

server.features.observe.observers.metrics.auto-http-metrics.sockets=@default,private   
```

- Measure `/greet` for only `GET` and `HEAD` requests.
- Do not measure the personalized greeting requests.
- Measure only endpoints on the default socket and the socket named `private`. Endpoints on other sockets (such as if you had an `admin` socket) are not measured.

The [AutoHttpMetricsConfig documentation][autohttpmetricsc] describes the configuration more fully.

## Examples

Helidon MP includes a pre-written example application illustrating [enabling/disabling metrics][enabling-disabli] using configuration.

The rest of this section contains other examples of working with metrics:

- [Example Application Code](#example-application-code)
- [Example Configuration](#example-configuration)

### Example Application Code

#### Adding Method-level Annotations

The following example adds a new resource class, `GreetingCards`, to the Helidon MP QuickStart example. It shows how to use the `@Counted` annotation to track the number of times the `/cards` endpoint is called.

Create a new class GreetingCards with the following code:

```java
@Path("/cards") 
@RequestScoped 
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "any-card")  
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some random cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- This class is annotated with `Path` which sets the path for this resource as `/cards`.
- The `@RequestScoped` annotation defines that this bean is request scoped. The request scope is active only for the duration of one web service invocation, and it is destroyed at the end of that invocation.
- The annotation `@Counted` will register a `Counter` metric for this method, creating it if needed. The counter is incremented each time the anyCards method is called. The `name` attribute is optional.

Build and run the application:

```shell [Terminal]
mvn package
java -jar target/helidon-quickstart-mp.jar
```

Access the application endpoints:

```shell [Terminal]
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

JSON response:

```text
{
  "io.helidon.examples.quickstart.mp.GreetingCards.any-card": 2, //  
  "personalizedGets": 0,
  "allGets": {
    "count": 0,
    "elapsedTime": 0,
    "max": 0,
    "mean": 0
  }
}
```

- The any-card count is two, since you invoked the endpoint twice. The other metrics are from the `SimpleGreetResource` class.

> [!NOTE]
> Notice the counter name is fully qualified with the class and method names. You can remove the prefix by using the `absolute=true` field in the `@Counted` annotation. You must use `absolute=false` (the default) for class-level annotations.

#### Additional Method-level Metrics

You can also use the @Timed\` annotation with a method. For the following example. you can just annotate the same method with `@Timed`. Timers significant information about the measured methods, but at a cost of some overhead and more complicated output.

Note that when using multiple annotations on a method, you **must** give the metrics different names as shown below, although they do not have to be absolute.

Update the GreetingCards class with the following code:

```java
@Path("/cards")
@RequestScoped
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "cardCount", absolute = true) 
    @Timed(name = "cardTimer", absolute = true, unit = MetricUnits.MILLISECONDS) 
    public JsonObject anyCard() {
        return createResponse("Here are some random cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- Specify a custom name for the `Counter` metric and set `absolute=true` to remove the path prefix from the name. \<2\>Add the `@Timed` annotation to get a `Timer` metric.

Build and run the application:

```shell [Terminal]
mvn package
java -jar target/helidon-quickstart-mp.jar
```

Access the application endpoints:

```shell [Terminal]
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

JSON response:

```json
{
  "cardTimer": {
    "count": 2,
    "elapsedTime": 0.002941925,
    "max": 0.002919973,
    "mean": 0.0014709625
  },
  "personalizedGets": 0,
  "allGets": {
    "count": 0,
    "elapsedTime": 0,
    "max": 0,
    "mean": 0
  },
  "cardCount": 2
}
```

#### Class-level Metrics

You can collect metrics at the class level to aggregate data from all methods in that class using the same metric. The following example introduces a metric to count all card queries. In the following example, the method-level metrics are not needed to aggregate the counts, but they are left in the example to demonstrate the combined output of all three metrics.

Update the GreetingCards class with the following code:

```java
@Path("/cards")
@RequestScoped
@Counted(name = "totalCards") 
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true) 
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some random cards ...");
    }

    @Path("/birthday")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true) 
    public JsonObject birthdayCard() throws InterruptedException {
        return createResponse("Here are some birthday cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- This class is now annotated with `@Counted`, which aggregates count data from all the method that have a `Count` annotation.
- Use `absolute=true` to remove path prefix for method-level annotations.
- Add a method with a `Counter` metric to get birthday cards.

Build and run the application:

```shell [Terminal]
mvn package
java -jar target/helidon-quickstart-mp.jar
```

Access the application endpoints:

```shell [Terminal]
curl http://localhost:8080/cards
curl http://localhost:8080/cards/birthday
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

JSON response from /metrics?scope=application:

```json
{
  "birthdayCard": 1,
  "personalizedGets": 0,
  "allGets": {
    "count": 0,
    "elapsedTime": 0,
    "max": 0,
    "mean": 0
  },
  "anyCard": 1,
  "io.helidon.examples.quickstart.mp.totalCards.GreetingCards": 2 
}
```

- The `totalCards.GreetingCards` count is a total of all the method-level `Counter` metrics. Class level metric names are always fully qualified.

#### Field Level Metrics

Field level metrics can be injected into managed objects, but they need to be updated by the application code. This annotation can be used on fields of type `Timer`, `Counter`, and `Histogram`.

The following example shows how to use a field-level `Counter` metric to track cache hits.

Update the GreetingCards class with the following code:

<!--@mdc ::code-collapse -->
```java
@Path("/cards")
@RequestScoped
@Counted(name = "totalCards")
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @Inject
    @Metric(name = "cacheHits", absolute = true) 
    private Counter cacheHits;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true)
    public JsonObject anyCard() throws InterruptedException {
        updateStats(); 
        return createResponse("Here are some random cards ...");
    }

    @Path("/birthday")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true)
    public JsonObject birthdayCard() throws InterruptedException {
        updateStats();  
        return createResponse("Here are some birthday cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }

    private void updateStats() {
        if (new Random().nextInt(3) == 1) {
            cacheHits.inc(); 
        }
    }
}
```
<!--@mdc :: -->

- A `Counter` metric field, `cacheHits`, is automatically injected by Helidon.
- Call `updateStats()` to update the cache hits.
- Call `updateStats()` to update the cache hits.
- Randomly increment the `cacheHits` counter.

Build and run the application, then invoke the following endpoints:

```shell [Terminal]
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl http://localhost:8080/cards/birthday
curl http://localhost:8080/cards/birthday
curl http://localhost:8080/cards/birthday
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

JSON response from /metrics/application:

```json
{
  "birthdayCard": 3,
  "personalizedGets": 0,
  "allGets": {
    "count": 0,
    "elapsedTime": 0,
    "max": 0,
    "mean": 0
  },
  "anyCard": 2,
  "cacheHits": 2, 
  "io.helidon.examples.quickstart.mp.totalCards.GreetingCards": 5
}
```

- The cache was hit two times out of five queries.

#### Gauge Metric

The metrics you have tested so far are updated in response to an application REST request, i.e. GET `/cards`. These metrics can be declared in a request scoped class and Helidon will store the metric in the `MetricRegistry`, so the value persists across requests. When GET `/metrics?scope=application` is invoked, Helidon will return the current value of the metric stored in the `MetricRegistry`.

The `Gauge` annotation is different from the other metric annotations. The application must provide a method to return the gauge value in an application-scoped class. When GET `/metrics?scope=application` is invoked, Helidon will call the `Gauge` method, using the returned value as the value of the gauge as part of the metrics response.

The following example demonstrates how to use a `Gauge` to track application up-time.

Create a new GreetingCardsAppMetrics class with the following code:

```java
@ApplicationScoped 
public class GreetingCardsAppMetrics {

    private AtomicLong startTime = new AtomicLong(0); 

    public void onStartUp(@Observes @Initialized(ApplicationScoped.class) Object init) {
        startTime = new AtomicLong(System.currentTimeMillis()); 
    }

    @Gauge(unit = "TimeSeconds")
    public long appUpTimeSeconds() {
        return Duration.ofMillis(System.currentTimeMillis() - startTime.get()).getSeconds();  
    }
}
```

- This managed object must be application scoped to properly register and use the annotated `Gauge` metric.
- Declare an `AtomicLong` field to hold the start time of the application.
- Initialize the application start time.
- Return the application `appUpTimeSeconds` metric, which will be included in the application metrics.

Update the GreetingCards class with the following code to simplify the metrics
output:

```java
@Path("/cards")
@RequestScoped
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "cardCount", absolute = true)
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some random cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

Build and run the application, then invoke the application metrics endpoint:

```shell [Terminal]
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

JSON response from /metrics/application:

```json
{
  "personalizedGets": 0,
  "allGets": {
    "count": 0,
    "elapsedTime": 0,
    "max": 0,
    "mean": 0
  },
  "io.helidon.examples.quickstart.mp.GreetingCardsAppMetrics.appUpTimeSeconds": 23, 
  "cardCount": 0
}
```

- The application has been running for 23 seconds.

#### Working with Metrics in CDI Extensions

<a id="extension_example"></a>
You can work with metrics from your own CDI extension by observing the `RuntimeStart` event.

CDI Extension that works correctly with metrics:

```java
public class MyExtension implements Extension {
    void startup(@Observes @RuntimeStart Object event,  
                 MetricRegistry metricRegistry) {       
        metricRegistry.counter("myCounter");         
    }
}
```

- Declares that your observer method responds to the `RuntimeStart` event. By this time, Helidon has initialized the metrics system.
- Injects a `MetricRegistry` (the application registry by default).
- Uses the injected registry to register a metric (a counter in this case).

<div class="informalexample">

Helidon does not prevent you from working with metrics earlier than the `RuntimeStart` event, but, if you do so, then Helidon might ignore certain configuration settings that would otherwise control how metrics behaves. Instead, consider writing your extension to use earlier lifecycle events (such as `ProcessAnnotatedType`) to gather and store information about metrics that you want to register. Then your extension’s `RuntimeStart` observer method would use that stored information to register the metrics you need.

</div>

### Example Configuration

Metrics configuration is quite extensive and powerful and, therefore, a bit complicated. The rest of this section illustrates some of the most common scenarios:

- [Disable metrics entirely.](#disable-metrics-subsystem)
- [Choose whether to report virtual threads metrics][choose-whether-t].
- [Choose whether to collect extended key performance indicator metrics.][choose-whether-t-2]
- [Control `REST.request` metrics collection.](#enable-restrequest-metrics)

#### Disable Metrics Subsystem

Disabling metrics entirely:

```properties
metrics.enabled=false
```

Helidon does not update metrics, and the `/metrics` endpoints respond with `404`.

#### Configuring Virtual Threads Metrics

##### Enabling Virtual Threads Metrics

Gathering data to compute the metrics for virtual threads is designed to be as efficient as possible, but doing so still imposes a load on the server and by default Helidon does not report metrics related to virtual threads.

To enable the metrics describing virtual threads include a config setting as shown in the following example.

Enabling virtual thread metrics:

```properties
metrics.virtual-threads.enabled = true
```

##### Controlling Measurements of Pinned Virtual Threads

Helidon measures pinned virtual threads only when the thread is pinned for a length of time at or above a threshold. Control the threshold as shown in the example below.

Setting virtual thread pinning threshold to 100 ms:

```properties
metrics.virtual-threads.pinned.threshold=PT0.100S
```

The threshold value is a `Duration` string, such as `PT0.100S` for 100 milliseconds.

#### Collecting Basic and Extended Key Performance Indicator (KPI) Metrics

Any time you include the Helidon metrics module in your application, Helidon tracks a basic performance indicator metric: a `Counter` of all requests received (`requests.count`)

Helidon MP also includes additional, extended KPI metrics which are disabled by default:

- current number of requests in-flight - a `Gauge` (`requests.inFlight`) of requests currently being processed
- long-running requests - a `Counter` (`requests.longRunning`) measuring the total number of requests which take at least a given amount of time to complete; configurable, defaults to 10000 milliseconds (10 seconds)
- load - a `Counter` (`requests.load`) measuring the number of requests worked on (as opposed to received)
- deferred - a `Gauge` (`requests.deferred`) measuring delayed request processing (work on a request was delayed after Helidon received the request)

You can enable and control these metrics using configuration:

Controlling extended KPI metrics:

```properties
metrics.key-performance-indicators.extended = true
metrics.key-performance-indicators.long-running.threshold-ms = 2000
```

#### Enable `REST.request` Metrics

Controlling REST request metrics:

```properties
metrics.rest-request.enabled=true
```

Helidon automatically registers and updates `Timer` metrics for every REST endpoint in your service.

## Additional Information

### Integration with Kubernetes and Prometheus

#### Kubernetes Integration

The following example shows how to integrate the Helidon MP application with Kubernetes.

Stop the application and build the docker image:

```shell [Terminal]
docker build -t helidon-metrics-mp .
```

Create the Kubernetes YAML specification, named `metrics.yaml`, with the
following content:

<!--@mdc ::code-collapse -->
```yaml [metrics.yaml]
kind: Service
apiVersion: v1
metadata:
  name: helidon-metrics 
  labels:
    app: helidon-metrics
  annotations:
    prometheus.io/scrape: "true" 
spec:
  type: NodePort
  selector:
    app: helidon-metrics
  ports:
    - port: 8080
      targetPort: 8080
      name: http
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: helidon-metrics
spec:
  replicas: 1 
  selector:
    matchLabels:
      app: helidon-metrics
  template:
    metadata:
      labels:
        app: helidon-metrics
        version: v1
    spec:
      containers:
        - name: helidon-metrics
          image: helidon-metrics-mp
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
```
<!--@mdc :: -->

- A service of type `NodePort` that serves the default routes on port `8080`.
- An annotation that will allow Prometheus to discover and scrape the application pod.
- A deployment with one replica of a pod.

Create and deploy the application into Kubernetes:

```shell [Terminal]
kubectl apply -f ./metrics.yaml
```

Get the service information:

```shell [Terminal]
kubectl get service/helidon-metrics
```

```shell [Terminal]
NAME             TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
helidon-metrics   NodePort   10.99.159.2   <none>        8080:31143/TCP   8s 
```

- A service of type `NodePort` that serves the default routes on port `31143`.

Verify the metrics endpoint using port 30116, your port will likely be different:

```shell [Terminal]
curl http://localhost:31143/metrics
```

> [!NOTE]
> Leave the application running in Kubernetes since it will be used for Prometheus integration.

#### Prometheus Integration

The metrics service that you just deployed into Kubernetes is already annotated with `prometheus.io/scrape:`. This will allow Prometheus to discover the service and scrape the metrics. This example shows how to install Prometheus into Kubernetes, then verify that it discovered the Helidon metrics in your application.

Install Prometheus and wait until the pod is ready:

```shell [Terminal]
helm install stable/prometheus --name metrics
export POD_NAME=$(kubectl get pods --namespace default -l "app=prometheus,component=server" -o jsonpath="{.items[0].metadata.name}")
kubectl get pod $POD_NAME
```

You will see output similar to the following. Repeat the `kubectl get pod` command until you see `2/2` and `Running`. This may take up to one minute.

```shell [Terminal]
metrics-prometheus-server-5fc5dc86cb-79lk4   2/2     Running   0          46s
```

Create a port-forward, so you can access the server URL:

```shell [Terminal]
kubectl --namespace default port-forward $POD_NAME 7090:9090
```

Now open your browser and navigate to `http://localhost:7090/targets`. Search for helidon on the page, and you will see your Helidon application as one of the Prometheus targets.

#### Final Cleanup

You can now delete the Kubernetes resources that were just created during this example.

Delete the Prometheus Kubernetes resources:

```shell [Terminal]
helm delete --purge metrics
```

Delete the application Kubernetes resources:

```shell [Terminal]
kubectl delete -f ./metrics.yaml
```

### References

[MicroProfile Metrics specification][microprofile-met-2]

[MicroProfile Metrics API][microprofile-met-3]

[OpenMetrics format][openmetrics-form]

[Prometheus exposition format][prometheus-expos]

[microprofile-met]: https://github.com/eclipse/microprofile-metrics/releases/tag/5.1.1
[longer-discussio]: #controlling-the-metric-type-for-gctime
[microprofile-met-2]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html
[counter]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/Counter.html
[counted]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/annotation/Counted.html
[histogram]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/Histogram.html
[timer]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/Timer.html
[duration]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Duration.html
[timed]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/annotation/Timed.html
[gauge-extends-nu]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/Gauge.html
[number]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java.lang.Number.html
[gauge]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/annotation/Gauge.html
[preview-feature]: https://helidon.io/docs/v4/apidocs/io.helidon.common.features.api/io/helidon/common/features/api/Preview.html
[this-later-secti]: #understanding-the-inferred-prometheus-publisher
[datadogmeterregi]: https://github.com/micrometer-metrics/micrometer/tree/main/implementations/micrometer-registry-datadog
[settable-propert]: https://github.com/micrometer-metrics/micrometer/blob/main/implementations/micrometer-registry-datadog/src/main/java/io/micrometer/datadog/DatadogConfig.java
[otlp-publisher-b]: {https://github.com/helidon-io/helidon/tree/main/metrics/providers/micrometer/src/main/java/io/helidon/metrics/providers/micrometer/OtlpPublisherConfigBlueprint.java
[openmetrics-and]: #openmetrics-and-prometheus-format
[openmetrics-form]: https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md
[prometheus-expos]: https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md
[timeunit]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html
[microprofile-met-3]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/package-summary.html
[metric]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/annotation/Metric.html
[metricregistry]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/MetricRegistry.html
[registryscope]: https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs/org/eclipse/microprofile/metrics/annotation/RegistryScope.html
[registryfactory]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.metrics/io/helidon/microprofile/metrics/RegistryFactory.html
[autohttpmetricsc]: ../../config/io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md
[enabling-disabli]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/metrics/filtering/mp
[choose-whether-t]: #configuring-virtual-threads-metrics
[choose-whether-t-2]: #collecting-basic-and-extended-key-performance-indicator-kpi-metrics
