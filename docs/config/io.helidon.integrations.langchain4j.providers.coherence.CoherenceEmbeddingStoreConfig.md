# io.<wbr>helidon.<wbr>integrations.<wbr>langchain4j.<wbr>providers.<wbr>coherence.<wbr>Coherence<wbr>Embedding<wbr>Store<wbr>Config

## Description

Configuration for LangChain4j model CoherenceEmbeddingStore

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
<code>embedding-<wbr>model</code>
</td>
<td>
<code>Embedding<wbr>Model</code>
</td>
<td>
</td>
<td>The embedding model to use</td>
</tr>
<tr>
<td>
<code>normalize-<wbr>embeddings</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>store.<wbr>embedding.<wbr>coherence.<wbr>Coherence<wbr>Embedding<wbr>Store.<wbr>Builder#<wbr>normalize<wbr>Embeddings(<wbr>boolean)</code></td>
</tr>
<tr>
<td>
<code>session</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>store.<wbr>embedding.<wbr>coherence.<wbr>Coherence<wbr>Embedding<wbr>Store.<wbr>Builder#<wbr>session(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>store.<wbr>embedding.<wbr>coherence.<wbr>Coherence<wbr>Embedding<wbr>Store.<wbr>Builder#<wbr>name(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>index</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The index name to use</td>
</tr>
<tr>
<td>
<code>embedding-<wbr>model-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>embedding-<wbr>model</code></td>
</tr>
<tr>
<td>
<code>dimension</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>The number of dimensions in the embeddings</td>
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
<td>If set to <code>false</code>, CoherenceEmbeddingStore will not be available even if configured</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.langchain4j.ProvidersConfig.md#coherence"><code>langchain4j.<wbr>providers.<wbr>coherence</code></a>

---

See the [manifest](manifest.md) for all available types.
