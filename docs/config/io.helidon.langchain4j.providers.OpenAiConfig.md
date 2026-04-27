# io.helidon.langchain4j.providers.OpenAiConfig

## Description

Merged configuration for langchain4j.providers.open-ai

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
<code>accumulate-tool-call-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#accumulateToolCallId(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#apiKey(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#baseUrl(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>custom-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customHeaders(java.util.Map)</code></td>
</tr>
<tr>
<td>
<code>custom-parameters</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, Object&gt;">Map&lt;String, Object&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customParameters(java.util.Map)</code></td>
</tr>
<tr>
<td>
<code>custom-query-params</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customQueryParams(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)</code></td>
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
<code>dimensions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#dimensions(java.lang.Integer)</code></td>
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
<td>If set to <code>false</code>, OpenAiChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>encoding-format</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#encodingFormat(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#frequencyPenalty(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)</code></td>
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
<code>listeners</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ChatModelListener&gt;">List&lt;ChatModelListener&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#listeners(java.util.List)</code></td>
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
<code>log-requests</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logRequests(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logResponses(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logger(org.slf4j.Logger)</code></td>
</tr>
<tr>
<td>
<code>logit-bias</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, Integer&gt;">Map&lt;String, Integer&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logitBias(java.util.Map)</code></td>
</tr>
<tr>
<td>
<code>max-completion-tokens</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxCompletionTokens(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxRetries(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>max-segments-per-batch</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#maxSegmentsPerBatch(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxTokens(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>metadata</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#metadata(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#modelName(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>organization-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#organizationId(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>parallel-tool-calls</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#parallelToolCalls(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#presencePenalty(java.lang.Double)</code></td>
</tr>
<tr>
<td>
<code>project-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#projectId(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>quality</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#quality(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>reasoning-effort</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#reasoningEffort(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>response-format</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enable a "JSON mode" in the model configuration</td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#returnThinking(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#seed(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#sendThinking(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>service-tier</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#serviceTier(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#size(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>stop</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#stop(java.util.List)</code></td>
</tr>
<tr>
<td>
<code>store</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#store(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>strict-json-schema</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#strictJsonSchema(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>strict-tools</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#strictTools(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>style</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#style(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#supportedCapabilities(java.util.Set)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#temperature(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#timeout(java.time.Duration)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#topP(java.lang.Double)</code></td>
</tr>
<tr>
<td>
<code>user</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#user(java.lang.String)</code></td>
</tr>
</tbody>
</table>



## Merged Types

- [io.helidon.integrations.langchain4j.providers.openai.OpenAiChatModelConfig](io.helidon.integrations.langchain4j.providers.openai.OpenAiChatModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.openai.OpenAiEmbeddingModelConfig](io.helidon.integrations.langchain4j.providers.openai.OpenAiEmbeddingModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.openai.OpenAiImageModelConfig](io.helidon.integrations.langchain4j.providers.openai.OpenAiImageModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.openai.OpenAiLanguageModelConfig](io.helidon.integrations.langchain4j.providers.openai.OpenAiLanguageModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.openai.OpenAiModerationModelConfig](io.helidon.integrations.langchain4j.providers.openai.OpenAiModerationModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.openai.OpenAiStreamingChatModelConfig](io.helidon.integrations.langchain4j.providers.openai.OpenAiStreamingChatModelConfig.md)

## Usages

- [`langchain4j.providers.open-ai`](io.helidon.langchain4j.ProvidersConfig.md#open-ai)

---

See the [manifest](manifest.md) for all available types.
