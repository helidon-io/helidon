# io.helidon.langchain4j.providers.JlamaConfig

## Description

Merged configuration for langchain4j.providers.jlama

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
<code>auth-token</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#authToken(java.lang.String)</code></td>
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
<td>If set to <code>false</code>, JlamaChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>max-tokens</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#maxTokens(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>model-cache-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#modelCachePath(java.nio.file.Path)</code></td>
</tr>
<tr>
<td>
<code>model-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure the model name</td>
</tr>
<tr>
<td>
<a id="pooling-type"></a>
<a href="com.github.tjake.jlama.model.functions.Generator.PoolingType.md">
<code>pooling-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PoolingType">PoolingType</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#poolingType(com.github.tjake.jlama.model.functions.Generator.PoolingType)</code></td>
</tr>
<tr>
<td>
<code>quantize-model-at-runtime</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>temperature</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Float</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#temperature(java.lang.Float)</code></td>
</tr>
<tr>
<td>
<code>thread-count</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#threadCount(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>working-directory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#workingDirectory(java.nio.file.Path)</code></td>
</tr>
<tr>
<td>
<a id="working-quantized-type"></a>
<a href="com.github.tjake.jlama.safetensors.DType.md">
<code>working-quantized-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">DType</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#workingQuantizedType(com.github.tjake.jlama.safetensors.DType)</code></td>
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
