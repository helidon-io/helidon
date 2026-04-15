# io.helidon.telemetry.otelconfig.RetryPolicyConfig

## Description

Retry policy settings

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>max-backoff</code></td>
<td><code>Duration</code></td>
<td>Maximum backoff time</td>
</tr>
<tr>
<td><code>initial-backoff</code></td>
<td><code>Duration</code></td>
<td>Initial backoff time</td>
</tr>
<tr>
<td><code>max-backoff-multiplier</code></td>
<td><code>Double</code></td>
<td>Maximum backoff multiplier</td>
</tr>
<tr>
<td><code>max-attempts</code></td>
<td><code>Integer</code></td>
<td>Maximum number of retry attempts</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
