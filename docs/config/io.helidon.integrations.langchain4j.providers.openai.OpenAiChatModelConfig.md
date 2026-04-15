# io.helidon.integrations.langchain4j.providers.openai.OpenAiChatModelConfig

## Description

Configuration for LangChain4j model OpenAiChatModel

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
<td><code>custom-query-params</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customQueryParams(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>metadata</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#metadata(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder</code></td>
<td><code>HttpClientBuilder</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>seed</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#seed(java.lang.Integer)&lt;/code&gt;</td>
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
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logger(org.slf4j.Logger)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-retries</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxRetries(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, OpenAiChatModel will not be available even if configured</td>
</tr>
<tr>
<td><code>reasoning-effort</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#reasoningEffort(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#timeout(java.time.Duration)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>service-tier</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#serviceTier(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-requests</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logRequests(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>api-key</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#apiKey(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="supported-capabilities"></a><a href="dev.langchain4j.model.chat.Capability.md"><code>supported-capabilities</code></a></td>
<td><code>List&lt;Capability&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#supportedCapabilities(java.util.Set)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>temperature</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#temperature(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-tokens</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxTokens(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#modelName(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>project-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#projectId(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>return-thinking</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#returnThinking(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>logit-bias</code></td>
<td><code>Map&lt;String, Integer&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logitBias(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-completion-tokens</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxCompletionTokens(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>base-url</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#baseUrl(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>frequency-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#frequencyPenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-request-parameters</code></td>
<td><code>ChatRequestParameters</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners</code></td>
<td><code>List&lt;ChatModelListener&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#listeners(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>custom-headers</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customHeaders(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>store</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#store(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>strict-json-schema</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#strictJsonSchema(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>strict-tools</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#strictTools(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-responses</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logResponses(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>response-format</code></td>
<td><code>String</code></td>
<td></td>
<td>Enable a &quot;JSON mode&quot; in the model configuration</td>
</tr>
<tr>
<td><code>top-p</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#topP(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>custom-parameters</code></td>
<td><code>Map&lt;String, Object&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customParameters(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>parallel-tool-calls</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#parallelToolCalls(java.lang.Boolean)&lt;/code&gt;</td>
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
<td><code>stop</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#stop(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>send-thinking</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#sendThinking(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>organization-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#organizationId(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>user</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#user(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>presence-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#presencePenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
