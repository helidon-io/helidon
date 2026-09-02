# io.<wbr>helidon.<wbr>server.<wbr>features.<wbr>openapi.<wbr>Generated<wbr>Config

## Description

Configuration for server.features.openapi.generated

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
<code>document-<wbr>sources</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Named generated document metadata sources to use, in the order configured</td>
</tr>
<tr>
<td>
<a id="mode"></a>
<a href="io.helidon.openapi.OpenApiGeneratedMode.md">
<code>mode</code>
</a>
</td>
<td>
<code>Open<wbr>ApiGenerated<wbr>Mode</code>
</td>
<td>
<code>STATIC_<wbr>FIRST</code>
</td>
<td>Generated document source handling mode</td>
</tr>
<tr>
<td>
<code>operation-<wbr>ids</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Operation ids to use for generated Java methods</td>
</tr>
<tr>
<td>
<code>resolve-<wbr>config-<wbr>expressions</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether generated document sources resolve annotation string values as Helidon config expressions at runtime</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.openapi.OpenApiFeature.md#generated"><code>server.<wbr>features.<wbr>openapi.<wbr>generated</code></a>

---

See the [manifest](manifest.md) for all available types.
