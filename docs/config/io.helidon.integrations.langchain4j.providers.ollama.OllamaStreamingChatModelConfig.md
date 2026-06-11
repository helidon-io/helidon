# io.helidon.integrations.langchain4j.providers.ollama.OllamaStreamingChatModelConfig

## Description

Configuration for LangChain4j model OllamaStreamingChatModel

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
<code>mirostat-<wbr>tau</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>mirostat<wbr>Tau(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>http-<wbr>client-<wbr>builder</code>
</td>
<td>
<code>Http<wbr>Client<wbr>Builder</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>http<wbr>Client<wbr>Builder(<wbr>dev.<wbr>langchain4j.<wbr>http.<wbr>client.<wbr>Http<wbr>Client<wbr>Builder)</code></td>
</tr>
<tr>
<td>
<code>min-<wbr>p</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>minP(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>mirostat</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>mirostat(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>seed(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
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
<code>logger</code>
</td>
<td>
<code>Logger</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>logger(<wbr>org.<wbr>slf4j.<wbr>Logger)</code></td>
</tr>
<tr>
<td>
<code>num-<wbr>ctx</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>numCtx(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
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
<td>If set to <code>false</code>, OllamaStreamingChatModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>timeout(<wbr>java.<wbr>time.<wbr>Duration)</code></td>
</tr>
<tr>
<td>
<code>log-<wbr>requests</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>logRequests(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
</tr>
<tr>
<td>
<code>repeat-<wbr>last-<wbr>n</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>repeat<wbr>Last<wbr>N(java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<a id="supported-capabilities"></a>
<a href="dev.langchain4j.model.chat.Capability.md">
<code>supported-<wbr>capabilities</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Capability&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>supported<wbr>Capabilities(<wbr>java.<wbr>util.<wbr>Set)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>temperature(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>model<wbr>Name(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>num-<wbr>predict</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>numPredict(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>return-<wbr>thinking</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>return<wbr>Thinking(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
</tr>
<tr>
<td>
<code>base-<wbr>url</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>base<wbr>Url(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>think</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>think(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>default<wbr>Request<wbr>Parameters(<wbr>dev.<wbr>langchain4j.<wbr>model.<wbr>chat.<wbr>request.<wbr>Chat<wbr>Request<wbr>Parameters)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>listeners(<wbr>java.<wbr>util.<wbr>List)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>topK(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>custom-<wbr>headers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>custom<wbr>Headers(<wbr>java.<wbr>util.<wbr>Map)</code></td>
</tr>
<tr>
<td>
<code>log-<wbr>responses</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>logResponses(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
</tr>
<tr>
<td>
<code>response-<wbr>format</code>
</td>
<td>
<code>Response<wbr>Format</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>response<wbr>Format(<wbr>dev.<wbr>langchain4j.<wbr>model.<wbr>chat.<wbr>request.<wbr>Response<wbr>Format)</code></td>
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
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>topP(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>http-<wbr>client-<wbr>builder-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>http-<wbr>client-<wbr>builder</code></td>
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
<code>stop</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>stop(<wbr>java.<wbr>util.<wbr>List)</code></td>
</tr>
<tr>
<td>
<code>repeat-<wbr>penalty</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>repeat<wbr>Penalty(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>mirostat-<wbr>eta</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>ollama.<wbr>Ollama<wbr>Base<wbr>Chat<wbr>Model.<wbr>Builder#<wbr>mirostat<wbr>Eta(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
