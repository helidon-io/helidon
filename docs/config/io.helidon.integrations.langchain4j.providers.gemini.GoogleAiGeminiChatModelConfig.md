# io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiChatModelConfig

## Description

Configuration for LangChain4j model GoogleAiGeminiChatModel

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
<code>media-resolution-per-part-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#mediaResolutionPerPartEnabled(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>retrieve-google-maps-widget-token</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#retrieveGoogleMapsWidgetToken(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>http-client-builder</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="HttpClientBuilder">HttpClientBuilder</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)</code></td>
</tr>
<tr>
<td>
<code>max-output-tokens</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#maxOutputTokens(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<a id="media-resolution"></a>
<a href="dev.langchain4j.model.googleai.GeminiMediaResolutionLevel.md">
<code>media-resolution</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="GeminiMediaResolutionLevel">GeminiMediaResolutionLevel</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#mediaResolution(dev.langchain4j.model.googleai.GeminiMediaResolutionLevel)</code></td>
</tr>
<tr>
<td>
<code>seed</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#seed(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>default-request-parameters-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>default-request-parameters</code></td>
</tr>
<tr>
<td>
<code>logger</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Logger</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logger(org.slf4j.Logger)</code></td>
</tr>
<tr>
<td>
<code>max-retries</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder#maxRetries(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>tool-config</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="GeminiFunctionCallingConfig">GeminiFunctionCallingConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#toolConfig(dev.langchain4j.model.googleai.GeminiFunctionCallingConfig)</code></td>
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
<td>If set to <code>false</code>, GoogleAiGeminiChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#timeout(java.time.Duration)</code></td>
</tr>
<tr>
<td>
<code>logprobs</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logprobs(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>log-requests</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logRequests(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>api-key</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#apiKey(java.lang.String)</code></td>
</tr>
<tr>
<td>
<a id="supported-capabilities"></a>
<a href="dev.langchain4j.model.chat.Capability.md">
<code>supported-capabilities</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Capability&gt;">List&lt;Capability&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder#supportedCapabilities(java.util.Set)</code></td>
</tr>
<tr>
<td>
<code>temperature</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#temperature(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#modelName(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>stop-sequences</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#stopSequences(java.util.List)</code></td>
</tr>
<tr>
<td>
<code>return-thinking</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#returnThinking(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>base-url</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#baseUrl(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>allow-google-search</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowGoogleSearch(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>frequency-penalty</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#frequencyPenalty(java.lang.Double)</code></td>
</tr>
<tr>
<td>
<code>default-request-parameters</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ChatRequestParameters">ChatRequestParameters</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)</code></td>
</tr>
<tr>
<td>
<code>allow-url-context</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowUrlContext(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>listeners</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ChatModelListener&gt;">List&lt;ChatModelListener&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#listeners(java.util.List)</code></td>
</tr>
<tr>
<td>
<code>response-logprobs</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#responseLogprobs(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>top-k</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#topK(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>allow-code-execution</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowCodeExecution(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>log-requests-and-responses</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logRequestsAndResponses(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>allow-google-maps</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowGoogleMaps(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>log-responses</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logResponses(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>response-format</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ResponseFormat">ResponseFormat</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)</code></td>
</tr>
<tr>
<td>
<code>top-p</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#topP(java.lang.Double)</code></td>
</tr>
<tr>
<td>
<code>http-client-builder-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>http-client-builder</code></td>
</tr>
<tr>
<td>
<code>listeners-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>listeners</code></td>
</tr>
<tr>
<td>
<code>include-code-execution-output</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#includeCodeExecutionOutput(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>safety-settings</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;GeminiSafetySetting&gt;">List&lt;GeminiSafetySetting&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Safety setting, affecting the safety-blocking behavior</td>
</tr>
<tr>
<td>
<code>thinking-config</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="GeminiThinkingConfig">GeminiThinkingConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#thinkingConfig(dev.langchain4j.model.googleai.GeminiThinkingConfig)</code></td>
</tr>
<tr>
<td>
<code>send-thinking</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#sendThinking(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>enable-enhanced-civic-answers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#enableEnhancedCivicAnswers(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>presence-penalty</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#presencePenalty(java.lang.Double)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
