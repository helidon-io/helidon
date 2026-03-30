# io.helidon.integrations.langchain4j.providers.coherence.CoherenceEmbeddingStoreConfig

## Description

Configuration for LangChain4j model CoherenceEmbeddingStore.

## Usages

- [`langchain4j.providers.coherence`](../config/config_reference.md#afc71b-langchain4j-providers-coherence)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a446a7-dimension"></span> `dimension` | `VALUE` | `Integer` |   | The number of dimensions in the embeddings |
| <span id="af963b-embedding-model"></span> `embedding-model` | `VALUE` | `d.l.m.e.EmbeddingModel` |   | The embedding model to use |
| <span id="a3b77b-embedding-model-discover-services"></span> `embedding-model-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `embedding-model` |
| <span id="a4e3d3-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, CoherenceEmbeddingStore will not be available even if configured |
| <span id="a09481-index"></span> `index` | `VALUE` | `String` |   | The index name to use |
| <span id="a8c86f-name"></span> `name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#name(java.lang.String)` |
| <span id="adaa86-normalize-embeddings"></span> `normalize-embeddings` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#normalizeEmbeddings(boolean)` |
| <span id="a4852b-session"></span> `session` | `VALUE` | `String` |   | Generated from `dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore.Builder#session(java.lang.String)` |

See the [manifest](../config/manifest.md) for all available types.
