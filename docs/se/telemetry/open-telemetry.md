# OpenTelemetry Support in Helidon SE

## Overview

> [!NOTE]
> Helidon SE support for OpenTelemetry configuration and semantic conventions as described below is currently a [preview feature][preview-feature]. We intend to support it going forward, but we might change its external API and behavior in backward-incompatible ways across dot releases.

Helidon SE supports OpenTelemetry in several important ways:

- Implements the [neutral Helidon tracing API](../../se/tracing.md) using OpenTelemetry
- Allows users to assign OpenTelemetry settings as follows:
  - Declaratively, using Helidon config under the top-level `telemetry` config key
  - Programmatically, using the OpenTelemetry SDK API and the Helidon OpenTelemetry API
- Conforms to the [OpenTelemetry semantic conventions][opentelemetry-se] for automatically-created spans and metrics for HTTP requests
- Allows [publishing Helidon metrics][publishing-helid] to backend systems using OTLP.

OpenTelemetry models observability as a set of [*signals*][signals]. Each signal—​for example metrics, tracing, and logging—​is an origin of monitoring data, and each has configurable settings which control its behavior.

Helidon’s config support for OpenTelemetry has certain config attributes which apply to OpenTelemetry as a whole, others which pertain to individual signals, and still more which describe lower-level elements within a signal.

The Helidon OpenTelemetry configuration format, the Helidon OpenTelemetry API, and this documentation all follow this hierarchy:

