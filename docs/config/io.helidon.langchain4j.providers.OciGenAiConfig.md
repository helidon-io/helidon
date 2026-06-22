# io.<wbr>helidon.<wbr>langchain4j.<wbr>providers.<wbr>OciGen<wbr>AiConfig

## Description

Merged configuration for langchain4j.providers.oci-gen-ai

## Configuration options


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
<code>auth-<wbr>provider</code>
</td>
<td>
<code>Basic<wbr>Authentication<wbr>Details<wbr>Provider</code>
</td>
<td>
</td>
<td>Authentication provider is by default used from default Service bean found in Service Registry</td>
</tr>
<tr>
<td>
<code>auth-<wbr>provider-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>auth-<wbr>provider</code></td>
</tr>
<tr>
<td>
<code>compartment-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>OCI Compartment OCID</td>
</tr>
<tr>
<td>
<code>default-<wbr>request-<wbr>parameters</code>
</td>
<td>
<code>Chat<wbr>Request<wbr>Parameters</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>default<wbr>Request<wbr>Parameters(<wbr>dev.<wbr>langchain4j.<wbr>model.<wbr>chat.<wbr>request.<wbr>Chat<wbr>Request<wbr>Parameters)</code></td>
</tr>
<tr>
<td>
<code>default-<wbr>request-<wbr>parameters-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>default-<wbr>request-<wbr>parameters</code></td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If set to <code>false</code>, OciGenAiChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>frequency-<wbr>penalty</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>frequency<wbr>Penalty(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>gen-<wbr>ai-client</code>
</td>
<td>
<code>Generative<wbr>AiInference<wbr>Client</code>
</td>
<td>
</td>
<td>Custom http client builder</td>
</tr>
<tr>
<td>
<code>gen-<wbr>ai-client-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>gen-<wbr>ai-client</code></td>
</tr>
<tr>
<td>
<code>listeners</code>
</td>
<td>
<code>List&lt;<wbr>Chat<wbr>Model<wbr>Listener&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>listeners(<wbr>java.<wbr>util.<wbr>List)</code></td>
</tr>
<tr>
<td>
<code>listeners-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>listeners</code></td>
</tr>
<tr>
<td>
<code>log-<wbr>probs</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Generic<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>logProbs(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>logit-<wbr>bias</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Integer&gt;</code>
</td>
<td>
</td>
<td>Modifies the likelihood of specified tokens that appear in the completion</td>
</tr>
<tr>
<td>
<code>max-<wbr>tokens</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>maxTokens(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>model-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>OCI LLM Model name or OCID</td>
</tr>
<tr>
<td>
<code>num-<wbr>generations</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Generic<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>numGenerations(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>presence-<wbr>penalty</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>presence<wbr>Penalty(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>region</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Region is by default set to the current OCI region detected by OCI SDK</td>
</tr>
<tr>
<td>
<code>region-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>region</code></td>
</tr>
<tr>
<td>
<code>seed</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>seed(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>serving-<wbr>type</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>serving<wbr>Type(<wbr>com.<wbr>oracle.<wbr>bmc.<wbr>generativeaiinference.<wbr>model.<wbr>Serving<wbr>Mode.<wbr>Serving<wbr>Type)</code></td>
</tr>
<tr>
<td>
<code>stop</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>stop(<wbr>java.<wbr>util.<wbr>List)</code></td>
</tr>
<tr>
<td>
<code>temperature</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>temperature(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>top-<wbr>k</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>topK(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>top-<wbr>p</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>community.<wbr>model.<wbr>oracle.<wbr>oci.<wbr>genai.<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>topP(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
</tbody>
</table>



## Merged Types

- [io.<wbr>helidon.<wbr>integrations.<wbr>langchain4j.<wbr>providers.<wbr>oci.<wbr>genai.<wbr>OciGen<wbr>AiChat<wbr>Model<wbr>Config](io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiChatModelConfig.md)
- [io.<wbr>helidon.<wbr>integrations.<wbr>langchain4j.<wbr>providers.<wbr>oci.<wbr>genai.<wbr>OciGen<wbr>AiStreaming<wbr>Chat<wbr>Model<wbr>Config](io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiStreamingChatModelConfig.md)

## Usages

- <a href="io.helidon.langchain4j.ProvidersConfig.md#oci-gen-ai"><code>langchain4j.<wbr>providers.<wbr>oci-<wbr>gen-<wbr>ai</code></a>

---

See the [manifest](manifest.md) for all available types.
