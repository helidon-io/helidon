# io.helidon.integrations.langchain4j.providers.openai.OpenAiImageModelConfig

## Description

Configuration for LangChain4j model OpenAiImageModel

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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#baseUrl(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#customQueryParams(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#customHeaders(java.util.Map)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#logger(org.slf4j.Logger)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#maxRetries(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#logResponses(java.lang.Boolean)</code></td>
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
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to <code>false</code>, OpenAiImageModel will not be available even if configured</td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#timeout(java.time.Duration)</code></td>
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
<code>log-requests</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#logRequests(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#apiKey(java.lang.String)</code></td>
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
<code>model-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#modelName(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#organizationId(java.lang.String)</code></td>
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
<code>project-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#projectId(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.openai.OpenAiImageModel.OpenAiImageModelBuilder#user(java.lang.String)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
