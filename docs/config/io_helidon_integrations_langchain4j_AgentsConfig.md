# io.helidon.integrations.langchain4j.AgentsConfig

## Description

Configuration for a single LangChain4j agent.

## Usages

- [`langchain4j.agents`](../config/config_reference.md#ab76ee-langchain4j-agents)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9a1a1-async"></span> `async` | `VALUE` | `Boolean` |   | If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent's result |
| <span id="a6a4d2-chat-memory"></span> `chat-memory` | `VALUE` | `String` |   | Name of the `dev.langchain4j.memory.ChatMemory` service to use for this agent |
| <span id="aa5535-chat-memory-provider"></span> `chat-memory-provider` | `VALUE` | `String` |   | Name of the `dev.langchain4j.memory.chat.ChatMemoryProvider` service to use for this agent |
| <span id="a97dca-chat-model"></span> `chat-model` | `VALUE` | `String` |   | Name of the `dev.langchain4j.model.chat.ChatModel` service to use for this agent |
| <span id="a52e5f-content-retriever"></span> `content-retriever` | `VALUE` | `String` |   | Name of the `dev.langchain4j.rag.content.retriever.ContentRetriever` service to use for this agent |
| <span id="a6c0df-description"></span> `description` | `VALUE` | `String` |   | Description of the agent |
| <span id="a6218c-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, agent will not be available even if configured |
| <span id="abb93a-execute-tools-concurrently"></span> `execute-tools-concurrently` | `VALUE` | `Boolean` |   | If true, the agent's tools can be invoked in a concurrent manner |
| <span id="ac4f33-input-guardrails"></span> `input-guardrails` | `LIST` | `Class` |   | Input guardrail classes to apply to the agent |
| <span id="a9ed0e-mcp-clients"></span> `mcp-clients` | `LIST` | `String` |   | Names of `dev.langchain4j.mcp.client.McpClient` services to use for MCP-backed tools |
| <span id="a548f4-name"></span> `name` | `VALUE` | `String` |   | Agent identifier used to label the agent in workflows and/or agent registries |
| <span id="a043a3-output-guardrails"></span> `output-guardrails` | `LIST` | `Class` |   | Output guardrail classes to apply to the agent |
| <span id="a0b130-output-key"></span> `output-key` | `VALUE` | `String` |   | Key of the output variable that will be used to store the result of the agent's invocation |
| <span id="a2258f-retrieval-augmentor"></span> `retrieval-augmentor` | `VALUE` | `String` |   | Name of the `dev.langchain4j.rag.RetrievalAugmentor` service to use for this agent |
| <span id="a6e1b1-tool-provider"></span> `tool-provider` | `VALUE` | `String` |   | Name of the `dev.langchain4j.service.tool.ToolProvider` service to use for this agent |
| <span id="a4040a-tools"></span> `tools` | `LIST` | `Class` |   | Tool service classes to register with the agent |

See the [manifest](../config/manifest.md) for all available types.
