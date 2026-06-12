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

<!--@include ../../config/io.helidon.integrations.langchain4j.McpClientConfig.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../../config/io.helidon.integrations.langchain4j.McpClientConfig.md#configuration-options).
<!--/include-->


[helidon-mcp-gith]: https://github.com/helidon-io/helidon-mcp
[helidon-mcp-docu]: https://github.com/helidon-io/helidon-mcp#documentation
[integration-with]: ../../se/ai/langchain4j/langchain4j.md
