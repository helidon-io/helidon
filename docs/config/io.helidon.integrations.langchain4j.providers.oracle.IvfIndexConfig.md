# io.helidon.integrations.langchain4j.providers.oracle.IvfIndexConfig

## Description

<code>N/A</code>

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>degree-of-parallelism</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#degreeOfParallelism(int)</code></td>
</tr>
<tr>
<td>
<code>target-accuracy</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#targetAccuracy(int)</code></td>
</tr>
<tr>
<td>
<a id="create-option"></a>
<a href="dev.langchain4j.store.embedding.oracle.CreateOption.md">
<code>create-option</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CreateOption">CreateOption</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IndexBuilder#createOption(dev.langchain4j.store.embedding.oracle.CreateOption)</code></td>
</tr>
<tr>
<td>
<code>neighbor-partitions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#neighborPartitions(int)</code></td>
</tr>
<tr>
<td>
<code>min-vectors-per-partition</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#minVectorsPerPartition(int)</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IndexBuilder#name(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>sample-per-partition</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Generated from <code>dev.langchain4j.store.embedding.oracle.IVFIndexBuilder#samplePerPartition(int)</code></td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.providers.oracle.ivf-index`](io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig.md#ivf-index)

---

See the [manifest](manifest.md) for all available types.
