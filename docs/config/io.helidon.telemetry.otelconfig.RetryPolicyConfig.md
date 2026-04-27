# io.helidon.telemetry.otelconfig.RetryPolicyConfig

## Description

Retry policy settings

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>max-backoff</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Maximum backoff time</td>
</tr>
<tr>
<td>
<code>initial-backoff</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Initial backoff time</td>
</tr>
<tr>
<td>
<code>max-backoff-multiplier</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td>Maximum backoff multiplier</td>
</tr>
<tr>
<td>
<code>max-attempts</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Maximum number of retry attempts</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
