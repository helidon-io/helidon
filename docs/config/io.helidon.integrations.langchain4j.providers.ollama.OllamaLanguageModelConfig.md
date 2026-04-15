# io.helidon.integrations.langchain4j.providers.ollama.OllamaLanguageModelConfig

## Description

Configuration for LangChain4j model OllamaLanguageModel

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
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#baseUrl(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder</code></td>
<td><code>HttpClientBuilder</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>seed</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#seed(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-k</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#topK(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>custom-headers</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#customHeaders(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-retries</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#maxRetries(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>num-ctx</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#numCtx(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-responses</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#logResponses(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>response-format</code></td>
<td><code>ResponseFormat</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, OllamaLanguageModel will not be available even if configured</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#timeout(java.time.Duration)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-p</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#topP(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-requests</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#logRequests(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;http-client-builder&lt;/code&gt;</td>
</tr>
<tr>
<td><code>stop</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#stop(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>repeat-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#repeatPenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>temperature</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#temperature(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#modelName(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>num-predict</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#numPredict(java.lang.Integer)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
