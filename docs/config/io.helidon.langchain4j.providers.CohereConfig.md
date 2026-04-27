# io.helidon.langchain4j.providers.CohereConfig

## Description

Merged configuration for langchain4j.providers.cohere

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
<code>api-key</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#apiKey(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#baseUrl(java.lang.String)</code></td>
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
<td>If set to <code>false</code>, CohereEmbeddingModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>input-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#inputType(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#logRequests(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#logResponses(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#logger(org.slf4j.Logger)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereScoringModel.CohereScoringModelBuilder#maxRetries(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#maxSegmentsPerBatch(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#modelName(java.lang.String)</code></td>
</tr>
<tr>
<td>
<code>proxy</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Proxy</code>
</td>
<td class="cm-default-cell">
</td>
<td>Proxy to use</td>
</tr>
<tr>
<td>
<code>proxy-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>proxy</code></td>
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
<td>Generated from <code>dev.langchain4j.model.cohere.CohereEmbeddingModel.CohereEmbeddingModelBuilder#timeout(java.time.Duration)</code></td>
</tr>
</tbody>
</table>



## Merged Types

- [io.helidon.integrations.langchain4j.providers.cohere.CohereEmbeddingModelConfig](io.helidon.integrations.langchain4j.providers.cohere.CohereEmbeddingModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.cohere.CohereScoringModelConfig](io.helidon.integrations.langchain4j.providers.cohere.CohereScoringModelConfig.md)

## Usages

- [`langchain4j.providers.cohere`](io.helidon.langchain4j.ProvidersConfig.md#cohere)

---

See the [manifest](manifest.md) for all available types.
