# OpenMetrics Exemplar Support

## Overview

A meter typically reflects the usage of a *single* point in your service which processes *multiple* requests over time. A value such as the total time consumed by a given REST endpoint which can be invoked multiple times underscores the aggregate nature of meter values; Helidon accumulates the time from all requests in the total duration.

Tracing, on the other hand, captures the usage of *multiple* parts of your code as your service responds to a *single* request.

Metrics and tracing come together in Helidon’s support for exemplars.

> [!NOTE]
> [*exemplar*](https://www.merriam-webster.com/dictionary/exemplar) - one that serves as a model or example
>
>  — Merriam-Webster Dictionary

In the context of metrics, an *exemplar* for a given meter is a specific sample which, in some sense, made a typical contribution to the meter’s value. For example, an exemplar for a `Counter` might be the most recent sample which updated the counter. The metrics output identifies the exemplar sample using the span and trace IDs of the span and trace which triggered that sample.

Exemplar support in Helidon relies on the exemplar support provided by the underlying metrics implementation. Currently, Helidon’s Micrometer implementation supports exemplars as recorded by Micrometer’s Prometheus meter registry and exposed by the OpenMetrics output (media type `application/openmetrics-text`).

## Maven Coordinates

To enable OpenMetrics exemplar support, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.metrics</groupId>
    <artifactId>helidon-metrics-trace-exemplar</artifactId>
    <scope>runtime</scope>
</dependency>
```

Also, include the Helidon integration module for a tracing implementation (such as [Helidon Zipkin](../../mp/tracing.md#zipkin-tracing))

```xml
<dependency>
    <groupId>io.helidon.tracing.providers</groupId>
    <artifactId>helidon-tracing-providers-zipkin</artifactId>
</dependency>
```

Add the Helidon tracing component itself:

```xml
<dependency>
    <groupId>io.helidon.microprofile.tracing</groupId>
    <artifactId>helidon-microprofile-tracing</artifactId>
</dependency>
```

## Usage

Once you add the appropriate dependencies to your project, exemplar support runs automatically as part of the Helidon metrics implementation using Micrometer. You do not need to change your application or configuration.

### Interpreting Exemplars

Each exemplar reflects a sample described by a label, a value, and a timestamp. When a client accesses the `/metrics` endpoint and specifies that it accepts the `application/openmetrics-text` media type, the label, value, and timestamp appear in the OpenMetrics response for meters that support exemplars.

The exemplar information in the output describes a single, actual sample that is representative of the statistical value as recorded by the underlying Micrometer Prometheus meter registry.

### Output Format

In the OpenMetrics output, an exemplar actually appears as a comment appended to the normal OpenMetrics output.

*OpenMetrics format with exemplars*

meter-identifier meter-value # exemplar-label sample-timestamp

Even downstream consumers of OpenMetrics output that do not recognize the exemplar format should continue to work correctly (as long as they *do* recognize comments).

But some consumers, such as trace collectors and their UIs, understand the exemplar format, and they allow you to browse meters and then navigate directly to the trace for the meter’s exemplar.

## Examples

Once you enable exemplar support you can see the exemplars in the metrics output.

*Exemplar output - `Counter`*

## TYPE counterForPersonalizedGreetings counter
    # HELP counterForPersonalizedGreetings
    counterForPersonalizedGreetings_total{scope="application"} 4.0 # {span_id="6b1fc9f9fd42fb0c",trace_id="6b1fc9f9fd42fb0c"} 1.0 1696889651.779

The exemplar (the portion following the `#`) is a sample corresponding to an update to the counter, showing the span and trace identifiers, the amount by which the counter was updated (`1.0`), and the timestamp recording when the update occurred expressed as seconds in the UNIX epoch (`1696889651.779`).

## Additional Information

Brief discussion of [exemplars in the OpenMetrics spec](https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md#exemplars)
