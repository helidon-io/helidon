# io.helidon.faulttolerance.Retry

## Description

<code>Retry</code> configuration bean

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
<code>overall-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>Overall timeout of all retries combined</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT0.<wbr>2S</code>
</td>
<td>Base delay between try and retry</td>
</tr>
<tr>
<td>
<code>jitter</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT-<wbr>1S</code>
</td>
<td>Jitter for <code>Retry.<wbr>Jitter<wbr>Retry<wbr>Policy</code></td>
</tr>
<tr>
<td>
<code>calls</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>3</code>
</td>
<td>Number of calls (first try + retries)</td>
</tr>
<tr>
<td>
<code>delay-<wbr>factor</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>-1.<wbr>0</code>
</td>
<td>Delay retry policy factor</td>
</tr>
<tr>
<td>
<code>enable-<wbr>metrics</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Flag to enable metrics for this instance</td>
</tr>
</tbody>
</table>



## Usages

- [`fault-tolerance.retries`](io.helidon.FaultToleranceConfig.md#retries)

---

See the [manifest](manifest.md) for all available types.
