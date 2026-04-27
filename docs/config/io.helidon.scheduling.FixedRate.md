# io.helidon.scheduling.FixedRate

## Description

Scheduling periodically executed task with specified fixed rate

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
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="delay-type"></a>
<a href="io.helidon.scheduling.FixedRate.DelayType.md">
<code>delay-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">DelayType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="SINCE_PREVIOUS_START">SINCE_PREVIOUS_START</code>
</td>
<td>Configure whether the interval between the invocations should be calculated from the time when previous task started or ended</td>
</tr>
<tr>
<td>
<code>delay-by</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0S</code>
</td>
<td>Initial delay of the first invocation</td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Fixed interval between each invocation</td>
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


### Deprecated Options


<table class="cm-table">
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
<code>delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
</td>
<td>Fixed rate delay between each invocation</td>
</tr>
<tr>
<td>
<a id="time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>time-unit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">TimeUnit</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="TimeUnit.SECONDS">TimeUnit.SECONDS</code>
</td>
<td><code>java.util.concurrent.TimeUnit TimeUnit</code> used for interpretation of values provided with <code>io.helidon.scheduling.FixedRateConfig.Builder#delay(long)</code> and <code>io.helidon.scheduling.FixedRateConfig.Builder#initialDelay(long)</code></td>
</tr>
<tr>
<td>
<code>initial-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
</td>
<td>Initial delay of the first invocation</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
