# io.helidon.integrations.langchain4j.providers.coherence.CoherenceEmbeddingStoreConfig

## Description

Configuration for LangChain4j model CoherenceEmbeddingStore

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
<td><code>EmbeddingModel</code></td>
<td></td>
<td>The embedding model to use</td>
</tr>
<tr>
<td><code>normalize-embeddings</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#normalizeEmbeddings(boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>session</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#session(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#name(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>index</code></td>
<td><code>String</code></td>
<td></td>
<td>The index name to use</td>
</tr>
<tr>
<td><code>embedding-model-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;embedding-model&lt;/code&gt;</td>
</tr>
<tr>
<td><code>dimension</code></td>
<td><code>Integer</code></td>
<td></td>
<td>The number of dimensions in the embeddings</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, CoherenceEmbeddingStore will not be available even if configured</td>
</tr>
</tbody>
</table>


## Usages

- [`langchain4j.providers.coherence`](io.helidon.langchain4j.ProvidersConfig.md#coherence)

---

See the [manifest](manifest.md) for all available types.
