# Helidon OpenTelemetry Support

> [!NOTE]
> Helidon SE support for OpenTelemetry configuration and semantic conventions as
> described below is currently a [preview feature][preview-feature]. We intend
> to support it going forward, but we might change its external API and behavior
> in backward-incompatible ways across dot releases.

## Overview

Helidon SE supports OpenTelemetry in several important ways:

- Implements the [neutral Helidon tracing API](../../se/tracing.md) using
  OpenTelemetry
- Allows users to assign OpenTelemetry settings as follows:
  - Declaratively, using Helidon config under the top-level `telemetry` config
    key
  - Programmatically, using the OpenTelemetry SDK API and the Helidon
    OpenTelemetry API
- Conforms to the [OpenTelemetry semantic conventions][opentelemetry-se] for
  automatically-created spans and metrics for HTTP requests
- Allows [publishing Helidon metrics][publishing-helid] to backend systems using
  OTLP.

OpenTelemetry models observability as a set of [*signals*][signals].

Each signal—for example metrics, tracing, and logging—is an origin of monitoring
data, and each has configurable settings which control its behavior.

Helidon’s config support for OpenTelemetry has certain config attributes which
apply to OpenTelemetry as a whole, others which pertain to individual signals,
and still more which describe lower-level elements within a signal.

The Helidon OpenTelemetry configuration format, the Helidon OpenTelemetry API,
and this documentation all follow this hierarchy:

