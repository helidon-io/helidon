# io.<wbr>helidon.<wbr>scheduling.<wbr>Fixed<wbr>Rate

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



---

See the [manifest](manifest.md) for all available types.
