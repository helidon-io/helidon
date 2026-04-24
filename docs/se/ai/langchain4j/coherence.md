# Coherence Embedding Store

## Overview

This module adds support for the Coherence embedding store.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4J core dependencies](langchain4j.md#maven-coordinates), you must add the following:

```xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-coherence</artifactId>
</dependency>
```

## Components

### CoherenceEmbeddingStore

To automatically create and add `CoherenceEmbeddingStore` to the service registry add the following lines to `application.yaml`:

```yaml
langchain4j:
  providers:
    coherence:
      session: "session"

  embedding-stores:
    coherence-embedding-store:
      provider: coherence
      name: "namedMap"
      normalize-embeddings: false
      index: "hnsw"
      dimension: 768
      embedding-model: beanName
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

| Key | Type | Description |
|----|----|----|
| `embedding-model` | string | Allows to configure embedding model by specifying named bean using `embeddingModel.service-registry.named: beanName`. |
| `dimension` | integer | The number of dimensions in the embeddings that will be stored in vector store. |
| `enabled` | boolean | If set to `true`, Coherence embedding store will be enabled. |
| `index` | string | Specifies vector index type use to create a vector index used to query embeddings. Only `hnsw` is supported. |
| `name` | string | Specifies name of the Coherence `com.tangosol.net.NamedMap` use to store embeddings. |
| `normalize-embeddings` | boolean | A flag that when true, forces normalization of embeddings on adding and searching. |
| `session` | string | The name of the `com.tangosol.net.Session` use to obtain the `com.tangosol.net.NamedMap` as specified with `name`. |

## Additional Information

- [LangChain4J Integration](langchain4j.md)
- [langChain4J Coherence Embedding Store Documentation](https://docs.langchain4j.dev/integrations/embedding-stores/coherence)
