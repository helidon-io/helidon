# io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig

## Description

Configuration for LangChain4j model OracleEmbeddingStore

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
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>data-source</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">DataSource</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configures a data source that connects to an Oracle Database</td>
</tr>
<tr>
<td>
<code>data-source-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>data-source</code></td>
</tr>
<tr>
<td>
<a id="embedding-table"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig.md">
<code>embedding-table</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="EmbeddingTableConfig">EmbeddingTableConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configures a table used to store embeddings, text, and metadata</td>
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
<td>If set to <code>false</code>, OracleEmbeddingStore will not be available even if configured</td>
</tr>
<tr>
<td>
<code>exact-search</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore.Builder#exactSearch(boolean)</code></td>
</tr>
<tr>
<td>
<a id="ivf-index"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.IvfIndexConfig.md">
<code>ivf-index</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;IvfIndexConfig&gt;">List&lt;IvfIndexConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>IVFIndex allows configuring an Inverted File Flat (IVF) index on the embedding column of the <code>EmbeddingTable</code></td>
</tr>
<tr>
<td>
<a id="json-index"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.JsonIndexConfig.md">
<code>json-index</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;JsonIndexConfig&gt;">List&lt;JsonIndexConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>JSONIndex allows configuring a function-based index on one or several keys of the metadata column of the <code>EmbeddingTable</code></td>
</tr>
<tr>
<td>
<a id="oracle"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig.md">
<code>oracle</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for oracle</td>
</tr>
<tr>
<td>
<a id="vector-index"></a>
<a href="dev.langchain4j.store.embedding.oracle.CreateOption.md">
<code>vector-index</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CreateOption">CreateOption</code>
</td>
<td class="cm-default-cell">
</td>
<td>The vector index creation option, which defines behavior when creating the vector index</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.providers.oracle`](io.helidon.langchain4j.ProvidersConfig.md#oracle)

---

See the [manifest](manifest.md) for all available types.
