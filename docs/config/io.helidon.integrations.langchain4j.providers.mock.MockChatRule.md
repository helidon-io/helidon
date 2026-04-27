# io.helidon.integrations.langchain4j.providers.mock.MockChatRule

## Description

Configuration blueprint for <code>MockChatRule</code>

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Response template (e.g., using placeholders ex.: '$1' for regex pattern group 1) used when the pattern matches</td>
</tr>
<tr>
<td>
<code>response</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Static text response that will be returned when the pattern matches</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Pattern</code>
</td>
<td>The regular expression pattern that this rule matches</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.providers.helidon-mock.rules`](io.helidon.langchain4j.providers.HelidonMockConfig.md#rules)

---

See the [manifest](manifest.md) for all available types.
