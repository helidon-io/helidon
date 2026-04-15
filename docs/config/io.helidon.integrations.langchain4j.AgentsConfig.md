# io.helidon.integrations.langchain4j.AgentsConfig

## Description

Configuration for a single LangChain4j agent

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
<td><code>chat-model</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the &lt;code&gt;dev.langchain4j.model.chat.ChatModel&lt;/code&gt; service to use for this agent</td>
</tr>
<tr>
<td><code>mcp-clients</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Names of &lt;code&gt;dev.langchain4j.mcp.client.McpClient&lt;/code&gt; services to use for MCP-backed tools</td>
</tr>
<tr>
<td><code>input-guardrails</code></td>
<td><code>List&lt;Class&gt;</code></td>
<td></td>
<td>Input guardrail classes to apply to the agent</td>
</tr>
<tr>
<td><code>content-retriever</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the &lt;code&gt;dev.langchain4j.rag.content.retriever.ContentRetriever&lt;/code&gt; service to use for this agent</td>
</tr>
<tr>
<td><code>description</code></td>
<td><code>String</code></td>
<td></td>
<td>Description of the agent</td>
</tr>
<tr>
<td><code>tools</code></td>
<td><code>List&lt;Class&gt;</code></td>
<td></td>
<td>Tool service classes to register with the agent</td>
</tr>
<tr>
<td><code>chat-memory</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the &lt;code&gt;dev.langchain4j.memory.ChatMemory&lt;/code&gt; service to use for this agent</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;false&lt;/code&gt;, agent will not be available even if configured</td>
</tr>
<tr>
<td><code>execute-tools-concurrently</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>If true, the agent&#x27;s tools can be invoked in a concurrent manner</td>
</tr>
<tr>
<td><code>chat-memory-provider</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the &lt;code&gt;dev.langchain4j.memory.chat.ChatMemoryProvider&lt;/code&gt; service to use for this agent</td>
</tr>
<tr>
<td><code>tool-provider</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the &lt;code&gt;dev.langchain4j.service.tool.ToolProvider&lt;/code&gt; service to use for this agent</td>
</tr>
<tr>
<td><code>async</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent&#x27;s result</td>
</tr>
<tr>
<td><code>output-key</code></td>
<td><code>String</code></td>
<td></td>
<td>Key of the output variable that will be used to store the result of the agent&#x27;s invocation</td>
</tr>
<tr>
<td><code>retrieval-augmentor</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the &lt;code&gt;dev.langchain4j.rag.RetrievalAugmentor&lt;/code&gt; service to use for this agent</td>
</tr>
<tr>
<td><code>output-guardrails</code></td>
<td><code>List&lt;Class&gt;</code></td>
<td></td>
<td>Output guardrail classes to apply to the agent</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td></td>
<td>Agent identifier used to label the agent in workflows and/or agent registries</td>
</tr>
</tbody>
</table>


## Usages

- [`langchain4j.agents`](io.helidon.Langchain4jConfig.md#agents)

---

See the [manifest](manifest.md) for all available types.
