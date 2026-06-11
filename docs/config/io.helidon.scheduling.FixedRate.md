# io.helidon.scheduling.FixedRate

## Description

Scheduling periodically executed task with specified fixed rate

## Configuration options


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
<a id="delay-type"></a>
<a href="io.helidon.scheduling.FixedRate.DelayType.md">
<code>delay-<wbr>type</code>
</a>
</td>
<td>
<code>Delay<wbr>Type</code>
</td>
<td>
<code>SINCE_<wbr>PREVIOUS_<wbr>START</code>
</td>
<td>Configure whether the interval between the invocations should be calculated from the time when previous task started or ended</td>
</tr>
<tr>
<td>
<code>delay-<wbr>by</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT0S</code>
</td>
<td>Initial delay of the first invocation</td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Fixed interval between each invocation</td>
</tr>
<tr>
<td>
<code>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Identification of the started task</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether the task is enabled</td>
</tr>
</tbody>
</table>


### Deprecated Options


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
<code>delay</code>
</td>
<td>
<code>Long</code>
</td>
<td>
</td>
<td>Fixed rate delay between each invocation</td>
</tr>
<tr>
<td>
<a id="time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>time-<wbr>unit</code>
</a>
</td>
<td>
<code>Time<wbr>Unit</code>
</td>
<td>
<code>Time<wbr>Unit.<wbr>SECONDS</code>
</td>
<td><code>java.<wbr>util.<wbr>concurrent.<wbr>Time<wbr>Unit Time<wbr>Unit</code> used for interpretation of values provided with <code>io.<wbr>helidon.<wbr>scheduling.<wbr>Fixed<wbr>Rate<wbr>Config.<wbr>Builder#<wbr>delay(<wbr>long)</code> and <code>io.<wbr>helidon.<wbr>scheduling.<wbr>Fixed<wbr>Rate<wbr>Config.<wbr>Builder#<wbr>initial<wbr>Delay(<wbr>long)</code></td>
</tr>
<tr>
<td>
<code>initial-<wbr>delay</code>
</td>
<td>
<code>Long</code>
</td>
<td>
</td>
<td>Initial delay of the first invocation</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