- [Top-level telemetry](#configuration-options)
  - Signals
    - [Tracing](#tracing-configuration)
    - [Metrics](#metrics-configuration)
    - [Logging](#logging-configuration)

This document describes how to configure each level in the hierarchy and covers
general topics related to Helidon’s support of OpenTelemetry.

## API

There are *two* APIs that might be useful to developers working with
OpenTelemetry:

- The Helidon OpenTelemetry API - useful for mapping configuration sources to
  Helidon builders and, ultimately, OpenTelemetry objects.
- The OpenTelemetry API - useful for creating OpenTelemetry objects apart from
  Helidon configuration sources.

The types in the Helidon OpenTelemetry API correspond closely to the
configuration structures described in later sections of this document.
Application code can use Helidon OpenTelemetry builders to prepare and construct
each of the configurable entities to ultimately prepare an `OpenTelemetry`
instance set up according to the application’s needs.

That said, application code can equally well use the OpenTelemetry API and its
builders to prepare the `OpenTelemetry` instance.

Applications could even use both APIs together, reading configuration to
construct a Helidon builder and then adding to that builder OpenTelemetry
objects created separately using the OpenTelemetry API.

The [Helidon OpenTelemetry API Javadoc][helidon-opentele] page lists the various
types developers can use to prepare OpenTelemetry objects programmatically. As a
starting point, the [`OpenTelemetryConfig`][opentelemetrycon] interface and its
[`Builder`][builder] represents the top-level configuration for OpenTelemetry.
Their Javadoc contains links to other types that compose the top-level object,
and so on.

Later sections in this document also describe the configuration settings
available.

The [OpenTelemetry SDK documentation][opentelemetry-sd] explains its API.

> [!NOTE]
> Many applications do not need to use either the Helidon OpenTelemetry API or
> the OpenTelemetry API directly. They can instead rely completely on
> declarative Helidon configuration of OpenTelemetry.

### Global Instance

Typically, an application uses the same `OpenTelemetry` instance throughout its
execution. OpenTelemetry offers a global `OpenTelemetry` instance to make it
easy for application code to set and obtain the global instance.

Similarly, the Helidon tracing API has a global `Tracer`.

In most cases, an application that prepares OpenTelemetry programmatically
should initialize both of those by including code as shown in the following
example.

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

HelidonOpenTelemetry.global(customOpenTelemetry, "your-service-name", tags);
```

#### Assigning the Global Instance

Using Helidon to set the global `OpenTelemetry` instance has these effects:

- Assigns the instance as the OpenTelemetry global instance.
- Creates a Helidon `Tracer` using the OpenTelemetry instance and makes that the
  Helidon global `Tracer`.

> [!NOTE]
> Helidon is deprecating its use of "global" Helidon objects in favor of
> retrieving the correct instance from the Helidon service registry.
> Applications should migrate toward using, for example,
> `Services.get(Tracer.class)` instead of `Tracer.global()`.

## Maven Coordinates

To enable various aspects of OpenTelemetry Support add one or more of the
following dependencies to your project’s `pom.xml` (see [Managing
Dependencies](../../managing-dependencies.md)).

Helidon Tracing provider:
```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-opentelemetry</artifactId>
</dependency>
```

Helidon Config and programmatic builder support:
```xml [pom.xml]
<dependency>
  <groupId>io.helidon.telemetry</groupId>
  <artifactId>helidon-telemetry-opentelemetry-config</artifactId>
</dependency>
```

Helidon WebServer OpenTelemetry Tracing semantics:
```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-telemetry-tracing</artifactId>
  <scope>runtime</scope>
</dependency>
```

Helidon WebServer OpenTelemetry Metrics semantics:
```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-telemetry-metrics</artifactId>
  <scope>runtime</scope>
</dependency>
```

## WebClient Support

Helidon supports the OpenTelemetry semantic conventions for outgoing traffic
which uses the Helidon WebClient. See the [Helidon WebClient
documentation][helidon-webclien].

## Additional Dependencies

Most applications need to declare other runtime dependencies on OpenTelemetry
artifacts because the configuration specifies—or the application code
uses—particular OpenTelemetry types packaged in other artifacts.

For example, OpenTelemetry exporters are packaged individually or as related
groups. See [this section below][this-section-bel] for some specific
dependencies to consider adding for particular exporters.

These exporters transmit telemetry data using a different protocol. (See [this
OpenTelemetry page][this-opentelemet].)

## Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.HelidonOpenTelemetry.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem].
<!--/include-->

Notes:

- OpenTelemetry uses default propagators of `tracecontext` and `baggage`. (See
  the `otel.propagators` property in [this OpenTelemetry
  guide][this-opentelemet-2].)
- Setting `global` to `true` has the effect described in the
  [section](#assigning-the-global-instance) about global instances.

## Signal Attributes

Attributes are key/value pairs that OpenTelemetry attaches to each transmission
of a signal.

OpenTelemetry supports attributes of type `String`, `long`, `double`, and
`boolean`.

The Helidon configuration structure groups attributes by type so Helidon can
indicate precisely to OpenTelemetry what type you intend for each attribute.

### Configuration options

You can add attributes to the configuration for any of the signals under the
signal’s `attributes` section.

<!--@include ../../config/io.helidon.telemetry.otelconfig.TypedAttributes.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-telem-2].
<!--/include-->

The following example shows attribute settings for the tracing signal.

### Configuration Example

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

## Processors & Readers

Two types of processors and readers are provided:

- `simple`: sends observations upon receive
- `batch`: groups observations into batches

In the table below only the `type` and `exporters` setting apply to `simple`
processors; the other settings are for batch processors.

### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.BatchProcessorConfig.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-telem-3].
<!--/include-->

## Exporters

Exporter objects in OpenTelemetry are specific to both *how* they transmit
telemetry data and *what* signal they work with. Even so, many exporter settings
are very similar across different signals. This section describes the behavior
and configuration that is common among exporters. Refer to the sections below
that describe each signal to see what additional exporter settings, if any, each
signal adds.

Helidon configuration supports several of the most popular exporters, discussed
below.

### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OtlpExporterConfig.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-telem-4].
<!--/include-->


Common Configuration for OTLP exporters

<table>
<thead>
<tr>
<th>Setting</th>
<th>OpenTelemetry default</th>
</tr>
</thead>
<tbody>
<tr>
<td><p><code>compression</code></p></td>
<td><p><code>none</code></p></td>
</tr>
<tr>
<td><p><code>endpoint</code></p></td>
<td><p><code>grpc</code> protocol: <a href="http://localhost:4317">http://localhost:4317</a></p>
<p><code>http/proto</code> protocol: <a href="http://localhost:4318">http://localhost:4318</a></p></td>
</tr>
<tr>
<td><p><code>protocol</code></p></td>
<td><p><code>grpc</code></p></td>
</tr>
<tr>
<td><p><code>retry-policy</code></p></td>
<td><p>none</p></td>
</tr>
<tr>
<td><p><code>timeout</code></p></td>
<td><p>10 seconds</p></td>
</tr>
</tbody>
</table>

### OTLP Retry Policy

You can control how each exporter retries if a transmission to a backend fails.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.RetryPolicyConfig.md#configuration-options delim=--- offset=4 collapseTables=10 -->
See [Configuration options][io-helidon-telem-5].
<!--/include-->


OpenTelemetry also supports a Zipkin exporter which it has recently deprecated.

### Zipkin Exporter

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.ZipkinExporterConfig.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-telem-6].
<!--/include-->


Configuration for Zipkin exporters

The [OpenTelemetry documentation][opentelemetry-do] describes the defaults; see
the "Properties for Zipkin span exporters" section there.

OpenTelemetry defaults for Zipkin exporters:

| Setting       | OpenTelemetry default value          |
|---------------|--------------------------------------|
| `compression` | `none`                               |
| `encoder`     | `JSON_V2`                            |
| `endpoint`    | <http://localhost:9411/api/v2/spans> |
| `timeout`     | 10 seconds                           |

OpenTelemetry provides other exporters, often used for debugging:

- `console`

  Writes telemetry data at the `INFO` level using the `java.util.logging.Logger`
  for `io.opentelemetry.exporter.logging.Logging{signal}Expoerter`

- `logging-otlp`

  Writes telemetry data in JSON format to the logger for the particular
  OpenTelemetry implementation class (e.g., `OtlpJsonLoggingMetricExporter`).

The `console` and `logging-otlp` have no configuration that is common across all
signals.

> [!NOTE]
> You need to add dependencies to your project for the exporters your
> application uses, even ones supported by Helidon config.

The table below describes the exporter types that Helidon configuration supports
and what dependency your project needs to support them.

If you need to use an exporter that is *not* in the table:

- Add a dependency on the OpenTelemetry artifact that contains that exporter
  type.
- Add application code that prepares the exporter instance.
- Prepare the Helidon OpenTelemetry builders programmatically and add your
  exporter instance to the builder.

In the table below, the Maven artifacts are all in the `io.opentelemetry` group.

<table>
<thead>
<tr>
<th>Exporter type</th>
<th>OpenTelemetry Java Type</th>
<th>Artifact ID to add - <code>see</code>also the <a href="https://opentelemetry.io/docs/languages/java/sdk/#spanexporter">OpenTelemetry documentation</a></th>
</tr>
</thead>
<tbody>
<tr>
<td rowspan="2"><p><code>otlp</code></p></td>
<td><p><code>OtlpGrpc{signal}Exporter</code></p></td>
<td rowspan="2" ><p><code>opentelemetry-exporter-otlp</code></p></td>
</tr>
<tr>
<td><p><code>OtlpHttp{signal}Exporter</code></p></td>
</tr>
<tr>
<td><p><a href="../../config/io.helidon.telemetry.otelconfig.ZipkinExporterConfig.md"><code>zipkin</code></a></p></td>
<td><p><code>ZipkinSpanExporter</code></p></td>
<td><p><code>opentelemetry-exporter-zipkin</code></p></td>
</tr>
<tr>
<td><p><code>console</code></p></td>
<td><p><code>Logging{signal}Exporter</code></p></td>
<td rowspan="2" ><p><code>opentelemetry-exporter-logging</code></p></td>
</tr>
<tr>
<td><p><code>logging_otlp</code></p></td>
<td><p><code>OtlpJsonLogging{signal}Exporter</code></p>
<p><code>SystemOutLogRecordExporter</code></p></td>
</tr>
</tbody>
</table>

### Assigning Exporters

In configuration, you link processors and readers with the exporters you want
each to use as follows:

- For clarity, name each exporter if you have more than one.
- Optionally specify for each processor or reader the names of the exporters it
  should use.

  If you omit the exporter names for a processor or reader, Helidon associates
  it with all configured exporters. If you configure no exporters explicitly,
  Helidon associates the OpenTelemetry default exporter with the processor or
  reader.

The following examples show increasingly-complicated scenarios using tracing as
the signal:

- Default
- Minimal configuration
- Maximum flexibility

For many applications the default and minimal scenarios work well.

#### Default

This scenario includes no configuration at all for either processors or
exporters.

OpenTelemetry uses its default processor (`batch`) with its default exporter
(`otlp` using `grpc`). \|

```yaml
telemetry:
  service: "inventory"
  tracing:
    sampler: "always_off"
```

#### Minimal configuration

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

#### Maximum flexibility

The user configures possibly multiple processors and possibly multiple named
exporters. Each processor’s configuration lists the names of the exporters it
should use; no names means all exporters.

The first processor (type `batch`) uses both exporters because it does not
specify any exporter names. The second processor uses only the `alternate-otlp`
exporter.\|

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

## Tracing Configuration

The settings under `signals.tracing` prepare an OpenTelemetry `TracerProvider`.
When your application uses the Helidon tracing API to obtain a `Tracer`, Helidon
uses the `TracerProvider` prepared from this config to create the tracer.

The next table describes the OpenTelemetry tracing settings.

### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-7].
<!--/include-->


OpenTelemetry applies the defaults described in the next table.

| Setting       | OpenTelemetry default (and OpenTelemetry doc link)                                                             |
|---------------|----------------------------------------------------------------------------------------------------------------|
| `exporters`   | [`otlp` with `grpc` protocol][opentelemetry-do] - see "Properties: exporters, `otel.traces.exporter` property" |
| `processors`  | [`batch` with defaults][batch-with-defau] - see "Properties for batch span processor(s)"                       |
| `sampler`     | [`parentbased_always_on`][batch-with-defau] - see "Properties for sampler"                                     |
| `span-limits` | See [tracing][batch-with-defau] "Properties for span limits"                                                   |

### Span Sampler

OpenTelemetry offers different ways of sampling data—deciding which tracing
spans tp capture and send to the backend. The [OpenTelemetry
documentation][opentelemetry-do-2] describes sampling in more detail.

Helidon configuration supports the sampler implementations that reside in the
`opentelemetry-sdk` as listed in the table below. Other samplers are in other
components. If you need to use one of those:

- Add the relevant OpenTelemetry dependency to your project.
- Instantiate the span sample you need.
- Prepare the sampler and the OpenTelemetry-related builders programmatically
  and use your sampler to assign the sampler the `OpenTelemetryTracer.Builder`
  should use.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.SamplerConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-8].
<!--/include-->


### Span Limits

OpenTelemetry allows you to constrain certain aspects of the data it gathers in
tracing spans. By assigning the settings in the table below, you can apply the
span limits you want.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.SpanLimitsConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-9].
<!--/include-->


The [OpenTelemetry documentation][opentelemetry-do-2] describes the defaults;
see the "Properties for span limits" section there.

OpenTelemetry defaults for span limits:

| Setting                      | OpenTelemetry Default |
|------------------------------|-----------------------|
| `max-attribute-value-length` | no limit              |
| `max-attributes`             | 128                   |
| `max-attributes-per-event`   | 128                   |
| `max-events`                 | 128                   |
| `max-links`                  | 128                   |

## Metrics Configuration

The settings under `signals.metrics` prepare an OpenTelemetry `MeterProvider`.
If your code uses the OpenTelemetry API to obtain an OpenTelemetry meter, meter
provider, or meter builder, OpenTelemetry uses the `MeterProvider` prepared from
this configuration.

The sections below describe Helidon config settings that correspond very
directly to OpenTelemetry builders for the relevant OpenTelemetry type. Refer to
the relevant OpenTelemetry documentation or Javadoc to understand the effect
each setting has.

### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-10].
<!--/include-->

OpenTelemetry applies the defaults described in the next table.

| Setting     | OpenTelemetry default (and OpenTelemetry doc link)                                                                                         |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `exporters` | [`otlp` with `grpc` protocol][opentelemetry-do] - see that web page’s "Properties: exporters, `otel.metrics.exporter` property"\] section. |
| `readers`   | [`PeriodicMetricReader`][periodicmetricre] with an interval of one minute                                                                  |

The following example illustrates some of the ways you can configure
OpenTelemetry metrics behavior. It is neither complete nor typical.

<!--@mdc ::code-callout{collapsed} -->
```yaml
telemetry:
  service: "test-telemetry"
  signals:
    metrics:                                          # <1>
      exporters:
        - name: exp-1                                 # <2>
          type: otlp
          endpoint: "http://host:1234"
          temporality-preference: cumulative          # <3>
          default-histogram-aggregation:              # <4>
            type: base2-exponential-bucket-histogram
            max-buckets: 152
            max-scale: 19
        - name: exp-2                                 # <5>
          type: otlp
          protocol: grpc
          temporality-preference: delta               # <6>
          default-histogram-aggregation:              # <7>
            type: explicit-bucket-histogram
            bucket-boundaries: [3,5,7]
      readers:
        - type: periodic                              # <8>
          exporter: exp-1
          interval: PT6S
      views:
        - name: sum-view                              # <9>
          aggregation:
            type: sum
          description: "Sum view"
          instrument-selector:
            name: counter-selector
            type: counter
            meter-name: my-counter
```
1. Introduces the metrics configuration.
2. Introduces the first metric exporter (with name `exp-1`).
3. Indicates to accumulate measurement values since the previous transmission.
4. Prescribes to aggregate histograms for transmission using the OpenTelemetry
   `BASE2_EXPONENTIAL_BUCKET_HISTOGRAM` technique with the specified maximum
   number of buckets and maximum scale.
5. Introduces the second metric exporter (with name `exp-2`).
6. Indicates to transmit deltas since the last transmission.
7. Prescribes to aggregate histograms using a histogram with the given explicit
   bucket boundary values.
8. Declares a single metric reader of the OpenTelemetry `PERIODIC` types
   gathering data each 6 seconds.
9. Declares a single view to influence the transmission of the `my-counter`
   counter data.
<!--@mdc :: -->

### Metric Exporters

The configuration for metrics exporters has several additional settings beyond
those described earlier for exporters in general.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.MetricExporterConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-11].
<!--/include-->

### Metric Aggregation

OpenTelemetry allows control over how each exporter aggregates histogram data
prior to transmission to a backend.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.MetricDefaultHistogramAggregationConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-12].
<!--/include-->

You can configure the explicit bucket boundaries for `EXPLICIT_BUCKET_HISTOGRAM`
aggregation.

<!--@include ../../config/io.helidon.telemetry.otelconfig.ExplicitBucketHistogramAggregationConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-13].
<!--/include-->

You can configure the exponential histogram aggregation behavior.

<!--@include ../../config/io.helidon.telemetry.otelconfig.Base2ExponentialHistogramAggregationConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-14].
<!--/include-->


### Metric Readers

An OpenTelemetry metric reader collects metric data in the server and then uses
the associated metric exporter to send that data to the endpoint configured.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.MetricReaderConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-15].
<!--/include-->

The periodic reader supports the following settings.

<!--@include ../../config/io.helidon.telemetry.otelconfig.PeriodicMetricReaderConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-16].
<!--/include-->

### Metric Views

OpenTelemetry metric views allow you to influence how meters are aggregated for
reporting to backend systems.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.ViewRegistrationConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-17].
<!--/include-->

The instrument selector controls which meters this view reflects.

<!--@include ../../config/io.helidon.telemetry.otelconfig.InstrumentSelectorConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-18].
<!--/include-->

## Logging Configuration

The settings under `signal.logging` prepare an OpenTelemetry `LoggerProvider`.

The sections below describe Helidon config settings that correspond directly to
OpenTelemetry builders for the relevant OpenTelemetry type. Refer to the
relevant OpenTelemetry documentation or Javadoc to understand the effect each
setting has.

### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-telem-19].
<!--/include-->

OpenTelemetry uses the following defaults:

| Setting            | OpenTelemetry default     |
|--------------------|---------------------------|
| `exporters`        | none                      |
| `minimum-severity` | undefined severity number |
| `processors`       | no-op processor           |
| `trace-based`      | `false`                   |

### Log Limits

For defaults, Helidon defers to the OpenTelemetry defaults, listed below.

#### Configuration options

<!--@include ../../config/io.helidon.telemetry.otelconfig.LogLimitsConfig.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-telem-20].
<!--/include-->


OpenTelemetry applies the following defaults:

| Setting                      | OpenTelemetry default                                                                                                                   |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `max-attribute-value-length` | `Integer.MAX_VALUE`                                                                                                                     |
| `max-number-of-attributes`   | 128                                                                                                                                     |
| `exporters`                  | [`otlp` with `grpc` protocol][opentelemetry-do] - see that web page’s "Properties: exporters, `otel.logger.exporter` property" section. |
| `log-limits`                 | `max-`                                                                                                                                  |
| `processors`                 | [`otlp`][otlp] - see that web page’s "Properties: logs" section.                                                                        |

The following example illustrates some of the ways you can configure
OpenTelemetry logger behavior. It is neither complete nor typical.

<!--@mdc ::code-callout -->
```yaml [application.yaml]
telemetry:
  service: test-tel-logging
  global: false
  signals:
    logging:                              # <1>
      minimum-severity: TRACE             # <2>
      log-limits:                         # <3>
        max-attribute-value-length: 20
        max-number-of-attributes: 14
      processors:                         # <4>
        - type: batch
          schedule-delay: PT10S
          max-queue-size: 15
          max-export-batch-size: 5
          timeout: PT30S
        - type: simple
      exporters:                          # <5>
        - name: exp-1
          endpoint: "http://host:1234"
```
1. Introduces the logger configuration.
2. Sets the minimum log level severity to of log messages to send to the backend
   system.
3. Configures limits related to attributes that accompany log messages.
4. Prescribes the logger processors.
5. Prescribes the logger exporters.
<!--@mdc :: -->

## References

- [Helidon Tracing](../../se/tracing.md)
- [Settings and defaults][opentelemetry-do]
- [OpenTelemetry Java SDK reference][opentelemetry-ja]
- [HTTP semantic conventions][opentelemetry-se]
- [Intro to OpenTelemetry Java][intro-to-opentel]

[preview-feature]: https://helidon.io/docs/v4/apidocs/io.helidon.common.features.api/io/helidon/common/features/api/Preview.html
[opentelemetry-se]: https://github.com/open-telemetry/semantic-conventions/blob/v1.37.0/docs/http/http-spans.md#http-server
[publishing-helid]: ../../se/metrics/metrics.md#publishing-metrics
[signals]: https://opentelemetry.io/docs/concepts/signals/
[helidon-opentele]: https://helidon.io/docs/v4/apidocs/io.helidon.telemetry.otelconfig/io/helidon/telemetry/otelconfig/package-summary.html
[opentelemetrycon]: https://helidon.io/docs/v4/apidocs/io.helidon.telemetry.otelconfig/io/helidon/telemetry/otelconfig/OpenTelemetryConfig.html
[builder]: https://helidon.io/docs/v4/apidocs/io.helidon.telemetry.otelconfig/io/helidon/telemetry/otelconfig/OpenTelemetryConfig.BuilderBase.html
[opentelemetry-sd]: https://opentelemetry.io/docs/languages/java/sdk/#sdk-components
[helidon-webclien]: ../../se/webclient.md#configuring-telemetry
[this-section-bel]: #additional-dependencies
[this-opentelemet]: https://github.com/open-telemetry/opentelemetry-java/tree/v1.58.0/exporters
[this-opentelemet-2]: https://opentelemetry.io/docs/languages/java/configuration/#properties-general
[opentelemetry-do]: https://opentelemetry.io/docs/languages/java/configuration/#properties-exporters
[batch-with-defau]: https://opentelemetry.io/docs/languages/java/configuration/#properties-traces
[opentelemetry-do-2]: https://opentelemetry.io/docs/languages/java/sdk/#sampler
[periodicmetricre]: https://opentelemetry.io/docs/languages/java/configuration/#properties-metrics
[otlp]: https://opentelemetry.io/docs/languages/java/configuration/#properties-logs
[opentelemetry-ja]: https://opentelemetry.io/docs/languages/java/sdk
[intro-to-opentel]: https://opentelemetry.io/docs/languages/java/intro/
[io-helidon-telem]: ../../config/io.helidon.telemetry.otelconfig.HelidonOpenTelemetry.md#configuration-options
[io-helidon-telem-2]: ../../config/io.helidon.telemetry.otelconfig.TypedAttributes.md#configuration-options
[io-helidon-telem-3]: ../../config/io.helidon.telemetry.otelconfig.BatchProcessorConfig.md#configuration-options
[io-helidon-telem-4]: ../../config/io.helidon.telemetry.otelconfig.OtlpExporterConfig.md#configuration-options
[io-helidon-telem-5]: ../../config/io.helidon.telemetry.otelconfig.RetryPolicyConfig.md#configuration-options
[io-helidon-telem-6]: ../../config/io.helidon.telemetry.otelconfig.ZipkinExporterConfig.md#configuration-options
[io-helidon-telem-7]: ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig.md#configuration-options
[io-helidon-telem-8]: ../../config/io.helidon.telemetry.otelconfig.SamplerConfig.md#configuration-options
[io-helidon-telem-9]: ../../config/io.helidon.telemetry.otelconfig.SpanLimitsConfig.md#configuration-options
[io-helidon-telem-10]: ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig.md#configuration-options
[io-helidon-telem-11]: ../../config/io.helidon.telemetry.otelconfig.MetricExporterConfig.md#configuration-options
[io-helidon-telem-12]: ../../config/io.helidon.telemetry.otelconfig.MetricDefaultHistogramAggregationConfig.md#configuration-options
[io-helidon-telem-13]: ../../config/io.helidon.telemetry.otelconfig.ExplicitBucketHistogramAggregationConfig.md#configuration-options
[io-helidon-telem-14]: ../../config/io.helidon.telemetry.otelconfig.Base2ExponentialHistogramAggregationConfig.md#configuration-options
[io-helidon-telem-15]: ../../config/io.helidon.telemetry.otelconfig.MetricReaderConfig.md#configuration-options
[io-helidon-telem-16]: ../../config/io.helidon.telemetry.otelconfig.PeriodicMetricReaderConfig.md#configuration-options
[io-helidon-telem-17]: ../../config/io.helidon.telemetry.otelconfig.ViewRegistrationConfig.md#configuration-options
[io-helidon-telem-18]: ../../config/io.helidon.telemetry.otelconfig.InstrumentSelectorConfig.md#configuration-options
[io-helidon-telem-19]: ../../config/io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig.md#configuration-options
[io-helidon-telem-20]: ../../config/io.helidon.telemetry.otelconfig.LogLimitsConfig.md#configuration-options
