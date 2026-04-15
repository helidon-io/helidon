# io.helidon.integrations.langchain4j.providers.oracle.IvfIndexConfig

## Description

&lt;code&gt;N/A&lt;/code&gt;

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>degree-of-parallelism</code></td>
<td><code>Integer</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#degreeOfParallelism(int)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>target-accuracy</code></td>
<td><code>Integer</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#targetAccuracy(int)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="create-option"></a><a href="dev.langchain4j.store.embedding.oracle.CreateOption.md"><code>create-option</code></a></td>
<td><code>CreateOption</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IndexBuilder#createOption(dev.langchain4j.store.embedding.oracle.CreateOption)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>neighbor-partitions</code></td>
<td><code>Integer</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#neighborPartitions(int)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>min-vectors-per-partition</code></td>
<td><code>Integer</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#minVectorsPerPartition(int)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IndexBuilder#name(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>sample-per-partition</code></td>
<td><code>Integer</code></td>
<td>Generated from &lt;code&gt;dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#samplePerPartition(int)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`langchain4j.providers.oracle.ivf-index`](io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig.md#ivf-index)

---

See the [manifest](manifest.md) for all available types.
