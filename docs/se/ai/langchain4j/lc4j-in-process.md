# In-Process Embedding Models

## Maven Coordinates

In addition to the [LangChain4j integration core
dependencies][langchain4j-inte], add:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.integrations.langchain4j.providers</groupId>
  <artifactId>helidon-integrations-langchain4j-providers-lc4j-in-process</artifactId>
</dependency>
```

Depending on configured model `type`, add model artifact dependencies as
follows:

- For `type: all_minilm_l6_v2`, add:

  ```xml [pom.xml]
  <dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
  </dependency>
  ```

- For `type: all_minilm_l6_v2_q`, add:

  ```xml [pom.xml]
  <dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2-q</artifactId>
  </dependency>
  ```

- For `type: custom`, no additional model-specific dependency is required.
  Configure `path-to-model`, `path-to-tokenizer`, and `pooling-mode`.

## In-Process Embedding Model

Provider key: `lc4j-in-process`.

LangChain4j in-process embedding models run ONNX embedding inference locally in
your process (see [LangChain4j documentation][langchain4j-docu]).

In Helidon, a named entry under `langchain4j.models` with `provider:
lc4j-in-process` is created as a named singleton declarative service bean in the
Helidon service registry. This is how `foo-bar-embedding-model` becomes
available for content retrievers and direct injection.

```yaml [application.yaml]
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

- Creates a named embedding model singleton bean (`foo-bar-embedding-model`) in
  Helidon.
- Sets provider defaults for in-process embedding model creation.
- Uses the named in-process embedding model from the service registry.

For `type: custom`, configure model and tokenizer paths and pooling mode:

```yaml [application.yaml]
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

If `type: custom` is selected but any of `path-to-model`, `path-to-tokenizer`,
or `pooling-mode` is missing, Helidon fails startup with a configuration
exception.

```java
@Service.Singleton
public class EmbeddingModelConsumer {
    EmbeddingModelConsumer(@Service.Named("foo-bar-embedding-model") EmbeddingModel embeddingModel) { 
    }
}
```

- Injects the named in-process embedding model bean directly into another
  Helidon declarative service.

Configuration properties:

### Configuration options

<!--@include ../../../config/io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-integ].
<!--/include-->


## Additional Information

- [LangChain4j Integration](langchain4j.md)
- [Lc4j Built-in Providers](lc4j-providers.md)
- [Retrieval-Augmented Generation (RAG)](rag.md)

[langchain4j-docu]: https://docs.langchain4j.dev/integrations/embedding-models/in-process/
[langchain4j-inte]: langchain4j.md#maven-coordinates
[io-helidon-integ]: ../../../config/io.helidon.integrations.langchain4j.providers.lc4jinprocess.InProcessEmbeddingModelConfig.md#configuration-options
