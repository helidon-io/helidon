# io.helidon.integrations.oci.metrics.OciMetricsSupport

## Description

OCI Metrics Support.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a143b1-batch-delay"></span> `batch-delay` | `VALUE` | `Long` | `1` | Sets the delay interval if metrics are posted in batches (defaults to `#DEFAULT_BATCH_DELAY`) |
| <span id="a2f02d-batch-size"></span> `batch-size` | `VALUE` | `Integer` | `50` | Sets the maximum no |
| <span id="aeee37-compartment-id"></span> `compartment-id` | `VALUE` | `String` |   | Sets the compartment ID |
| <span id="a989a3-delay"></span> `delay` | `VALUE` | `Long` | `60` | Sets the delay interval between metric posting (defaults to `#DEFAULT_SCHEDULER_DELAY`) |
| <span id="a75a1e-description-enabled"></span> `description-enabled` | `VALUE` | `Boolean` | `true` | Sets whether the description should be enabled or not |
| <span id="ae3003-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Sets whether metrics transmission to OCI is enabled |
| <span id="a7b3f6-initial-delay"></span> `initial-delay` | `VALUE` | `Long` | `1` | Sets the initial delay before metrics are sent to OCI (defaults to `#DEFAULT_SCHEDULER_INITIAL_DELAY`) |
| <span id="ab35c7-namespace"></span> `namespace` | `VALUE` | `String` |   | Sets the namespace |
| <span id="aced2d-resource-group"></span> `resource-group` | `VALUE` | `String` |   | Sets the resource group |
| <span id="aea7b6-scheduling-time-unit"></span> [`scheduling-time-unit`](../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` | `TimeUnit.SECONDS` | Sets the time unit applied to the initial delay and delay values (defaults to `TimeUnit.SECONDS`) |
| <span id="ae71bc-scopes"></span> `scopes` | `LIST` | `String` | `All scopes` | Sets which metrics scopes (e.g., base, vendor, application) should be sent to OCI |

See the [manifest](../config/manifest.md) for all available types.
