# io.helidon.faulttolerance.CircuitBreaker

## Description

Configuration of a circuit breaker.

## Usages

- [`fault-tolerance.circuit-breakers`](../config/config_reference.md#a6df4a-fault-tolerance-circuit-breakers)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa852b-delay"></span> `delay` | `VALUE` | `Duration` | `PT5S` | How long to wait before transitioning from open to half-open state |
| <span id="aa2947-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Flag to enable metrics for this instance |
| <span id="a847af-error-ratio"></span> `error-ratio` | `VALUE` | `Integer` | `60` | How many failures out of 100 will trigger the circuit to open |
| <span id="a9353a-success-threshold"></span> `success-threshold` | `VALUE` | `Integer` | `1` | How many successful calls will close a half-open circuit |
| <span id="ae6b91-volume"></span> `volume` | `VALUE` | `Integer` | `10` | Rolling window size used to calculate ratio of failed requests |

See the [manifest](../config/manifest.md) for all available types.
