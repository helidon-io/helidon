# io.helidon.integrations.langchain4j.McpClientConfig

## Description

Configuration for LangChain4j MCP (Model Context Protocol) clients

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>ping-timeout</code></td>
<td><code>Duration</code></td>
<td>The timeout to apply when waiting for a ping response</td>
</tr>
<tr>
<td><code>client-version</code></td>
<td><code>String</code></td>
<td>Sets the version string that the client will use to identify itself to the MCP server in the initialization message</td>
</tr>
<tr>
<td><code>protocol-version</code></td>
<td><code>String</code></td>
<td>Sets the protocol version that the client will advertise in the initialization message</td>
</tr>
<tr>
<td><code>log-responses</code></td>
<td><code>Boolean</code></td>
<td>Whether to log response traffic</td>
</tr>
<tr>
<td><code>reconnect-interval</code></td>
<td><code>Duration</code></td>
<td>The delay before attempting to reconnect after a failed connection</td>
</tr>
<tr>
<td><code>uri</code></td>
<td><code>URI</code></td>
<td>The URL of the MCP server</td>
</tr>
<tr>
<td><code>prompts-timeout</code></td>
<td><code>Duration</code></td>
<td>The timeout for prompt-related operations (listing prompts as well as rendering the contents of a prompt)</td>
</tr>
<tr>
<td><code>client-name</code></td>
<td><code>String</code></td>
<td>Sets the name that the client will use to identify itself to the MCP server in the initialization message</td>
</tr>
<tr>
<td><code>resources-timeout</code></td>
<td><code>Duration</code></td>
<td>Sets the timeout for resource-related operations (listing resources as well as reading the contents of a resource)</td>
</tr>
<tr>
<td><code>log-requests</code></td>
<td><code>Boolean</code></td>
<td>Whether to log request traffic</td>
</tr>
<tr>
<td><a id="tls"></a><a href="io.helidon.common.tls.Tls.md"><code>tls</code></a></td>
<td><code>Tls</code></td>
<td>TLS configuration for the MCP server connection</td>
</tr>
<tr>
<td><code>tool-execution-timeout-error-message</code></td>
<td><code>String</code></td>
<td>The error message to return when a tool execution times out</td>
</tr>
<tr>
<td><code>key</code></td>
<td><code>String</code></td>
<td>Sets a unique identifier for the client</td>
</tr>
<tr>
<td><code>tool-execution-timeout</code></td>
<td><code>Duration</code></td>
<td>Sets the timeout for tool execution</td>
</tr>
<tr>
<td><code>initialization-timeout</code></td>
<td><code>Duration</code></td>
<td>Sets the timeout for initializing the client</td>
</tr>
</tbody>
</table>

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>sse-uri</code></td>
<td><code>URI</code></td>
<td>The initial URI where to connect to the server and request an SSE channel</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
