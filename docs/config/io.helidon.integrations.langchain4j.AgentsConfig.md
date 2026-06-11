# io.helidon.integrations.langchain4j.AgentsConfig

## Description

Configuration for a single LangChain4j agent

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
<code>chat-<wbr>model</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the <code>dev.<wbr>langchain4j.<wbr>model.<wbr>chat.<wbr>Chat<wbr>Model</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>mcp-<wbr>clients</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Names of <code>dev.<wbr>langchain4j.<wbr>mcp.<wbr>client.<wbr>McpClient</code> services to use for MCP-backed tools</td>
</tr>
<tr>
<td>
<code>input-<wbr>guardrails</code>
</td>
<td>
<code>List&lt;<wbr>Class&gt;</code>
</td>
<td>
</td>
<td>Input guardrail classes to apply to the agent</td>
</tr>
<tr>
<td>
<code>content-<wbr>retriever</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the <code>dev.<wbr>langchain4j.<wbr>rag.<wbr>content.<wbr>retriever.<wbr>Content<wbr>Retriever</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Description of the agent</td>
</tr>
<tr>
<td>
<code>tools</code>
</td>
<td>
<code>List&lt;<wbr>Class&gt;</code>
</td>
<td>
</td>
<td>Tool service classes to register with the agent</td>
</tr>
<tr>
<td>
<code>chat-<wbr>memory</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the <code>dev.<wbr>langchain4j.<wbr>memory.<wbr>Chat<wbr>Memory</code> service to use for this agent</td>
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
<td>If set to <code>false</code>, agent will not be available even if configured</td>
</tr>
<tr>
<td>
<code>execute-<wbr>tools-<wbr>concurrently</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>If true, the agent's tools can be invoked in a concurrent manner</td>
</tr>
<tr>
<td>
<code>chat-<wbr>memory-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the <code>dev.<wbr>langchain4j.<wbr>memory.<wbr>chat.<wbr>Chat<wbr>Memory<wbr>Provider</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>tool-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the <code>dev.<wbr>langchain4j.<wbr>service.<wbr>tool.<wbr>Tool<wbr>Provider</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>async</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent's result</td>
</tr>
<tr>
<td>
<code>output-<wbr>key</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Key of the output variable that will be used to store the result of the agent's invocation</td>
</tr>
<tr>
<td>
<code>retrieval-<wbr>augmentor</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the <code>dev.<wbr>langchain4j.<wbr>rag.<wbr>Retrieval<wbr>Augmentor</code> service to use for this agent</td>
</tr>
<tr>
<td>
<code>output-<wbr>guardrails</code>
</td>
<td>
<code>List&lt;<wbr>Class&gt;</code>
</td>
<td>
</td>
<td>Output guardrail classes to apply to the agent</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Agent identifier used to label the agent in workflows and/or agent registries</td>
</tr>
</tbody>
</table>



## Usages

- [`langchain4j.agents`](io.helidon.Langchain4jConfig.md#agents)

---

See the [manifest](manifest.md) for all available types.
