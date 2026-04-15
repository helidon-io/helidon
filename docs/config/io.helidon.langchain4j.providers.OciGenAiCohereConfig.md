# io.helidon.langchain4j.providers.OciGenAiCohereConfig

## Description

Merged configuration for langchain4j.providers.oci-gen-ai-cohere

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
<td><code>auth-provider</code></td>
<td><code>BasicAuthenticationDetailsProvider</code></td>
<td></td>
<td>Authentication provider is by default used from default Service bean found in Service Registry</td>
</tr>
<tr>
<td><code>auth-provider-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;auth-provider&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="citation-quality"></a><a href="com.oracle.bmc.generativeaiinference.model.CohereChatRequest.CitationQuality.md"><code>citation-quality</code></a></td>
<td><code>CitationQuality</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#citationQuality(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.CitationQuality)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>compartment-id</code></td>
<td><code>String</code></td>
<td></td>
<td>OCI Compartment OCID</td>
</tr>
<tr>
<td><code>default-request-parameters</code></td>
<td><code>ChatRequestParameters</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-request-parameters-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;default-request-parameters&lt;/code&gt;</td>
</tr>
<tr>
<td><code>documents</code></td>
<td><code>List&lt;Object&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#documents(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, OciGenAiCohereChatModel will not be available even if configured</td>
</tr>
<tr>
<td><code>frequency-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#frequencyPenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>gen-ai-client</code></td>
<td><code>GenerativeAiInferenceClient</code></td>
<td></td>
<td>Custom http client builder</td>
</tr>
<tr>
<td><code>gen-ai-client-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;gen-ai-client&lt;/code&gt;</td>
</tr>
<tr>
<td><code>is-raw-prompting</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isRawPrompting(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>is-search-queries-only</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isSearchQueriesOnly(java.lang.Boolean)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners</code></td>
<td><code>List&lt;ChatModelListener&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#listeners(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>listeners-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;listeners&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-input-tokens</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#maxInputTokens(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-tokens</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#maxTokens(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>model-name</code></td>
<td><code>String</code></td>
<td></td>
<td>OCI LLM Model name or OCID</td>
</tr>
<tr>
<td><code>preamble-override</code></td>
<td><code>String</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#preambleOverride(java.lang.String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>presence-penalty</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#presencePenalty(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="prompt-truncation"></a><a href="com.oracle.bmc.generativeaiinference.model.CohereChatRequest.PromptTruncation.md"><code>prompt-truncation</code></a></td>
<td><code>PromptTruncation</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#promptTruncation(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.PromptTruncation)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>region</code></td>
<td><code>OciFactoryMethods</code></td>
<td></td>
<td>Region is by default set to the current OCI region detected by OCI SDK</td>
</tr>
<tr>
<td><code>region-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;region&lt;/code&gt;</td>
</tr>
<tr>
<td><code>seed</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#seed(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>serving-type</code></td>
<td><code>OciFactoryMethods</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#servingType(com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>stop</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#stop(java.util.List)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>temperature</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#temperature(java.lang.Double)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-k</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topK(java.lang.Integer)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>top-p</code></td>
<td><code>Double</code></td>
<td></td>
<td>Generated from &lt;code&gt;dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topP(java.lang.Double)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Merged Types

- [io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereChatModelConfig](io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereChatModelConfig.md)
- [io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereStreamingChatModelConfig](io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereStreamingChatModelConfig.md)

## Usages

- [`langchain4j.providers.oci-gen-ai-cohere`](io.helidon.langchain4j.ProvidersConfig.md#oci-gen-ai-cohere)

---

See the [manifest](manifest.md) for all available types.
