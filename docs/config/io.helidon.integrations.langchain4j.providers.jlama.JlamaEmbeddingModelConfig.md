# io.helidon.integrations.langchain4j.providers.jlama.JlamaEmbeddingModelConfig

## Description

Configuration for LangChain4j model JlamaEmbeddingModel

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
<code>thread-count</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#threadCount(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<a id="pooling-type"></a>
<a href="com.github.tjake.jlama.model.functions.Generator.PoolingType.md">
<code>pooling-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PoolingType">PoolingType</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#poolingType(com.github.tjake.jlama.model.functions.Generator.PoolingType)</code></td>
</tr>
<tr>
<td>
<code>model-cache-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#modelCachePath(java.nio.file.Path)</code></td>
</tr>
<tr>
<td>
<code>quantize-model-at-runtime</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)</code></td>
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
<td>Configure the model name</td>
</tr>
<tr>
<td>
<code>working-directory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#workingDirectory(java.nio.file.Path)</code></td>
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
<td>If set to <code>false</code>, JlamaEmbeddingModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>auth-token</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#authToken(java.lang.String)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
