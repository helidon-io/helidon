# io.helidon.integrations.langchain4j.McpClientConfig

## Description

Configuration for LangChain4j MCP (Model Context Protocol) clients.

## Usages

## Configuration options

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
| <span id="accdc6-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` | TLS configuration for the MCP server connection |
| <span id="a93c51-tool-execution-timeout"></span> `tool-execution-timeout` | `VALUE` | `Duration` | Sets the timeout for tool execution |
| <span id="a35999-tool-execution-timeout-error-message"></span> `tool-execution-timeout-error-message` | `VALUE` | `String` | The error message to return when a tool execution times out |
| <span id="ae642c-uri"></span> `uri` | `VALUE` | `URI` | The URL of the MCP server |

### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="aa72c1-sse-uri"></span> `sse-uri` | `VALUE` | `URI` | The initial URI where to connect to the server and request an SSE channel |

See the [manifest](../config/manifest.md) for all available types.
