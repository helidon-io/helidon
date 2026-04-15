# io.helidon.langchain4j.providers.OllamaConfig

## Description

Merged configuration for langchain4j.providers.ollama

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
<td><code>base-url</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#baseUrl(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>custom-headers</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#customHeaders(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-request-parameters</code></td>
<td><code>ChatRequestParameters</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-request-parameters-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;default-request-parameters&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, OllamaChatModel will not be available even if configured</td>
</tr>
<tr>
<td><code>http-client-builder</code></td>
<td><code>HttpClientBuilder</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;http-client-builder&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners</code></td>
<td><code>List&lt;ChatModelListener&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#listeners(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;listeners&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-requests</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logRequests(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-responses</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logResponses(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>logger</code></td>
<td><code>Logger</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logger(org.slf4j.Logger)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-retries</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder#maxRetries(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>min-p</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#minP(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>mirostat</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostat(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>mirostat-eta</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostatEta(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>mirostat-tau</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostatTau(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#modelName(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>num-ctx</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#numCtx(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>num-predict</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#numPredict(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>repeat-last-n</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#repeatLastN(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>repeat-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#repeatPenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>response-format</code></td>
<td><code>ResponseFormat</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>return-thinking</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#returnThinking(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>seed</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#seed(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>stop</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#stop(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="supported-capabilities"></a><a href="dev.langchain4j.model.chat.Capability.md"><code>supported-capabilities</code></a></td>
<td><code>List&lt;Capability&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#supportedCapabilities(java.util.Set)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>temperature</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#temperature(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>think</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#think(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#timeout(java.time.Duration)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-k</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#topK(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-p</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#topP(java.lang.Double)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Merged Types

- [io.helidon.integrations.langchain4j.providers.ollama.OllamaChatModelConfig](io.helidon.integrations.langchain4j.providers.ollama.OllamaChatModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.ollama.OllamaEmbeddingModelConfig](io.helidon.integrations.langchain4j.providers.ollama.OllamaEmbeddingModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.ollama.OllamaLanguageModelConfig](io.helidon.integrations.langchain4j.providers.ollama.OllamaLanguageModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.ollama.OllamaStreamingChatModelConfig](io.helidon.integrations.langchain4j.providers.ollama.OllamaStreamingChatModelConfig.md)

## Usages

- [`langchain4j.providers.ollama`](io.helidon.langchain4j.ProvidersConfig.md#ollama)

---

See the [manifest](manifest.md) for all available types.
