# io.helidon.integrations.langchain4j.providers.openai.OpenAiLanguageModelConfig

## Description

Configuration for LangChain4j model OpenAiLanguageModel

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
<code>base-url</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#baseUrl(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#customQueryParams(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#customHeaders(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#logger(org.slf4j.Logger)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#maxRetries(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#logResponses(java.lang.Boolean)</code></td>
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
<td>If set to <code>false</code>, OpenAiLanguageModel will not be available even if configured</td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#timeout(java.time.Duration)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#logRequests(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#apiKey(java.lang.String)</code></td>
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
<code>temperature</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#temperature(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#modelName(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#organizationId(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#projectId(java.lang.String)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
