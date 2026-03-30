# io.helidon.telemetry.otelconfig.AggregationType

## Description

This type is an enumeration.

## Usages

## Allowed Values

| Value | Description |
|----|----|
| `DROP` | Drops all metrics; exports no metrics |
| `DEFAULT` | Default aggregation for a given instrument type |
| `SUM` | Aggregates measurements into a double sum or long sum |
| `LAST_VALUE` | Records the last seen measurement as a double aauge or long gauge |
| `EXPLICIT_BUCKET_HISTOGRAM` | Aggregates measurements into a histogram using default or explicit bucket boundaries |
| `BASE2_EXPONENTIAL_BUCKET_HISTOGRAM` | Aggregates measurements into a base-2 exponential histogram using default or explicit maximum number of buckets and maximum scale |

See the [manifest](../config/manifest.md) for all available types.
