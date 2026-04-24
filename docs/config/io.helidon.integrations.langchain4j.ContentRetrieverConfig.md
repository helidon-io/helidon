# io.helidon.integrations.langchain4j.ContentRetrieverConfig

## Description

Configuration for LangChain4j <code>ContentRetriever</code> components

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
<code>embedding-model</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Explicit embedding model to use in the content retriever</td>
</tr>
<tr>
<td>
<code>display-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Display name for this content retriever configuration</td>
</tr>
<tr>
<td>
<code>embedding-store</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Embedding store to use in the content retriever</td>
</tr>
<tr>
<td>
<code>max-results</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Maximum number of results to return from the retriever</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.integrations.langchain4j.ContentRetrieverType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ContentRetrieverType">ContentRetrieverType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="EMBEDDING_STORE_CONTENT_RETRIEVER">EMBEDDING_STORE_CONTENT_RETRIEVER</code>
</td>
<td>Type of content retriever to create</td>
</tr>
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
<td>If set to <code>false</code>, component will be disabled even if configured</td>
</tr>
<tr>
<td>
<code>min-score</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Minimum score threshold for retrieved results</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
