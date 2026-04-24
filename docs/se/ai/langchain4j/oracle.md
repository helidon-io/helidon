# Oracle Embedding Store

## Overview

This module adds support for the Oracle embedding store.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4j core dependencies](langchain4j.md#maven-coordinates), you must add the following:

```xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-oracle</artifactId>
</dependency>
```

## OracleEmbeddingStore

To automatically create and add `OracleEmbeddingStore` to the service registry add the following lines to `application.yaml`:

```yaml
# Oracle UCP datasource
data:
  sources:
    sql:
      - name: "foo-bar-data-source" 
        provider.ucp:
          username: "vector"
          password: "vector"
          url: "jdbc:oracle:thin:@localhost:1521/freepdb1"
          connection-factory-class-name: oracle.jdbc.pool.OracleDataSource

langchain4j:
  providers:
    oracle:
      # Configuration of a datasource default for all oracle embedding stores
      data-source: "foo-bar-data-source"

  embedding-stores:
    foo-bar-oracle-db-embedding-store:
      provider: oracle
      embedding-table:
        name: "foo-bar-embeddings"
```

- Configured with `io.helidon.data.sql.datasource:helidon-data-sql-datasource-ucp` see [Helidon Data Repository](../../../se/data.md) for more info

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `data-source` | string | The name of Helidon service containing a data source for connecting to the Oracle embedding store. If not present, the default unnamed service is used. |
| `embedding-table` | [OracleEmbeddingTable](#oracleembeddingtable) | Root configuration key for `OracleEmbeddingTable` configuration. Contains properties of the embedding table associated with the Oracle embedding store. |
| `enabled` | boolean | If set to `true`, Oracle embedding store will be enabled. |
| `exact-search` | boolean | Specifies whether exact matching is used in searches. |
| `vector-index-create-option` | string | The vector index creation option, which defines behavior when creating the vector index. Options are `CREATE_NONE` (default), `CREATE_IF_NOT_EXISTS`, `CREATE_OR_REPLACE`. |

### OracleEmbeddingTable

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `create-option` | string | Defines the behavior when creating the embedding table. Options are: `CREATE_NONE` (default), `CREATE_IF_NOT_EXISTS`, `CREATE_OR_REPLACE`. |
| `embedding-column` | string | Name of the embedding column in the embedding table. Default is "embedding". |
| `id-column` | string | Name of the ID column in the embedding table. Default is "id". |
| `metadata-column` | string | Name of the metadata column in the embedding table. default is "metadata". |
| `name` | string | Required. Name of the embedding table. |
| `text-column` | string | Name of the text column in the embedding table. Default is "text". |

## Additional Information

- [LangChain4j Integration](langchain4j.md)
- [langChain4J Oracle Embedding Store Documentation](https://docs.langchain4j.dev/integrations/embedding-stores/oracle)
