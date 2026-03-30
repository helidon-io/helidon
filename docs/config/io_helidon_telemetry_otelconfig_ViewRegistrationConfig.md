# io.helidon.telemetry.otelconfig.ViewRegistrationConfig

## Description

Settings for an OpenTelemetry metrics view registration.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a6e77f-aggregation"></span> `aggregation` | `VALUE` | `i.h.t.o.V.CustomMethods` | Aggregation for the metric view, configurable as an `io.helidon.telemetry.otelconfig.AggregationType`: `DROP, DEFAULT, SUM, LAST_VALUE, EXPLICIT_BUCKET_HISTOGRAM, BASE2_EXPONENTIAL_BUCKET_HISTOGRAM` |
| <span id="a2d426-attribute-filter"></span> `attribute-filter` | `VALUE` | `i.h.t.o.V.CustomMethods` | Attribute name filter, configurable as a string compiled as a regular expression using `java.util.regex.Pattern` |
| <span id="ae87fb-cardinality-limit"></span> `cardinality-limit` | `VALUE` | `Integer` | Cardinality limit |
| <span id="abda85-description"></span> `description` | `VALUE` | `String` | Metric view description |
| <span id="acbe0f-instrument-selector"></span> `instrument-selector` | `VALUE` | `i.h.t.o.V.CustomMethods` | Instrument selector, configurable using `io.helidon.telemetry.otelconfig.InstrumentSelectorConfig` |
| <span id="a14ee9-name"></span> `name` | `VALUE` | `String` | Metrics view name |

See the [manifest](../config/manifest.md) for all available types.
