# MCP

## Model Context Protocol (MCP)

The [Model Context Protocol (MCP)](https://modelcontextprotocol.io) is an open protocol designed to connect AI models with external tools, resources, and data sources in a standardized way. An MCP server exposes resources, prompts, and tools that AI clients can discover and invoke dynamically, enabling more powerful and context-aware applications.

## MCP Server

Helidon provides support for building Model Context Protocol (MCP) servers through a dedicated extension. The MCP Server feature is not part of the core Helidon Framework – it is delivered as a separate project hosted in the [helidon-mcp GitHub repository](https://github.com/helidon-io/helidon-mcp).

### Helidon MCP Server Extension

The Helidon MCP Server extension allows you to build and run MCP servers with Helidon.

Key points:

- Separate repository: [helidon-mcp](https://github.com/helidon-io/helidon-mcp)
- Independent lifecycle: Requires Helidon but has its own versioning and release cadence
- Dedicated documentation: Full usage guides, configuration details, and examples are provided directly in the [helidon-mcp documentation](https://github.com/helidon-io/helidon-mcp#documentation)

To get started:

1.  Visit the [helidon-mcp GitHub repository](https://github.com/helidon-io/helidon-mcp).
2.  Follow the setup and usage instructions in the repository’s documentation.
3.  Explore how to expose your Helidon resources as MCP tools, prompts, and data sources.

## MCP Client

Helidon includes support for an MCP client through its [integration with LangChain4j](../../se/ai/langchain4j/langchain4j.md). With this integration, you can set up the MCP client using Helidon configuration and plug it directly into your LangChain4j AI Services and Agents.

In LangChain4j, an MCP (Model Context Protocol) client acts as a bridge between the language model and external services or resources that follow the MCP standard. Instead of directly embedding custom logic into the application, the MCP client enables the model to discover, connect to, and interact with external tools and data providers in a standardized way.

To add MCP Clients to your AI Service, use `@Ai.McpClients` annotation to reference configured clients:

``` java
@Ai.Service
@Ai.ChatModel("expensive-model")
@Ai.McpClients(value = {"foo-mcp-server", "bar-mcp-server"})
public interface ChatAiService {
    String chat(String question);
}
```

If you want to have your MCP clients created from the configuration, it should be placed under the `langchain4j.mcp-clients`.

``` yaml
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

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="aa2f58-client-name"></span> `client-name` | `VALUE` | `String` | Sets the name that the client will use to identify itself to the MCP server in the initialization message |
| <span id="a13f78-client-version"></span> `client-version` | `VALUE` | `String` | Sets the version string that the client will use to identify itself to the MCP server in the initialization message |
| <span id="a10402-initialization-timeout"></span> `initialization-timeout` | `VALUE` | `Duration` | Sets the timeout for initializing the client |
| <span id="ad969f-key"></span> `key` | `VALUE` | `String` | Sets a unique identifier for the client |
| <span id="afeabc-log-requests"></span> `log-requests` | `VALUE` | `Boolean` | Whether to log request traffic |
| <span id="a50414-log-responses"></span> `log-responses` | `VALUE` | `Boolean` | Whether to log response traffic |
| <span id="af6558-ping-timeout"></span> `ping-timeout` | `VALUE` | `Duration` | The timeout to apply when waiting for a ping response |
| <span id="a0460e-prompts-timeout"></span> `prompts-timeout` | `VALUE` | `Duration` | The timeout for prompt-related operations (listing prompts as well as rendering the contents of a prompt) |
| <span id="ad11cb-protocol-version"></span> `protocol-version` | `VALUE` | `String` | Sets the protocol version that the client will advertise in the initialization message |
| <span id="ad5cc0-reconnect-interval"></span> `reconnect-interval` | `VALUE` | `Duration` | The delay before attempting to reconnect after a failed connection |
| <span id="a3257a-resources-timeout"></span> `resources-timeout` | `VALUE` | `Duration` | Sets the timeout for resource-related operations (listing resources as well as reading the contents of a resource) |
| <span id="accdc6-tls"></span> [`tls`](../../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` | TLS configuration for the MCP server connection |
| <span id="a93c51-tool-execution-timeout"></span> `tool-execution-timeout` | `VALUE` | `Duration` | Sets the timeout for tool execution |
| <span id="a35999-tool-execution-timeout-error-message"></span> `tool-execution-timeout-error-message` | `VALUE` | `String` | The error message to return when a tool execution times out |
| <span id="ae642c-uri"></span> `uri` | `VALUE` | `URI` | The URL of the MCP server |

#### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="aa72c1-sse-uri"></span> `sse-uri` | `VALUE` | `URI` | The initial URI where to connect to the server and request an SSE channel |
