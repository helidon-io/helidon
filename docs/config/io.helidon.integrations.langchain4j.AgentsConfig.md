# io.helidon.integrations.langchain4j.AgentsConfig

## Description

Configuration for a single LangChain4j agent

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
<code>chat-model</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the <code>dev.langchain4j.model.chat.ChatModel</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>mcp-clients</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Names of <code>dev.langchain4j.mcp.client.McpClient</code> services to use for MCP-backed tools</td>
</tr>
<tr>
<td>
<code>input-guardrails</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Class&gt;">List&lt;Class&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Input guardrail classes to apply to the agent</td>
</tr>
<tr>
<td>
<code>content-retriever</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the <code>dev.langchain4j.rag.content.retriever.ContentRetriever</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Description of the agent</td>
</tr>
<tr>
<td>
<code>tools</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Class&gt;">List&lt;Class&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Tool service classes to register with the agent</td>
</tr>
<tr>
<td>
<code>chat-memory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the <code>dev.langchain4j.memory.ChatMemory</code> service to use for this agent</td>
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
<td>If set to <code>false</code>, agent will not be available even if configured</td>
</tr>
<tr>
<td>
<code>execute-tools-concurrently</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>If true, the agent's tools can be invoked in a concurrent manner</td>
</tr>
<tr>
<td>
<code>chat-memory-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the <code>dev.langchain4j.memory.chat.ChatMemoryProvider</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>tool-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the <code>dev.langchain4j.service.tool.ToolProvider</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>async</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent's result</td>
</tr>
<tr>
<td>
<code>output-key</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Key of the output variable that will be used to store the result of the agent's invocation</td>
</tr>
<tr>
<td>
<code>retrieval-augmentor</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the <code>dev.langchain4j.rag.RetrievalAugmentor</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>output-guardrails</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Class&gt;">List&lt;Class&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Output guardrail classes to apply to the agent</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Agent identifier used to label the agent in workflows and/or agent registries</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.agents`](io.helidon.Langchain4jConfig.md#agents)

---

See the [manifest](manifest.md) for all available types.
