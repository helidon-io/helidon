# io.<wbr>helidon.<wbr>webserver.<wbr>context.<wbr>Context<wbr>Feature

## Description

Configuration of context feature

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
<a id="records"></a>
<a href="io.helidon.common.context.http.ContextRecordConfig.md">
<code>records</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Context<wbr>Record<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>List of propagation records</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>1100.<wbr>0</code>
</td>
<td>Weight of the context feature</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>List of sockets to register this feature on</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.ServerFeature.md#context"><code>server.<wbr>features.<wbr>context</code></a>

---

See the [manifest](manifest.md) for all available types.
