# io.helidon.integrations.langchain4j.providers.coherence.CoherenceEmbeddingStoreConfig

## Description

Configuration for LangChain4j model CoherenceEmbeddingStore

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
<code>embedding-model</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="EmbeddingModel">EmbeddingModel</code>
</td>
<td class="cm-default-cell">
</td>
<td>The embedding model to use</td>
</tr>
<tr>
<td>
<code>normalize-embeddings</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#normalizeEmbeddings(boolean)</code></td>
</tr>
<tr>
<td>
<code>session</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#session(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#name(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>index</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The index name to use</td>
</tr>
<tr>
<td>
<code>embedding-model-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>embedding-model</code></td>
</tr>
<tr>
<td>
<code>dimension</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>The number of dimensions in the embeddings</td>
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
<td>If set to <code>false</code>, CoherenceEmbeddingStore will not be available even if configured</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.providers.coherence`](io.helidon.langchain4j.ProvidersConfig.md#coherence)

---

See the [manifest](manifest.md) for all available types.
