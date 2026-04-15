# io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig

## Description

Configuration for LangChain4j model OracleEmbeddingStore

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
<td><code>data-source</code></td>
<td><code>DataSource</code></td>
<td></td>
<td>Configures a data source that connects to an Oracle Database</td>
</tr>
<tr>
<td><code>data-source-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;data-source&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="embedding-table"></a><a href="io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig.md"><code>embedding-table</code></a></td>
<td><code>EmbeddingTableConfig</code></td>
<td></td>
<td>Configures a table used to store embeddings, text, and metadata</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, OracleEmbeddingStore will not be available even if configured</td>
</tr>
<tr>
<td><code>exact-search</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore.Builder#exactSearch(boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="ivf-index"></a><a href="io.helidon.integrations.langchain4j.providers.oracle.IvfIndexConfig.md"><code>ivf-index</code></a></td>
<td><code>List&lt;IvfIndexConfig&gt;</code></td>
<td></td>
<td>IVFIndex allows configuring an Inverted File Flat (IVF) index on the embedding column of the &lt;code&gt;EmbeddingTable&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="json-index"></a><a href="io.helidon.integrations.langchain4j.providers.oracle.JsonIndexConfig.md"><code>json-index</code></a></td>
<td><code>List&lt;JsonIndexConfig&gt;</code></td>
<td></td>
<td>JSONIndex allows configuring a function-based index on one or several keys of the metadata column of the &lt;code&gt;EmbeddingTable&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="oracle"></a><a href="io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig.md"><code>oracle</code></a></td>
<td></td>
<td></td>
<td>Configuration for oracle</td>
</tr>
<tr>
<td><a id="vector-index"></a><a href="dev.langchain4j.store.embedding.oracle.CreateOption.md"><code>vector-index</code></a></td>
<td><code>CreateOption</code></td>
<td></td>
<td>The vector index creation option, which defines behavior when creating the vector index</td>
</tr>
</tbody>
</table>


## Usages

- [`langchain4j.providers.oracle`](io.helidon.langchain4j.ProvidersConfig.md#oracle)

---

See the [manifest](manifest.md) for all available types.
