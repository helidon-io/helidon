# io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig

## Description

Configuration for LangChain4j model OracleEmbeddingStore.

## Usages

- [`langchain4j.providers.oracle`](../config/config_reference.md#afcb77-langchain4j-providers-oracle)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a8714b-data-source"></span> `data-source` | `VALUE` | `j.s.DataSource` |   | Configures a data source that connects to an Oracle Database |
| <span id="a23836-data-source-discover-services"></span> `data-source-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `data-source` |
| <span id="aa5553-embedding-table"></span> [`embedding-table`](../config/io_helidon_integrations_langchain4j_providers_oracle_EmbeddingTableConfig.md) | `VALUE` | `i.h.i.l.p.o.EmbeddingTableConfig` |   | Configures a table used to store embeddings, text, and metadata |
| <span id="ae22a2-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OracleEmbeddingStore will not be available even if configured |
| <span id="aff862-exact-search"></span> `exact-search` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore.Builder#exactSearch(boolean)` |
| <span id="a59988-ivf-index"></span> [`ivf-index`](../config/io_helidon_integrations_langchain4j_providers_oracle_IvfIndexConfig.md) | `LIST` | `i.h.i.l.p.o.IvfIndexConfig` |   | IVFIndex allows configuring an Inverted File Flat (IVF) index on the embedding column of the `EmbeddingTable` |
| <span id="a6124b-json-index"></span> [`json-index`](../config/io_helidon_integrations_langchain4j_providers_oracle_JsonIndexConfig.md) | `LIST` | `i.h.i.l.p.o.JsonIndexConfig` |   | JSONIndex allows configuring a function-based index on one or several keys of the metadata column of the `EmbeddingTable` |
| <span id="ae8cef-vector-index"></span> [`vector-index`](../config/dev_langchain4j_store_embedding_oracle_CreateOption.md) | `VALUE` | `d.l.s.e.o.CreateOption` |   | The vector index creation option, which defines behavior when creating the vector index |

See the [manifest](../config/manifest.md) for all available types.
