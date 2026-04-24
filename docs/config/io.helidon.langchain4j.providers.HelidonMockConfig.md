# io.helidon.langchain4j.providers.HelidonMockConfig

## Description

Merged configuration for langchain4j.providers.helidon-mock

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to <code>false</code> , MockChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<a id="rules"></a>
<a href="io.helidon.integrations.langchain4j.providers.mock.MockChatRule.md">
<code>rules</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;MockChatRule&gt;">List&lt;MockChatRule&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>The list of <code>MockChatRule</code>s that the mock chat model evaluates</td>
</tr>
</tbody>
</table>



## Merged Types

- [io.helidon.integrations.langchain4j.providers.mock.MockChatModel](io.helidon.integrations.langchain4j.providers.mock.MockChatModel.md)
- [io.helidon.integrations.langchain4j.providers.mock.MockStreamingChatModel](io.helidon.integrations.langchain4j.providers.mock.MockStreamingChatModel.md)

## Usages

- [`langchain4j.providers.helidon-mock`](io.helidon.langchain4j.ProvidersConfig.md#helidon-mock)

---

See the [manifest](manifest.md) for all available types.
