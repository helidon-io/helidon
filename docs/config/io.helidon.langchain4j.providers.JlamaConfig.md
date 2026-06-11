# io.helidon.langchain4j.providers.JlamaConfig

## Description

Merged configuration for langchain4j.providers.jlama

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
<code>auth-<wbr>token</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>auth<wbr>Token(<wbr>java.<wbr>lang.<wbr>String)</code></td>
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
<td>If set to <code>false</code>, JlamaChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>max-<wbr>tokens</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>maxTokens(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>model-<wbr>cache-<wbr>path</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>model<wbr>Cache<wbr>Path(<wbr>java.<wbr>nio.<wbr>file.<wbr>Path)</code></td>
</tr>
<tr>
<td>
<code>model-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Configure the model name</td>
</tr>
<tr>
<td>
<a id="pooling-type"></a>
<a href="com.github.tjake.jlama.model.functions.Generator.PoolingType.md">
<code>pooling-<wbr>type</code>
</a>
</td>
<td>
<code>Pooling<wbr>Type</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Embedding<wbr>Model.<wbr>Jlama<wbr>Embedding<wbr>Model<wbr>Builder#<wbr>pooling<wbr>Type(<wbr>com.<wbr>github.<wbr>tjake.<wbr>jlama.<wbr>model.<wbr>functions.<wbr>Generator.<wbr>Pooling<wbr>Type)</code></td>
</tr>
<tr>
<td>
<code>quantize-<wbr>model-<wbr>at-runtime</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>quantize<wbr>Model<wbr>AtRuntime(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
</tr>
<tr>
<td>
<code>temperature</code>
</td>
<td>
<code>Float</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>temperature(<wbr>java.<wbr>lang.<wbr>Float)</code></td>
</tr>
<tr>
<td>
<code>thread-<wbr>count</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>thread<wbr>Count(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>working-<wbr>directory</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>working<wbr>Directory(<wbr>java.<wbr>nio.<wbr>file.<wbr>Path)</code></td>
</tr>
<tr>
<td>
<a id="working-quantized-type"></a>
<a href="com.github.tjake.jlama.safetensors.DType.md">
<code>working-<wbr>quantized-<wbr>type</code>
</a>
</td>
<td>
<code>D<wbr>Type</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>jlama.<wbr>Jlama<wbr>Chat<wbr>Model.<wbr>Jlama<wbr>Chat<wbr>Model<wbr>Builder#<wbr>working<wbr>Quantized<wbr>Type(<wbr>com.<wbr>github.<wbr>tjake.<wbr>jlama.<wbr>safetensors.<wbr>DType)</code></td>
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
