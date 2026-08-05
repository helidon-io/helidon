# io.<wbr>helidon.<wbr>integrations.<wbr>langchain4j.<wbr>providers.<wbr>lc4jinprocess.<wbr>InProcess<wbr>Embedding<wbr>Model<wbr>Config

## Description

Configuration blueprint for LangChain4j in-process models

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
<code>path-<wbr>to-tokenizer</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>The path to the tokenizer file (e.g., "/path/to/tokenizer.json")</td>
</tr>
<tr>
<td>
<code>path-<wbr>to-model</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>The path to the modelPath file (e.g., "/path/to/model.onnx")</td>
</tr>
<tr>
<td>
<code>executor</code>
</td>
<td>
<code>Thread<wbr>Pool<wbr>Config</code>
</td>
<td>
</td>
<td>Executor configuration used by the embedding model</td>
</tr>
<tr>
<td>
<a id="pooling-mode"></a>
<a href="dev.langchain4j.model.embedding.onnx.PoolingMode.md">
<code>pooling-<wbr>mode</code>
</a>
</td>
<td>
<code>Pooling<wbr>Mode</code>
</td>
<td>
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
<td>
<code>In<wbr>Process<wbr>Model<wbr>Type</code>
</td>
<td>
</td>
<td>Which in-process ONNX model variant should be used</td>
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
<td>Whether the embedding model is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.langchain4j.ProvidersConfig.md#lc4j-in-process"><code>langchain4j.<wbr>providers.<wbr>lc4j-<wbr>in-process</code></a>

---

See the [manifest](manifest.md) for all available types.
