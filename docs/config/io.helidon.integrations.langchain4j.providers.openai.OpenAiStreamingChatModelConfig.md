# io.helidon.integrations.langchain4j.providers.openai.OpenAiStreamingChatModelConfig

## Description

Configuration for LangChain4j model OpenAiStreamingChatModel

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>custom-query-params</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#customQueryParams(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#metadata(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#seed(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logger(org.slf4j.Logger)</code></td>
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
<td>If set to <code>false</code>, OpenAiStreamingChatModel will not be available even if configured</td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#reasoningEffort(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#timeout(java.time.Duration)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#serviceTier(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logRequests(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#apiKey(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#temperature(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#maxTokens(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#modelName(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#projectId(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#returnThinking(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logitBias(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#maxCompletionTokens(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#baseUrl(java.lang.String)</code></td>
</tr>
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
<code>frequency-penalty</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#frequencyPenalty(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#listeners(java.util.List)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#customHeaders(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#store(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#strictJsonSchema(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#strictTools(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logResponses(java.lang.Boolean)</code></td>
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
<code>top-p</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#topP(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#customParameters(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#parallelToolCalls(java.lang.Boolean)</code></td>
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
<code>stop</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#stop(java.util.List)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#sendThinking(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#organizationId(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#user(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#presencePenalty(java.lang.Double)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
