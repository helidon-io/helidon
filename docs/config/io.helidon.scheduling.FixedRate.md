# io.helidon.scheduling.FixedRate

## Description

Scheduling periodically executed task with specified fixed rate

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
<td><a id="delay-type"></a><a href="io.helidon.scheduling.FixedRate.DelayType.md"><code>delay-type</code></a></td>
<td><code>DelayType</code></td>
<td><code>SINCE_PREVIOUS_START</code></td>
<td>Configure whether the interval between the invocations should be calculated from the time when previous task started or ended</td>
</tr>
<tr>
<td><code>delay-by</code></td>
<td><code>Duration</code></td>
<td><code>PT0S</code></td>
<td>Initial delay of the first invocation</td>
</tr>
<tr>
<td><code>interval</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Fixed interval between each invocation</td>
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

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>delay</code></td>
<td><code>Long</code></td>
<td></td>
<td>Fixed rate delay between each invocation</td>
</tr>
<tr>
<td><a id="time-unit"></a><a href="java.util.concurrent.TimeUnit.md"><code>time-unit</code></a></td>
<td><code>TimeUnit</code></td>
<td><code>TimeUnit.SECONDS</code></td>
<td>&lt;code&gt;java.util.concurrent.TimeUnit TimeUnit&lt;/code&gt; used for interpretation of values provided with &lt;code&gt;io.helidon.scheduling.FixedRateConfig.Builder#delay(long)&lt;/code&gt; and &lt;code&gt;io.helidon.scheduling.FixedRateConfig.Builder#initialDelay(long)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>initial-delay</code></td>
<td><code>Long</code></td>
<td></td>
<td>Initial delay of the first invocation</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
