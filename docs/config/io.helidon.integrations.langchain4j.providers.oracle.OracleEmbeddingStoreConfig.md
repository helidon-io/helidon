# io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig

## Description

Configuration for LangChain4j model OracleEmbeddingStore

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
<code>data-<wbr>source</code>
</td>
<td>
<code>Data<wbr>Source</code>
</td>
<td>
</td>
<td>Configures a data source that connects to an Oracle Database</td>
</tr>
<tr>
<td>
<code>data-<wbr>source-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>data-<wbr>source</code></td>
</tr>
<tr>
<td>
<a id="embedding-table"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig.md">
<code>embedding-<wbr>table</code>
</a>
</td>
<td>
<code>Embedding<wbr>Table<wbr>Config</code>
</td>
<td>
</td>
<td>Configures a table used to store embeddings, text, and metadata</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If set to <code>false</code>, OracleEmbeddingStore will not be available even if configured</td>
</tr>
<tr>
<td>
<code>exact-<wbr>search</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>store.<wbr>embedding.<wbr>oracle.<wbr>Oracle<wbr>Embedding<wbr>Store.<wbr>Builder#<wbr>exact<wbr>Search(<wbr>boolean)</code></td>
</tr>
<tr>
<td>
<a id="ivf-index"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.IvfIndexConfig.md">
<code>ivf-<wbr>index</code>
</a>
</td>
<td>
<code>List&lt;<wbr>IvfIndex<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>IVFIndex allows configuring an Inverted File Flat (IVF) index on the embedding column of the <code>Embedding<wbr>Table</code></td>
</tr>
<tr>
<td>
<a id="json-index"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.JsonIndexConfig.md">
<code>json-<wbr>index</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Json<wbr>Index<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>JSONIndex allows configuring a function-based index on one or several keys of the metadata column of the <code>Embedding<wbr>Table</code></td>
</tr>
<tr>
<td>
<a id="oracle"></a>
<a href="io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig.md">
<code>oracle</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for oracle</td>
</tr>
<tr>
<td>
<a id="vector-index"></a>
<a href="dev.langchain4j.store.embedding.oracle.CreateOption.md">
<code>vector-<wbr>index</code>
</a>
</td>
<td>
<code>Create<wbr>Option</code>
</td>
<td>
</td>
<td>The vector index creation option, which defines behavior when creating the vector index</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.providers.oracle`](io.helidon.langchain4j.ProvidersConfig.md#oracle)

---

See the [manifest](manifest.md) for all available types.
