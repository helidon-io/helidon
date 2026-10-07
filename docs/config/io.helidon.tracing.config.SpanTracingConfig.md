# io.<wbr>helidon.<wbr>tracing.<wbr>config.<wbr>Span<wbr>Tracing<wbr>Config

## Description

Configuration of a traced span

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
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the span to configure</td>
</tr>
<tr>
<td>
<code>new-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Configure a new name of this span</td>
</tr>
<tr>
<td>
<a id="logs"></a>
<a href="io.helidon.tracing.config.SpanLogTracingConfig.md">
<code>logs</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Span<wbr>LogTracing<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Add configuration of a traced span log</td>
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
<td>Configure whether this traced span is enabled or disabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.tracing.config.ComponentTracingConfig.md#spans"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>tracing.<wbr>components.<wbr>spans</code></a>

---

See the [manifest](manifest.md) for all available types.
