# io.helidon.langchain4j.providers.JlamaConfig

## Description

Merged configuration for langchain4j.providers.jlama

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
<td><code>auth-token</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#authToken(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, JlamaChatModel will not be available even if configured</td>
</tr>
<tr>
<td><code>max-tokens</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#maxTokens(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-cache-path</code></td>
<td><code>Path</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#modelCachePath(java.nio.file.Path)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Configure the model name</td>
</tr>
<tr>
<td><a id="pooling-type"></a><a href="com.github.tjake.jlama.model.functions.Generator.PoolingType.md"><code>pooling-type</code></a></td>
<td><code>PoolingType</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#poolingType(com.github.tjake.jlama.model.functions.Generator.PoolingType)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>quantize-model-at-runtime</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>temperature</code></td>
<td><code>Float</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#temperature(java.lang.Float)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>thread-count</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#threadCount(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>working-directory</code></td>
<td><code>Path</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#workingDirectory(java.nio.file.Path)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="working-quantized-type"></a><a href="com.github.tjake.jlama.safetensors.DType.md"><code>working-quantized-type</code></a></td>
<td><code>DType</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#workingQuantizedType(com.github.tjake.jlama.safetensors.DType)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Merged Types

- [io.helidon.integrations.langchain4j.providers.jlama.JlamaChatModelConfig](io.helidon.integrations.langchain4j.providers.jlama.JlamaChatModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.jlama.JlamaEmbeddingModelConfig](io.helidon.integrations.langchain4j.providers.jlama.JlamaEmbeddingModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.jlama.JlamaLanguageModelConfig](io.helidon.integrations.langchain4j.providers.jlama.JlamaLanguageModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.jlama.JlamaStreamingChatModelConfig](io.helidon.integrations.langchain4j.providers.jlama.JlamaStreamingChatModelConfig.md)

## Usages

- [`langchain4j.providers.jlama`](io.helidon.langchain4j.ProvidersConfig.md#jlama)

---

See the [manifest](manifest.md) for all available types.
