# io.helidon.integrations.langchain4j.providers.openai.OpenAiEmbeddingModelConfig

## Description

Configuration for LangChain4j model OpenAiEmbeddingModel

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
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#baseUrl(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>custom-query-params</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#customQueryParams(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder</code></td>
<td><code>HttpClientBuilder</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>custom-headers</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#customHeaders(java.util.Map)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>logger</code></td>
<td><code>Logger</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#logger(org.slf4j.Logger)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-retries</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#maxRetries(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-responses</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#logResponses(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, OpenAiEmbeddingModel will not be available even if configured</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#timeout(java.time.Duration)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>encoding-format</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#encodingFormat(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-requests</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#logRequests(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>api-key</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#apiKey(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>http-client-builder-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;http-client-builder&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#modelName(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>organization-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#organizationId(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-segments-per-batch</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#maxSegmentsPerBatch(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>project-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#projectId(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>user</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#user(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>dimensions</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder#dimensions(java.lang.Integer)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
