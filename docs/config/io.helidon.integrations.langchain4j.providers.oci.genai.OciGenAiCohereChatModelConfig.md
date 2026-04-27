# io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereChatModelConfig

## Description

Configuration for LangChain4j model OciGenAiCohereChatModel

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
<code>gen-ai-client</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="GenerativeAiInferenceClient">GenerativeAiInferenceClient</code>
</td>
<td class="cm-default-cell">
</td>
<td>Custom http client builder</td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#seed(java.lang.Integer)</code></td>
</tr>
<tr>
<td>
<code>documents</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Object&gt;">List&lt;Object&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#documents(java.util.List)</code></td>
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
<code>auth-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="BasicAuthenticationDetailsProvider">BasicAuthenticationDetailsProvider</code>
</td>
<td class="cm-default-cell">
</td>
<td>Authentication provider is by default used from default Service bean found in Service Registry</td>
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
<td>If set to <code>false</code>, OciGenAiCohereChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>max-input-tokens</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#maxInputTokens(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#temperature(java.lang.Double)</code></td>
</tr>
<tr>
<td>
<code>is-raw-prompting</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isRawPrompting(java.lang.Boolean)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#maxTokens(java.lang.Integer)</code></td>
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
<td>OCI LLM Model name or OCID</td>
</tr>
<tr>
<td>
<code>auth-provider-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>auth-provider</code></td>
</tr>
<tr>
<td>
<code>region-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>region</code></td>
</tr>
<tr>
<td>
<code>serving-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OciFactoryMethods">OciFactoryMethods</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#servingType(com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#frequencyPenalty(java.lang.Double)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)</code></td>
</tr>
<tr>
<td>
<a id="prompt-truncation"></a>
<a href="com.oracle.bmc.generativeaiinference.model.CohereChatRequest.PromptTruncation.md">
<code>prompt-truncation</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PromptTruncation">PromptTruncation</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#promptTruncation(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.PromptTruncation)</code></td>
</tr>
<tr>
<td>
<a id="citation-quality"></a>
<a href="com.oracle.bmc.generativeaiinference.model.CohereChatRequest.CitationQuality.md">
<code>citation-quality</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CitationQuality">CitationQuality</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#citationQuality(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.CitationQuality)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#listeners(java.util.List)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topK(java.lang.Integer)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topP(java.lang.Double)</code></td>
</tr>
<tr>
<td>
<code>is-search-queries-only</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isSearchQueriesOnly(java.lang.Boolean)</code></td>
</tr>
<tr>
<td>
<code>preamble-override</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#preambleOverride(java.lang.String)</code></td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#stop(java.util.List)</code></td>
</tr>
<tr>
<td>
<code>compartment-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>OCI Compartment OCID</td>
</tr>
<tr>
<td>
<code>gen-ai-client-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>gen-ai-client</code></td>
</tr>
<tr>
<td>
<code>region</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OciFactoryMethods">OciFactoryMethods</code>
</td>
<td class="cm-default-cell">
</td>
<td>Region is by default set to the current OCI region detected by OCI SDK</td>
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
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#presencePenalty(java.lang.Double)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
