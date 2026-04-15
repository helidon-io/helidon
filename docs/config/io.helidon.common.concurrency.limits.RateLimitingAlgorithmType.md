# io.helidon.common.concurrency.limits.RateLimitingAlgorithmType

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>TOKEN_BUCKET</code></td>
<td>Requests require tokens from a bucket that fills over time</td>
</tr>
<tr>
<td><code>FIXED_RATE</code></td>
<td>Requests are processed at a fixed rate</td>
</tr>
</tbody>
</table>

## Usages

- [`server.concurrency-limit.throughput.rate-limiting-algorithm`](io.helidon.common.concurrency.limits.ThroughputLimit.md#rate-limiting-algorithm)
- [`server.features.limits.concurrency-limit.throughput.rate-limiting-algorithm`](io.helidon.common.concurrency.limits.ThroughputLimit.md#rate-limiting-algorithm)
- [`server.sockets.concurrency-limit.throughput.rate-limiting-algorithm`](io.helidon.common.concurrency.limits.ThroughputLimit.md#rate-limiting-algorithm)

---

See the [manifest](manifest.md) for all available types.
