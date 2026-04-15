# io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiStreamingChatModelConfig

## Description

Configuration for LangChain4j model GoogleAiGeminiStreamingChatModel

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
<td><code>media-resolution-per-part-enabled</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#mediaResolutionPerPartEnabled(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>retrieve-google-maps-widget-token</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#retrieveGoogleMapsWidgetToken(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder</code></td>
<td><code>HttpClientBuilder</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-output-tokens</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#maxOutputTokens(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="media-resolution"></a><a href="dev.langchain4j.model.googleai.GeminiMediaResolutionLevel.md"><code>media-resolution</code></a></td>
<td><code>GeminiMediaResolutionLevel</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#mediaResolution(dev.langchain4j.model.googleai.GeminiMediaResolutionLevel)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>seed</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#seed(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-request-parameters-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;default-request-parameters&lt;/code&gt;</td>
</tr>
<tr>
<td><code>logger</code></td>
<td><code>Logger</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logger(org.slf4j.Logger)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>tool-config</code></td>
<td><code>GeminiFunctionCallingConfig</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#toolConfig(dev.langchain4j.model.googleai.GeminiFunctionCallingConfig)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, GoogleAiGeminiStreamingChatModel will not be available even if configured</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#timeout(java.time.Duration)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>logprobs</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logprobs(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-requests</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logRequests(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>api-key</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#apiKey(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>temperature</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#temperature(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#modelName(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>stop-sequences</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#stopSequences(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>return-thinking</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#returnThinking(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>base-url</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#baseUrl(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>allow-google-search</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowGoogleSearch(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>frequency-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#frequencyPenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-request-parameters</code></td>
<td><code>ChatRequestParameters</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>allow-url-context</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowUrlContext(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners</code></td>
<td><code>List&lt;ChatModelListener&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#listeners(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>response-logprobs</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#responseLogprobs(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-k</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#topK(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>allow-code-execution</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowCodeExecution(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-requests-and-responses</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logRequestsAndResponses(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>allow-google-maps</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowGoogleMaps(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-responses</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logResponses(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>response-format</code></td>
<td><code>ResponseFormat</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-p</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#topP(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;http-client-builder&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;listeners&lt;/code&gt;</td>
</tr>
<tr>
<td><code>include-code-execution-output</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#includeCodeExecutionOutput(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>safety-settings</code></td>
<td><code>List&lt;GeminiSafetySetting&gt;</code></td>
<td></td>
<td>Safety setting, affecting the safety-blocking behavior</td>
</tr>
<tr>
<td><code>thinking-config</code></td>
<td><code>GeminiThinkingConfig</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#thinkingConfig(dev.langchain4j.model.googleai.GeminiThinkingConfig)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>send-thinking</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#sendThinking(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enable-enhanced-civic-answers</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#enableEnhancedCivicAnswers(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>presence-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#presencePenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
