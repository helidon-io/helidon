# io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig

## Description

Configuration blueprint for LangChain4j in-process models

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
<td><code>path-to-tokenizer</code></td>
<td><code>Path</code></td>
<td></td>
<td>The path to the tokenizer file (e.g., &quot;/path/to/tokenizer.json&quot;)</td>
</tr>
<tr>
<td><code>path-to-model</code></td>
<td><code>Path</code></td>
<td></td>
<td>The path to the modelPath file (e.g., &quot;/path/to/model.onnx&quot;)</td>
</tr>
<tr>
<td><code>executor</code></td>
<td><code>ThreadPoolConfig</code></td>
<td></td>
<td>Executor configuration used by the embedding model</td>
</tr>
<tr>
<td><a id="pooling-mode"></a><a href="dev.langchain4j.model.embedding.onnx.PoolingMode.md"><code>pooling-mode</code></a></td>
<td><code>PoolingMode</code></td>
<td></td>
<td>The pooling model to use</td>
</tr>
<tr>
<td><a id="type"></a><a href="io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessModelType.md"><code>type</code></a></td>
<td><code>InProcessModelType</code></td>
<td></td>
<td>Which in-process ONNX model variant should be used</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the embedding model is enabled</td>
</tr>
</tbody>
</table>


## Usages

- [`langchain4j.providers.lc4j-in-process`](io.helidon.langchain4j.ProvidersConfig.md#lc4j-in-process)

---

See the [manifest](manifest.md) for all available types.
