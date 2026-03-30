# io.helidon.common.concurrency.limits.Limit

## Description

This type is a provider contract.

## Usages

- [`server.concurrency-limit`](io_helidon_webserver_WebServer.md#a32e67-concurrency-limit)
- [`server.features.limits.concurrency-limit`](io_helidon_webserver_concurrency_limits_LimitsFeature.md#a5339e-concurrency-limit)
- [`server.sockets.concurrency-limit`](io_helidon_webserver_ListenerConfig.md#a87b14-concurrency-limit)

## Implementations

| Key | Type | Description |
|----|----|----|
| <span id="a1db99-aimd"></span> [`aimd`](io_helidon_common_concurrency_limits_AimdLimit.md) | `i.h.c.c.l.AimdLimit` | Configuration of `io.helidon.common.concurrency.limits.AimdLimit` |
| <span id="aaa497-fixed"></span> [`fixed`](io_helidon_common_concurrency_limits_FixedLimit.md) | `i.h.c.c.l.FixedLimit` | Configuration of `FixedLimit` |
| <span id="a13e9b-throughput"></span> [`throughput`](io_helidon_common_concurrency_limits_ThroughputLimit.md) | `i.h.c.c.l.ThroughputLimit` | Configuration of `ThroughputLimit` |

See the [manifest](../config/manifest.md) for all available types.
