# In-Process Embedding Models

## Contents

- [Maven Coordinates](#maven-coordinates)
- [In-Process Embedding Model](#in-process-embedding-model)
- [Additional Information](#additional-information)

## Maven Coordinates

In addition to the [LangChain4j integration core dependencies](langchain4j.md#maven-coordinates), add:

``` xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-lc4j-in-process</artifactId>
</dependency>
```

Depending on configured model `type`, add model artifact dependencies as follows:

- For `type: all_minilm_l6_v2`, add:

  ``` xml
  <dependency>
      <groupId>dev.langchain4j</groupId>
      <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
  </dependency>
  ```

- For `type: all_minilm_l6_v2_q`, add:

  ``` xml
  <dependency>
      <groupId>dev.langchain4j</groupId>
      <artifactId>langchain4j-embeddings-all-minilm-l6-v2-q</artifactId>
  </dependency>
  ```

- For `type: custom`, no additional model-specific dependency is required. Configure `path-to-model`, `path-to-tokenizer`, and `pooling-mode`.

## In-Process Embedding Model

Provider key: `lc4j-in-process`.

LangChain4j in-process embedding models run ONNX embedding inference locally in your process (see [LangChain4j documentation](https://docs.langchain4j.dev/integrations/embedding-models/in-process/)).

In Helidon, a named entry under `langchain4j.models` with `provider: lc4j-in-process` is created as a named singleton declarative service bean in the Helidon service registry. This is how `foo-bar-embedding-model` becomes available for content retrievers and direct injection.

``` yaml
langchain4j:

  models:
    foo-bar-embedding-model: 
      provider: lc4j-in-process
      type: all_minilm_l6_v2_q 

  content-retrievers:
    foo-bar-content-retriever:
      provider: lc4j-content-retriever
      embedding-model: foo-bar-embedding-model 
      embedding-store: foo-bar-inmemory-embedding-store
```

- Creates a named embedding model singleton bean (`foo-bar-embedding-model`) in Helidon.
- Sets provider defaults for in-process embedding model creation.
- Uses the named in-process embedding model from the service registry.

For `type: custom`, configure model and tokenizer paths and pooling mode:

``` yaml
langchain4j:
  models:
    foo-bar-content-retriever:
      provider: lc4j-in-process
      type: custom 
      path-to-model: "/models/custom-embeddings/model.onnx" 
      path-to-tokenizer: "/models/custom-embeddings/tokenizer.json" 
      pooling-mode: mean 
```

- Uses user-provided ONNX model.
- Required for custom type.
- Required for custom type.
- Required for custom type; maps to LangChain4j pooling mode.

If `type: custom` is selected but any of `path-to-model`, `path-to-tokenizer`, or `pooling-mode` is missing, Helidon fails startup with a configuration exception.

``` java
@Service.Singleton
public class EmbeddingModelConsumer {
    EmbeddingModelConsumer(@Service.Named("foo-bar-embedding-model") EmbeddingModel embeddingModel) { 
    }
}
```

- Injects the named in-process embedding model bean directly into another Helidon declarative service.

Configuration properties:

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="adebfb-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the embedding model is enabled |
| <span id="a66290-executor"></span> `executor` | `VALUE` | `i.h.c.c.ThreadPoolConfig` |   | Executor configuration used by the embedding model |
| <span id="abd52a-path-to-model"></span> `path-to-model` | `VALUE` | `Path` |   | The path to the modelPath file (e.g., "/path/to/model.onnx") |
| <span id="a6fca5-path-to-tokenizer"></span> `path-to-tokenizer` | `VALUE` | `Path` |   | The path to the tokenizer file (e.g., "/path/to/tokenizer.json") |
| <span id="a20f0e-pooling-mode"></span> [`pooling-mode`](../../../config/dev_langchain4j_model_embedding_onnx_PoolingMode.md) | `VALUE` | `d.l.m.e.o.PoolingMode` |   | The pooling model to use |
| <span id="ab207e-type"></span> [`type`](../../../config/io_helidon_integrations_langchain4j_providers_lc4jinprocess_InProcessModelType.md) | `VALUE` | `i.h.i.l.p.l.InProcessModelType` |   | Which in-process ONNX model variant should be used |

## Additional Information

- [LangChain4j Integration](langchain4j.md)
- [Lc4j Built-in Providers](lc4j-providers.md)
- [Retrieval-Augmented Generation (RAG)](rag.md)
