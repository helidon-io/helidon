# io.helidon.integrations.langchain4j.ContentRetrieverConfig

## Description

Configuration for LangChain4j

ContentRetriever

components.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa8c41-display-name"></span> `display-name` | `VALUE` | `String` |   | Display name for this content retriever configuration |
| <span id="aed316-embedding-model"></span> `embedding-model` | `VALUE` | `String` |   | Explicit embedding model to use in the content retriever |
| <span id="aa55e0-embedding-store"></span> `embedding-store` | `VALUE` | `String` |   | Embedding store to use in the content retriever |
| <span id="a2a276-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, component will be disabled even if configured |
| <span id="a7fe57-max-results"></span> `max-results` | `VALUE` | `Integer` |   | Maximum number of results to return from the retriever |
| <span id="a4bf10-min-score"></span> `min-score` | `VALUE` | `Double` |   | Minimum score threshold for retrieved results |
| <span id="afd89c-type"></span> [`type`](../config/io_helidon_integrations_langchain4j_ContentRetrieverType.md) | `VALUE` | `i.h.i.l.ContentRetrieverType` | `EMBEDDING_STORE_CONTENT_RETRIEVER` | Type of content retriever to create |

See the [manifest](../config/manifest.md) for all available types.
