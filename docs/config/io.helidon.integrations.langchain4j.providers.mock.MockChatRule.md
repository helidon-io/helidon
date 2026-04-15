# io.helidon.integrations.langchain4j.providers.mock.MockChatRule

## Description

Configuration blueprint for &lt;code&gt;MockChatRule&lt;/code&gt;

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>template</code></td>
<td><code>String</code></td>
<td>Response template (e.g., using placeholders ex.: &#x27;$1&#x27; for regex pattern group 1) used when the pattern matches</td>
</tr>
<tr>
<td><code>response</code></td>
<td><code>String</code></td>
<td>Static text response that will be returned when the pattern matches</td>
</tr>
<tr>
<td><code>pattern</code></td>
<td><code>Pattern</code></td>
<td>The regular expression pattern that this rule matches</td>
</tr>
</tbody>
</table>


## Usages

- [`langchain4j.providers.helidon-mock.rules`](io.helidon.langchain4j.providers.HelidonMockConfig.md#rules)

---

See the [manifest](manifest.md) for all available types.
