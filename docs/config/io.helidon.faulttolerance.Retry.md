# io.<wbr>helidon.<wbr>faulttolerance.<wbr>Retry

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
<td>Positive overall timeout used to bound the complete retry sequence</td>
</tr>
<tr>
<td>
<code>max-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Optional non-negative maximum delay applied after jitter; when absent, the delay is not capped</td>
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
<td>Non-negative base delay between the initial call and retries, which defaults to <code>200 ms</code></td>
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
<td>Absolute random jitter that must be <code>PT-<wbr>1S</code> (disabled) or non-negative; it cannot be combined with <code>jitter-<wbr>factor</code>, is applied after <code>delay-<wbr>factor</code>, and is capped by <code>max-<wbr>delay</code> when present</td>
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
<td>Number of calls, including the initial call and retries, which must be at least <code>1</code></td>
</tr>
<tr>
<td>
<code>jitter-<wbr>factor</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>-1.<wbr>0</code>
</td>
<td>Relative random jitter that must be <code>-1</code> (disabled) or from <code>0</code> (inclusive) to <code>1</code> (exclusive); it cannot be combined with <code>jitter</code>, is applied after <code>delay-<wbr>factor</code>, and is capped by <code>max-<wbr>delay</code> when present</td>
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
<td>Delay multiplier that must be <code>-1</code> or finite and non-negative; <code>-1</code> selects <code>2</code> unless either jitter option is configured, and an explicit multiplier is applied before jitter</td>
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

- <a href="io.helidon.FaultToleranceConfig.md#retries"><code>fault-<wbr>tolerance.<wbr>retries</code></a>

---

See the [manifest](manifest.md) for all available types.
