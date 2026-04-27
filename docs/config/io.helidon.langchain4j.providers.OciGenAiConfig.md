# io.helidon.langchain4j.providers.OciGenAiConfig

## Description

Merged configuration for langchain4j.providers.oci-gen-ai

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
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to <code>false</code>, OciGenAiChatModel will not be available even if configured</td>
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
<code>log-probs</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseGenericChatModel.Builder#logProbs(java.lang.Integer)</code></td>
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
<td>Modifies the likelihood of specified tokens that appear in the completion</td>
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
<code>num-generations</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Generated from <code>dev.langchain4j.community.model.oracle.oci.genai.BaseGenericChatModel.Builder#numGenerations(java.lang.Integer)</code></td>
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
</tbody>
</table>



## Merged Types

- [io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiChatModelConfig](io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiChatModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiStreamingChatModelConfig](io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiStreamingChatModelConfig.md)

## Usages

- [`langchain4j.providers.oci-gen-ai`](io.helidon.langchain4j.ProvidersConfig.md#oci-gen-ai)

---

See the [manifest](manifest.md) for all available types.
