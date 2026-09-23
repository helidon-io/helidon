# io.<wbr>helidon.<wbr>metrics.<wbr>api.<wbr>Meter<wbr>Config

## Description

Registry settings for a meter identified by its exact name, regardless of its tags

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
<code>minimum-<wbr>expected-<wbr>value</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Minimum expected timer duration used to size the histogram, preserving builder settings when absent</td>
</tr>
<tr>
<td>
<code>percentiles</code>
</td>
<td>
<code>List&lt;<wbr>Double&gt;</code>
</td>
<td>
</td>
<td>Local timer percentiles, preserving builder settings when absent and disabling percentiles when explicitly empty</td>
</tr>
<tr>
<td>
<code>buckets</code>
</td>
<td>
<code>List&lt;<wbr>Duration&gt;</code>
</td>
<td>
</td>
<td>Explicit timer histogram bucket boundaries, preserving builder settings when absent and clearing explicit boundaries when empty</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Meter name to match exactly, before any exporter-specific naming conversion</td>
</tr>
<tr>
<td>
<code>maximum-<wbr>expected-<wbr>value</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Maximum expected timer duration used to size the histogram, preserving builder settings when absent</td>
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
<td>Whether the meter is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.MetricsConfig.md#meters"><code>metrics.<wbr>meters</code></a>
- <a href="io.helidon.webserver.observe.metrics.MetricsObserver.md#meters"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>metrics.<wbr>meters</code></a>

---

See the [manifest](manifest.md) for all available types.
