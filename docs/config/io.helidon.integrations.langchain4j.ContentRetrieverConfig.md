# io.helidon.integrations.langchain4j.ContentRetrieverConfig

## Description

Configuration for LangChain4j &lt;code&gt;ContentRetriever&lt;/code&gt; components

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>embedding-model</code></td>
<td><code>String</code></td>
<td></td>
<td>Explicit embedding model to use in the content retriever</td>
</tr>
<tr>
<td><code>display-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Display name for this content retriever configuration</td>
</tr>
<tr>
<td><code>embedding-store</code></td>
<td><code>String</code></td>
<td></td>
<td>Embedding store to use in the content retriever</td>
</tr>
<tr>
<td><code>max-results</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Maximum number of results to return from the retriever</td>
</tr>
<tr>
<td><a id="type"></a><a href="io.helidon.integrations.langchain4j.ContentRetrieverType.md"><code>type</code></a></td>
<td><code>ContentRetrieverType</code></td>
<td><code>EMBEDDING_STORE_CONTENT_RETRIEVER</code></td>
<td>Type of content retriever to create</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, component will be disabled even if configured</td>
</tr>
<tr>
<td><code>min-score</code></td>
<td><code>Double</code></td>
<td></td>
<td>Minimum score threshold for retrieved results</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
