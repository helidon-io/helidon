# io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig

## Description

Configuration blueprint for LangChain4j in-process models

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
<code>path-to-tokenizer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>The path to the tokenizer file (e.g., "/path/to/tokenizer.json")</td>
</tr>
<tr>
<td>
<code>path-to-model</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>The path to the modelPath file (e.g., "/path/to/model.onnx")</td>
</tr>
<tr>
<td>
<code>executor</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ThreadPoolConfig">ThreadPoolConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Executor configuration used by the embedding model</td>
</tr>
<tr>
<td>
<a id="pooling-mode"></a>
<a href="dev.langchain4j.model.embedding.onnx.PoolingMode.md">
<code>pooling-mode</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PoolingMode">PoolingMode</code>
</td>
<td class="cm-default-cell">
</td>
<td>The pooling model to use</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessModelType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="InProcessModelType">InProcessModelType</code>
</td>
<td class="cm-default-cell">
</td>
<td>Which in-process ONNX model variant should be used</td>
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
<td>Whether the embedding model is enabled</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.providers.lc4j-in-process`](io.helidon.langchain4j.ProvidersConfig.md#lc4j-in-process)

---

See the [manifest](manifest.md) for all available types.
