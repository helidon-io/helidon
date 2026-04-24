# io.helidon.scheduling.Cron

## Description

Scheduling periodically executed task with specified cron expression

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>expression</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Cron expression for specifying period of execution</td>
</tr>
<tr>
<td>
<code>zone</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">ZoneId</code>
</td>
<td class="cm-default-cell">
</td>
<td>Time zone to use for cron expression evaluation</td>
</tr>
<tr>
<td>
<code>concurrent</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Allow concurrent execution if previous task didn't finish before next execution</td>
</tr>
<tr>
<td>
<code>id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Identification of the started task</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the task is enabled</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
