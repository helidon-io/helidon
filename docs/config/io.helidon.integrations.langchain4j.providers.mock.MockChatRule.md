# io.<wbr>helidon.<wbr>integrations.<wbr>langchain4j.<wbr>providers.<wbr>mock.<wbr>Mock<wbr>Chat<wbr>Rule

## Description

Configuration blueprint for <code>Mock<wbr>Chat<wbr>Rule</code>

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>template</code>
</td>
<td>
<code>String</code>
</td>
<td>Response template (e.g., using placeholders ex.: '$1' for regex pattern group 1) used when the pattern matches</td>
</tr>
<tr>
<td>
<code>response</code>
</td>
<td>
<code>String</code>
</td>
<td>Static text response that will be returned when the pattern matches</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td>
<code>Pattern</code>
</td>
<td>The regular expression pattern that this rule matches</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.langchain4j.providers.HelidonMockConfig.md#rules"><code>langchain4j.<wbr>providers.<wbr>helidon-<wbr>mock.<wbr>rules</code></a>

---

See the [manifest](manifest.md) for all available types.
