# io.helidon.scheduling.Cron

## Description

Scheduling periodically executed task with specified cron expression

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>expression</code></td>
<td><code>String</code></td>
<td></td>
<td>Cron expression for specifying period of execution</td>
</tr>
<tr>
<td><code>zone</code></td>
<td><code>ZoneId</code></td>
<td></td>
<td>Time zone to use for cron expression evaluation</td>
</tr>
<tr>
<td><code>concurrent</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Allow concurrent execution if previous task didn&#x27;t finish before next execution</td>
</tr>
<tr>
<td><code>id</code></td>
<td><code>String</code></td>
<td></td>
<td>Identification of the started task</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the task is enabled</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
