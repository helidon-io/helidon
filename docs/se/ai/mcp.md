# MCP

## Model Context Protocol (MCP)

The [Model Context Protocol (MCP)](https://modelcontextprotocol.io) is an open protocol designed to connect AI models with external tools, resources, and data sources in a standardized way. An MCP server exposes resources, prompts, and tools that AI clients can discover and invoke dynamically, enabling more powerful and context-aware applications.

## MCP Server

Helidon provides support for building Model Context Protocol (MCP) servers through a dedicated extension. The MCP Server feature is not part of the core Helidon Framework – it is delivered as a separate project hosted in the [helidon-mcp GitHub repository][helidon-mcp-gith].

### Helidon MCP Server Extension

The Helidon MCP Server extension allows you to build and run MCP servers with Helidon.

Key points:

- Separate repository: [helidon-mcp][helidon-mcp-gith]
- Independent lifecycle: Requires Helidon but has its own versioning and release cadence
- Dedicated documentation: Full usage guides, configuration details, and examples are provided directly in the [helidon-mcp documentation][helidon-mcp-docu]

To get started:

1.  Visit the [helidon-mcp GitHub repository][helidon-mcp-gith].
2.  Follow the setup and usage instructions in the repository’s documentation.
3.  Explore how to expose your Helidon resources as MCP tools, prompts, and data sources.

## MCP Client

Helidon includes support for an MCP client through its [integration with LangChain4j][integration-with]. With this integration, you can set up the MCP client using Helidon configuration and plug it directly into your LangChain4j AI Services and Agents.

In LangChain4j, an MCP (Model Context Protocol) client acts as a bridge between the language model and external services or resources that follow the MCP standard. Instead of directly embedding custom logic into the application, the MCP client enables the model to discover, connect to, and interact with external tools and data providers in a standardized way.

To add MCP Clients to your AI Service, use `@Ai.McpClients` annotation to reference configured clients:

```java
@Ai.Service
@Ai.ChatModel("expensive-model")
@Ai.McpClients(value = {"foo-mcp-server", "bar-mcp-server"})
public interface ChatAiService {
    String chat(String question);
}
```

If you want to have your MCP clients created from the configuration, it should be placed under the `langchain4j.mcp-clients`.

```yaml [application.yaml]
langchain4j:
  providers:
    open-ai:
      api-key: "${OPEN_AI_API_TOKEN}"

  models:
    expensive-model:
      provider: open-ai
      model-name: "openai.gpt-oss-120b"

  mcp-clients:
    foo-mcp-server:
      uri: http://foo-mcp-server
      initialization-timeout: PT15M
    bar-mcp-server:
      uri: http://bar-mcp-server
      tool-execution-timeout: PT10S
```

These are all the MCP Client configuration options currently supported:

### Configuration options

<!--@include ../../config/io.helidon.integrations.langchain4j.McpClientConfig.md#configuration-options offset=1 -->
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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>ping-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>The timeout to apply when waiting for a ping response</td>
</tr>
<tr>
<td>
<code>client-version</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Sets the version string that the client will use to identify itself to the MCP server in the initialization message</td>
</tr>
<tr>
<td>
<code>protocol-version</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Sets the protocol version that the client will advertise in the initialization message</td>
</tr>
<tr>
<td>
<code>log-responses</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether to log response traffic</td>
</tr>
<tr>
<td>
<code>reconnect-interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>The delay before attempting to reconnect after a failed connection</td>
</tr>
<tr>
<td>
<code>uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td>The URL of the MCP server</td>
</tr>
<tr>
<td>
<code>prompts-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>The timeout for prompt-related operations (listing prompts as well as rendering the contents of a prompt)</td>
</tr>
<tr>
<td>
<code>client-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Sets the name that the client will use to identify itself to the MCP server in the initialization message</td>
</tr>
<tr>
<td>
<code>resources-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Sets the timeout for resource-related operations (listing resources as well as reading the contents of a resource)</td>
</tr>
<tr>
<td>
<code>log-requests</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether to log request traffic</td>
</tr>
<tr>
<td>
<a id="tls"></a>
<a href="io.helidon.common.tls.Tls.md">
<code>tls</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Tls</code>
</td>
<td>TLS configuration for the MCP server connection</td>
</tr>
<tr>
<td>
<code>tool-execution-timeout-error-message</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The error message to return when a tool execution times out</td>
</tr>
<tr>
<td>
<code>key</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Sets a unique identifier for the client</td>
</tr>
<tr>
<td>
<code>tool-execution-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Sets the timeout for tool execution</td>
</tr>
<tr>
<td>
<code>initialization-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Sets the timeout for initializing the client</td>
</tr>
</tbody>
</table>


### Deprecated Options


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>sse-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td>The initial URI where to connect to the server and request an SSE channel</td>
</tr>
</tbody>
</table>
<!--/include-->


[helidon-mcp-gith]: https://github.com/helidon-io/helidon-mcp
[helidon-mcp-docu]: https://github.com/helidon-io/helidon-mcp#documentation
[integration-with]: ../../se/ai/langchain4j/langchain4j.md