- [Top-level telemetry](#top-level-config)
  - Signals
    - [Tracing](#tracing-config)
    - [Metrics](#metrics-config)
    - [Logging](#logger-config)

This document describes how to configure each level in the hierarchy and covers general topics related to Helidon’s support of OpenTelemetry.

## API

There are *two* APIs that might be useful to developers working with OpenTelemetry:

- The Helidon OpenTelemetry API - useful for mapping configuration sources to Helidon builders and, ultimately, OpenTelemetry objects.
- The OpenTelemetry API - useful for creating OpenTelemetry objects apart from Helidon configuration sources.

The types in the Helidon OpenTelemetry API correspond closely to the configuration structures described in later sections of this document. Application code can use Helidon OpenTelemetry builders to prepare and construct each of the configurable entities to ultimately prepare an `OpenTelemetry` instance set up according to the application’s needs.

That said, application code can equally well use the OpenTelemetry API and its builders to prepare the `OpenTelemetry` instance.

Applications could even use both APIs together, reading configuration to construct a Helidon builder and then adding to that builder OpenTelemetry objects created separately using the OpenTelemetry API.

The [Helidon OpenTelemetry API Javadoc][helidon-opentele] page lists the various types developers can use to prepare OpenTelemetry objects programmatically. As a starting point, the [`OpenTelemetryConfig`][opentelemetrycon] interface and its [`Builder`][builder] represents the top-level configuration for OpenTelemetry. Their Javadoc contains links to other types that compose the top-level object, and so on.

Later sections in this document also describe the configuration settings available.

The [OpenTelemetry SDK documentation][opentelemetry-sd] explains its API.

> [!NOTE]
> Many applications do not need to use either the Helidon OpenTelemetry API or the OpenTelemetry API directly. They can instead rely completely on declarative Helidon configuration of OpenTelemetry.

### Managing the Global `OpenTelemetry` Instance

Typically, an application uses the same `OpenTelemetry` instance throughout its execution. OpenTelemetry offers a global `OpenTelemetry` instance to make it easy for application code to set and obtain the global instance.

Similarly, the Helidon tracing API has a global `Tracer`.

In most cases, an application that prepares OpenTelemetry programmatically should initialize both of those by including code as shown in the following example.

Setting the global OpenTelemetry and Tracer instances in Helidon:

```java
import java.util.Map;
import io.helidon.telemetry.otelconfig.HelidonOpenTelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

        // Application code using the OpenTelemetry API or the Helidon OpenTelemetry API or both.
        OpenTelemetry customOpenTelemetry = prepareOpenTelemetry();

        // App code to build any tags to be applied to every span.
        Map<String, String> tags = prepareTags();

        HelidonOpenTelemetry.global(customOpenTelemetry,
                                    "your-service-name",
                                    tags);
```

<a id="effects-of-setting-global"></a>
#### Assigning the Global Instance

Using Helidon to set the global `OpenTelemetry` instance has these effects:

- Assigns the instance as the OpenTelemetry global instance.
- Creates a Helidon `Tracer` using the OpenTelemetry instance and makes that the Helidon global `Tracer`.

> [!NOTE]
> Helidon is deprecating its use of "global" Helidon objects in favor of retrieving the correct instance from the Helidon service registry. Applications should migrate toward using, for example, `Services.get(Tracer.class)` instead of `Tracer.global()`.

## Maven Coordinates

To enable various aspects of OpenTelemetry Support add one or more of the following dependencies to your project’s `pom.xml` (see [Managing Dependencies](../../managing-dependencies.md)).

### Using the OpenTelemetry implementation of the Helidon Tracing API

Helidon offers an implementation of its [ neutral tracing API](../../se/tracing.md) that uses OpenTelemetry. Add the following dependency to use OpenTelemetry for tracing.

Dependency to use the Helidon OpenTelemetry implementation of Helidon tracing:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-opentelemetry</artifactId>
  <scope>runtime</scope>
</dependency>
```

### Adding OpenTelemetry Configuration and Builder Support

To allow deployers and end users to set up Helidon configuration to control OpenTelemetry behavior, add the following dependency.

Dependency to add Helidon OpenTelemetry config and programmatic builder support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.telemetry</groupId>
  <artifactId>helidon-telemetry-opentelemetry-config</artifactId>
  <scope>runtime</scope>
</dependency>
```

- To use the Helidon OpenTelemetry API in your application code, remove this line or change it to `<scope>compile</scope>`.

### Enabling Automatic Spans for HTTP Requests

Helidon’s tracing observability support automatically creates a new tracing span for each HTTP request if your project includes the following dependency.

Dependency for automatic HTTP request tracing:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-tracing</artifactId>
  <scope>runtime</scope>
</dependency>
```

By default, when Helidon SE creates spans automatically for HTTP requests, it uses a set of rules—​semantic conventions—​for choosing the span name and adding tags to each span.

OpenTelemetry prescribes its own [semantic conventions][opentelemetry-se]. If you add the following dependency, Helidon follows the OpenTelemetry semantic conventions for spans instead of its own.

Dependency for Helidon support of the OpenTelemetry tracing semantic conventions:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-telemetry-tracing</artifactId>
  <scope>runtime</scope>
</dependency>
```

### Enabling Automatic Metrics for Incoming HTTP Requests

Helidon’s metrics observability support automatically registers and updates one or more meters (depending on configuration) and updates them accordingly as HTTP requests arrive.

Dependency for automatic HTTP request measurements:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-metrics</artifactId>
  <scope>runtime</scope>
</dependency>
```

OpenTelemetry prescribes its own [semantic conventions][semantic-convent] for metrics—​their names and tha attributes (tags) they have. If you add the following dependency, Helidon registers and updates meters according to the OpenTelemetry metrics semantic conventions.

Dependency for Helidon support of the OpenTelemetry metrics semantic conventions for incoming HTTP requests:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-telemetry-metrics</artifactId>
  <scope>runtime</scope>
</dependency>
```

### Enabling OpenTelemetry for Outgoing Helidon WebClient Traffic

Helidon supports the OpenTelemetry semantic conventions for outgoing traffic which uses the Helidon WebClient. See the [Helidon WebClient documentation][helidon-webclien].

<a id="note-about-exporter-dependencies"></a>
### Specifying Additional OpenTelemetry Dependencies

Most applications need to declare other runtime dependencies on OpenTelemetry artifacts because the configuration specifies—​or the application code uses—​particular OpenTelemetry types packaged in other artifacts. For example, OpenTelemetry exporters are packaged individually or as related groups. See [this section below][this-section-bel] for some specific dependencies to consider adding for particular exporters.

These exporters transmit telemetry data using a different protocol. (See [this OpenTelemetry page][this-opentelemet].)

## Configuration

You can control almost all of OpenTelemetry’s overall, tracing, metrics, and logger runtime behavior using Helidon configuration settings. Helidon constructs an `OpenTelemetry` object using the configuration. The resulting `OpenTelemetry` instance reflects these settings from the Helidon configuration:

- Settings that pertain to [overall OpenTelemetry behavior](#top-level-config), apart from a particular signal.
- An OpenTelemetry tracer provider based on [tracing configuration](#tracing-config) in `signals.tracing`.
- An OpenTelemetry meter provider based on [metrics configuration](#metrics-config) in `signals.metrics`.
- An OpenTelemetry logger provider based on [logger configuration](#logger-config) in `signals.logging`.

<a id="top-level-config"></a>
### Controlling Overall OpenTelemetry Behavior

Several settings control the operation of OpenTelemetry as a whole, as shown in the next table.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.HelidonOpenTelemetry.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.HelidonOpenTelemetry.md#configuration-options).
<!--/include-->


Notes:

- OpenTelemetry uses default propagators of `tracecontext` and `baggage`. (See the `otel.propagators` property in [this OpenTelemetry guide][this-opentelemet-2].)
- Setting `global` to `true` has the effect described in the [section](#effects-of-setting-global) about global instances.

<a id="common-config"></a>
### Common Configuration Across Signals

This section describes settings that apply to multiple signal types.

<a id="attributes-config"></a>
#### Assigning Attributes

Configured attributes are key/value pairs that OpenTelemetry attaches to each transmission of a signal. OpenTelemetry supports attributes of type `String`, `long`, `double`, and `boolean`. The Helidon configuration structure groups attributes by type so Helidon can indicate precisely to OpenTelemetry what type you intend for each attribute.

You can add attributes to the configuration for any of the signals under the signal’s `attributes` section.

##### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.TypedAttributes.md#configuration-options offset=3 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.TypedAttributes.md#configuration-options).
<!--/include-->


The following example shows attribute settings for the tracing signal.

Example attribute settings:

```yaml
telemetry:
  service: my-helidon-service
  tracing:
    attributes:
      strings:
        attr1: 12
        attr5: "any old thing"
        attr7: something
      longs:
        attr2: 12
      doubles:
        attr3: 24.5
        attr6: 12
      booleans:
        attr4: true
```

<a id="exporters-and-processors"></a>
#### Configuring Exporters and Processors/Readers

OpenTelemetry transmits the telemetry data it gathers to a backend system—​such as Grafana, Signoz, Prometheus, Jaeger, or others—​where you can view and query the data. OpenTelemetry goes through these distinct steps to gather and send data:

1.  OpenTelemetry tracing and log record *processors* and metrics *readers* gather and process data observations.
2.  OpenTelemetry *exporters* associated with each processor or reader then transmit\_ the data to one or more targets. Targets are typically backend systems but can be local ones for debugging. Each processor or reader uses one or more exporters to transmit telemetry data.

The processor settings determine when and how often each uses its exporters to deliver data. Each exporter’s settings prescribe where it should send the data, how to connect to a backend, etc.

##### Configuring Processors (and readers)

An OpenTelemetry span or log record processor or metric reader is one of the following types:

- simple - The processor sends each telemetry observation to its exporters for transmission as soon as it receives the observation.
- batch - The processor groups observations into batches and sends a batch at a time to its exporters for transmission.

In the table below only the `type` and `exporters` setting apply to `simple` processors; the other settings are for batch processors.

##### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.BatchProcessorConfig.md#configuration-options offset=3 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.BatchProcessorConfig.md#configuration-options).
<!--/include-->


##### Configuring Exporters

Exporter objects in OpenTelemetry are specific to both *how* they transmit telemetry data and *what* signal they work with. Even so, many exporter settings are very similar across different signals. This section describes the behavior and configuration that is common among exporters. Refer to the sections below that describe each signal to see what additional exporter settings, if any, each signal adds.

Helidon configuration supports several of the most popular exporters, discussed below.

###### Setting Up OTLP Exporters

Most users choose an `Otlp` exporter which has two variations—​one using gRPC and one using HTTP with protocol buffers—​as indicated by the `protocol` setting.

##### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OtlpExporterConfig.md#configuration-options offset=3 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.OtlpExporterConfig.md#configuration-options).
<!--/include-->


Common Configuration for OTLP exporters

<table>
<caption>OpenTelemetry OTLP exporter defaults</caption>
<colgroup>
<col style="width: 20%" />
<col style="width: 80%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Setting</th>
<th style="text-align: left;">OpenTelemetry default</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>compression</code></p></td>
<td style="text-align: left;"><p><code>none</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>endpoint</code></p></td>
<td style="text-align: left;"><p><code>grpc</code> protocol: <a href="http://localhost:4317">http://localhost:4317</a></p>
<p><code>http/proto</code> protocol: <a href="http://localhost:4318">http://localhost:4318</a></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>protocol</code></p></td>
<td style="text-align: left;"><p><code>grpc</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>retry-policy</code></p></td>
<td style="text-align: left;"><p>none</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>timeout</code></p></td>
<td style="text-align: left;"><p>10 seconds</p></td>
</tr>
</tbody>
</table>

###### OTLP Retry Policy

You can control how each exporter retries if a transmission to a backend fails.

###### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.RetryPolicyConfig.md#configuration-options offset=4 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.RetryPolicyConfig.md#configuration-options).
<!--/include-->


OpenTelemetry also supports a Zipkin exporter which it has recently deprecated.

###### Zipkin Exporter

##### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.ZipkinExporterConfig.md#configuration-options offset=3 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.ZipkinExporterConfig.md#configuration-options).
<!--/include-->


Configuration for Zipkin exporters

The [OpenTelemetry documentation][opentelemetry-do] describes the defaults; see the "Properties for Zipkin span exporters" section there.

| Setting       | OpenTelemetry default value          |
|---------------|--------------------------------------|
| `compression` | `none`                               |
| `encoder`     | `JSON_V2`                            |
| `endpoint`    | <http://localhost:9411/api/v2/spans> |
| `timeout`     | 10 seconds                           |

OpenTelemetry defaults for Zipkin exporters

OpenTelemetry provides other exporters, often used for debugging:

- `console`

  Writes telemetry data at the `INFO` level using the `java.util.logging.Logger` for `io.opentelemetry.exporter.logging.Logging{signal}Expoerter`

- `logging-otlp`

  Writes telemetry data in JSON format to the logger for the particular OpenTelemetry implementation class (e.g., `OtlpJsonLoggingMetricExporter`).

The `console` and `logging-otlp` have no configuration that is common across all signals.

> [!NOTE]
> You need to add dependencies to your project for the exporters your application uses, even ones supported by Helidon config.

The table below describes the exporter types that Helidon configuration supports and what dependency your project needs to support them.

If you need to use an exporter that is *not* in the table:

- Add a dependency on the OpenTelemetry artifact that contains that exporter type.
- Add application code that prepares the exporter instance.
- Prepare the Helidon OpenTelemetry builders programmatically and add your exporter instance to the builder.

In the table below, the Maven artifacts are all in the `io.opentelemetry` group.

<table>
<colgroup>
<col style="width: 12%" />
<col style="width: 25%" />
<col style="width: 62%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Exporter type</th>
<th style="text-align: left;">OpenTelemetry Java Type</th>
<th style="text-align: left;">Artifact ID to add - <code>see</code>also the <a href="https://opentelemetry.io/docs/languages/java/sdk/#spanexporter">OpenTelemetry documentation</a></th>
</tr>
</thead>
<tbody>
<tr>
<td rowspan="2" style="text-align: left;"><p><a href="#otlp-exporter-config"><code>otlp</code></a><br />
(see <code>protocol</code> setting below)</p></td>
<td style="text-align: left;"><p><code>OtlpGrpc{signal}Exporter</code></p></td>
<td rowspan="2" style="text-align: left;"><p><code>opentelemetry-exporter-otlp</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>OtlpHttp{signal}Exporter</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><a href="../../config/io.helidon.telemetry.otelconfig.ZipkinExporterConfig.md"><code>zipkin</code></a></p></td>
<td style="text-align: left;"><p><code>ZipkinSpanExporter</code></p></td>
<td style="text-align: left;"><p><code>opentelemetry-exporter-zipkin</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>console</code></p></td>
<td style="text-align: left;"><p><code>Logging{signal}Exporter</code></p></td>
<td rowspan="2" style="text-align: left;"><p><code>opentelemetry-exporter-logging</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>logging_otlp</code></p></td>
<td style="text-align: left;"><p><code>OtlpJsonLogging{signal}Exporter</code></p>
<p><code>SystemOutLogRecordExporeter</code></p></td>
</tr>
</tbody>
</table>

##### Associating Each Processor and Reader with its Exporters

In configuration, you link processors and readers with the exporters you want each to use as follows:

- For clarity, name each exporter if you have more than one.
- Optionally specify for each processor or reader the names of the exporters it should use.

  If you omit the exporter names for a processor or reader, Helidon associates it with all configured exporters. If you configure no exporters explicitly, Helidon associates the OpenTelemetry default exporter with the processor or reader.

The following examples show increasingly-complicated scenarios using tracing as the signal:

- Default
- Minimal configuration
- Maximum flexibility

For many applications the default and minimal scenarios work well.

##### Default

This scenario includes no configuration at all for either processors or exporters.

OpenTelemetry uses its default processor (`batch`) with its default exporter (`otlp` using `grpc`). \|

```yaml
telemetry:
  service: "inventory"
  tracing:
    sampler: "always_off"
```

##### Minimal configuration

The user configures at most one processor and at most one exporter.

The single processor uses the single exporter.

No exporter name is declared or referenced.

```yaml
telemetry:
  service: "inventory"
  tracing:
    sampler: "always_off"
    exporters:
      - type: zipkin
        compression: gzip
    processors:
      - type: batch
        max-queue-size: 50
```

##### Maximum flexibility

The user configures possibly multiple processors and possibly multiple named exporters. Each processor’s configuration lists the names of the exporters it should use; no names means all exporters.

The first processor (type `batch`) uses both exporters because it does not specify any exporter names. The second processor uses only the `alternate-otlp` exporter.\|

```yaml
telemetry:
  service: "inventory"
  tracing:
    sampler: "always_off"
    exporters:
      - type: zipkin
        compression: gzip
        name: "compressed-zipkin"
      - endpoint: "http://collect.com:4317"
        name: "alternate-otlp""
    processors:
      - type: batch
        max-queue-size: 50
      - type: simple
        exporters: ["alternate-otlp"]
```

<a id="tracing-config"></a>
### Controlling OpenTelemetry Tracing Behavior

The settings under `signals.tracing` prepare an OpenTelemetry `TracerProvider`. When your application uses the Helidon tracing API to obtain a `Tracer`, Helidon uses the `TracerProvider` prepared from this config to create the tracer.

The next table describes the OpenTelemetry tracing settings.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig.md#configuration-options).
<!--/include-->


OpenTelemetry applies the defaults described in the next table.

| Setting | OpenTelemetry default (and OpenTelemetry doc link) |
|----|----|
| `exporters` | [`otlp` with `grpc` protocol][opentelemetry-do] - see "Properties: exporters, `otel.traces.exporter` property" |
| `processors` | [`batch` with defaults][batch-with-defau] - see "Properties for batch span processor(s)" |
| `sampler` | [`parentbased_always_on`][batch-with-defau] - see "Properties for sampler" |
| `span-limits` | See [tracing][batch-with-defau] "Properties for span limits" |

Default tracing settings applied by OpenTelemetry

Refer to the earlier sections about [configuring attributes](#attributes-config) and [configuring processors and exporters](#exporters-and-processors).

Sections below describe how to set up the tracing signal configuration:

- [Configuring the Span Sampler](#span-sampler-config)
- [Configuring the Span Limits](#span-limits-config)

<a id="span-sampler-config"></a>
#### Configuring the Span Sampler

OpenTelemetry offers different ways of sampling data—​deciding which tracing spans tp capture and send to the backend. The [OpenTelemetry documentation][opentelemetry-do-2] describes sampling in more detail.

Helidon configuration supports the sampler implementations that reside in the `opentelemetry-sdk` as listed in the table below. Other samplers are in other components. If you need to use one of those:

- Add the relevant OpenTelemetry dependency to your project.
- Instantiate the span sample you need.
- Prepare the sampler and the OpenTelemetry-related builders programmatically and use your sampler to assign the sampler the `OpenTelemetryTracer.Builder` should use.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.SamplerConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.SamplerConfig.md#configuration-options).
<!--/include-->


<a id="span-limits-config"></a>
#### Configuring Span Limits

OpenTelemetry allows you to constrain certain aspects of the data it gathers in tracing spans. By assigning the settings in the table below, you can apply the span limits you want.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.SpanLimitsConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.SpanLimitsConfig.md#configuration-options).
<!--/include-->


The [OpenTelemetry documentation][opentelemetry-do-2] describes the defaults; see the "Properties for span limits" section there.

| Setting                      | OpenTelemetry Default |
|------------------------------|-----------------------|
| `max-attribute-value-length` | no limit              |
| `max-attributes`             | 128                   |
| `max-attributes-per-event`   | 128                   |
| `max-events`                 | 128                   |
| `max-links`                  | 128                   |

OpenTelemetry defaults for span limits

<a id="metrics-config"></a>
### Controlling OpenTelemetry Metrics Behavior

The settings under `signals.metrics` prepare an OpenTelemetry `MeterProvider`. If your code uses the OpenTelemetry API to obtain an OpenTelemetry meter, meter provider, or meter builder, OpenTelemetry uses the `MeterProvider` prepared from this configuration.

The sections below describe Helidon config settings that correspond very directly to OpenTelemetry builders for the relevant OpenTelemetry type. Refer to the relevant OpenTelemetry documentation or Javadoc to understand the effect each setting has.

See the earlier sections about [configuring attributes](#attributes-config) and [configuring processors and exporters](#exporters-and-processors). A [later section below](#metric-exporters-config) describes some additional attributes on metrics exporters.

The next table describes the OpenTelemetry metrics settings.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig.md#configuration-options).
<!--/include-->


OpenTelemetry applies the defaults described in the next table.

| Setting | OpenTelemetry default (and OpenTelemetry doc link) |
|----|----|
| `exporters` | [`otlp` with `grpc` protocol][opentelemetry-do] - see that web page’s "Properties: exporters, `otel.metrics.exporter` property"\] section. |
| `readers` | [`PeriodicMetricReader`][periodicmetricre] with an interval of one minute |

Default metric settings applied by OpenTelemetry

Sections below describe how to set up the configuration that is specific to the metrics signal:

- [Configuring Metric Exporters](#metric-exporters-config)
- [Configuring Metric Readers](#metric-readers-config)
- [Configuring Metric Views](#metric-views-config)

The following example illustrates some of the ways you can configure OpenTelemetry metrics behavior. It is neither complete nor typical.

Example OpenTelemetry Metrics Configuration:

<!--@mdc ::code-collapse -->
```yaml
telemetry:
  service: "test-telemetry"
  signals:
    metrics:                                          
      exporters:
        - name: exp-1                                 
          type: otlp
          endpoint: "http://host:1234"
          temporality-preference: cumulative          
          default-histogram-aggregation:              
            type: base2-exponential-bucket-histogram
            max-buckets: 152
            max-scale: 19
        - name: exp-2                                 
          type: otlp
          protocol: grpc
          temporality-preference: delta               
          default-histogram-aggregation:              
            type: explicit-bucket-histogram
            bucket-boundaries: [3,5,7]
      readers:
        - type: periodic                              
          exporter: exp-1
          interval: PT6S
      views:
        - name: sum-view                              
          aggregation:
            type: sum
          description: "Sum view"
          instrument-selector:
            name: counter-selector
            type: counter
            meter-name: my-counter
```
<!--@mdc :: -->

- Introduces the metrics configuration.
- Introduces the first metric exporter (with name `exp-1`).
- Indicates to accumulate measurement values since the previous transmission.
- Prescribes to aggregate histograms for transmission using the OpenTelemetry `BASE2_EXPONENTIAL_BUCKET_HISTOGRAM` technique with the specified maximum number of buckets and maximum scale.
- Introduces the second metric exporter (with name `exp-2`).
- Indicates to transmit deltas since the last transmission.
- Prescribes to aggregate histograms using a histogram with the given explicit bucket boundary values.
- Declares a single metric reader of the OpenTelemetry `PERIODIC` types gathering data each 6 seconds.
- Declares a single view to influence influence the transmission of the `my-counter` counter data.

<a id="metric-exporters-config"></a>
#### Metric Exporters

The configuration for metrics exporters has several additional settings beyond those described earlier for exporters in general.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.MetricExporterConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.MetricExporterConfig.md#configuration-options).
<!--/include-->


##### Metric Aggregation

OpenTelemetry allows control over how each exporter aggregates histogram data prior to transmission to a backend.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.MetricDefaultHistogramAggregationConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.MetricDefaultHistogramAggregationConfig.md#configuration-options).
<!--/include-->


You can configure the explicit bucket boundaries for `EXPLICIT_BUCKET_HISTOGRAM` aggregation.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.ExplicitBucketHistogramAggregationConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.ExplicitBucketHistogramAggregationConfig.md#configuration-options).
<!--/include-->


You can configure the exponential histogram aggregation behavior.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.Base2ExponentialHistogramAggregationConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.Base2ExponentialHistogramAggregationConfig.md#configuration-options).
<!--/include-->


<a id="metric-readers-config"></a>
#### Metric Readers

An OpenTelemetry metric reader collects metric data in the server and then uses the associated metric exporter to send that data to the endpoint configured.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.MetricReaderConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.MetricReaderConfig.md#configuration-options).
<!--/include-->


The periodic reader supports the following settings.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.PeriodicMetricReaderConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.PeriodicMetricReaderConfig.md#configuration-options).
<!--/include-->


<a id="metric-views-config"></a>
#### Metric Views

OpenTelemetry metric views allow you to influence how meters are aggregated for reporting to backend systems.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.ViewRegistrationConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.ViewRegistrationConfig.md#configuration-options).
<!--/include-->


The instrument selector controls which meters this view reflects.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.InstrumentSelectorConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.InstrumentSelectorConfig.md#configuration-options).
<!--/include-->


<a id="logger-config"></a>
### Controlling OpenTelemetry Logger Behavior

The settings under `signal.logging` prepare an OpenTelemetry \`LoggerProvider.

The sections below describe Helidon config settings that correspond directly to OpenTelemetry builders for the relevant OpenTelemetry type. Refer to the relevant OpenTelemetry documentation or Javadoc to understand the effect each setting has.

The next table describes the OpenTelemetry logging settings.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig.md#configuration-options offset=2 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig.md#configuration-options).
<!--/include-->


OpenTelemetry uses the following defaults:

| Setting            | OpenTelemetry default     |
|--------------------|---------------------------|
| `exporters`        | none                      |
| `minimum-severity` | undefined severity number |
| `processors`       | no-op processor           |
| `trace-based`      | `false`                   |

Default logging settings applied by OpenTelemetry

Refer to the earlier sections about [configuring attributes](#attributes-config) and [configuring processors and exporters](#exporters-and-processors).

Sections below explain how to set up the configuration that is specific to the logging signal:

- [Configuring Log Limits](#configuring-log-limits)

#### Configuring Log Limits

For defaults, Helidon defers to the OpenTelemetry defaults, listed below.

##### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.LogLimitsConfig.md#configuration-options offset=3 -->
See [Configuration options](../../config/io.helidon.telemetry.otelconfig.LogLimitsConfig.md#configuration-options).
<!--/include-->


OpenTelemetry applies the following defaults:

| Setting | OpenTelemetry default |
|----|----|
| `max-attribute-value-length` | `Integer.MAX_VALUE` |
| `max-number-of-attributes` | 128 |
| `exporters` | [`otlp` with `grpc` protocol][opentelemetry-do] - see that web page’s "Properties: exporters, `otel.logger.exporter` property" section. |
| `log-limits` | \`max- |
| `processors` | [`otlp`][otlp] - see that web page’s "Properties: logs" section. |

Default log limit settings applied by OpenTelemetry

The following example illustrates some of the ways you can configure OpenTelemetry logger behavior. It is neither complete nor typical.

Example OpenTelemetry Logger Configuration:

```yaml
telemetry:
  service: test-tel-logging
  global: false
  signals:
    logging:                              
      minimum-severity: TRACE             
      log-limits:                         
        max-attribute-value-length: 20
        max-number-of-attributes: 14
      processors:                         
        - type: batch
          schedule-delay: PT10S
          max-queue-size: 15
          max-export-batch-size: 5
          timeout: PT30S
        - type: simple
      exporters:                          
        - name: exp-1
          endpoint: "http://host:1234"
```

- Introduces the logger configuration.
- Sets the minimum log level severity to of log messages to send to the backend system.
- Configures limits related to attributes that accompany log messages.
- Prescribes the logger processors.
- Prescribes the logger exporters.

#### Logger Exporters and Processors

You associate each logger processor with a logger exporter using the exporter’s name. See the [earlier section](#exporters-and-processors) for more information.

## Additional Information

### Helidon Documentation

- [Helidon Tracing](../../se/tracing.md)

### OpenTelemetry Documentation

- [Settings and defaults][opentelemetry-do]
- [OpenTelemetry Java SDK reference][opentelemetry-ja]
- [HTTP semantic conventions][opentelemetry-se]
- [Intro to OpenTelemetry Java][intro-to-opentel]

[preview-feature]: https://helidon.io/docs/v4/apidocs/io.helidon.common.features.api/io/helidon/common/features/api/Preview.html
[opentelemetry-se]: https://github.com/open-telemetry/semantic-conventions/blob/v1.58.0/docs/http/http-spans.md#http-server
[publishing-helid]: ../../se/metrics/metrics.md#publishing-metrics-for-external-access
[signals]: https://opentelemetry.io/docs/concepts/signals/
[helidon-opentele]: https://helidon.io/docs/v4/apidocs/io.helidon.telemetry.otelconfig/io/helidon/telemetry/otelconfig/package-summary.html
[opentelemetrycon]: https://helidon.io/docs/v4/apidocs/io.helidon.telemetry.otelconfig/io/helidon/telemetry/otelconfig/OpenTelemetryConfig.html
[builder]: https://helidon.io/docs/v4/apidocs/io.helidon.telemetry.otelconfig/io/helidon/telemetry/otelconfig/OpenTelemetryConfig.BuilderBase.html
[opentelemetry-sd]: https://opentelemetry.io/docs/languages/java/sdk/#sdk-components
[semantic-convent]: https://github.com/open-telemetry/semantic-conventions/blob/v1.58.0/docs/http/http-metrics.md#http-server
[helidon-webclien]: ../../se/webclient.md#configuring-telemetry
[this-section-bel]: #note-about-exporter-dependencies
[this-opentelemet]: https://github.com/open-telemetry/opentelemetry-java/tree/v1.58.0/exporters
[this-opentelemet-2]: https://opentelemetry.io/docs/languages/java/configuration/#properties-general
[opentelemetry-do]: https://opentelemetry.io/docs/languages/java/configuration/#properties-exporters
[batch-with-defau]: https://opentelemetry.io/docs/languages/java/configuration/#properties-traces
[opentelemetry-do-2]: https://opentelemetry.io/docs/languages/java/sdk/#sampler
[periodicmetricre]: https://opentelemetry.io/docs/languages/java/configuration/#properties-metrics
[otlp]: https://opentelemetry.io/docs/languages/java/configuration/#properties-logs
[opentelemetry-ja]: https://opentelemetry.io/docs/languages/java/sdk
[intro-to-opentel]: https://opentelemetry.io/docs/languages/java/intro/
