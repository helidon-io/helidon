# Built-in LangChain4j Providers

## Contents

- [Maven Coordinates](#maven-coordinates)
- [Content Retriever](#content-retriever)
- [In-Memory Embedding Store](#in-memory-embedding-store)
- [Additional Information](#additional-information)

## Maven Coordinates

No additional dependencies are required beyond the [LangChain4j integration core dependencies](langchain4j.md#maven-coordinates).

## Content Retriever

Provider key: `lc4j-content-retriever`.

In LangChain4j [RAG](https://docs.langchain4j.dev/tutorials/rag), `ContentRetriever` is the component that takes a user query, retrieves relevant content from an underlying data source, and returns ranked content used to augment the prompt.

In Helidon, this provider creates LangChain4j content retrievers from configuration. If `type` is not set, Helidon uses the default `embedding-store-content-retriever` (`ContentRetrieverType.EMBEDDING_STORE_CONTENT_RETRIEVER`) and wires it using the configured embedding model and embedding store.

In a typical RAG setup (see [RAG](rag.md)), a named retriever references:

- an `EmbeddingModel` (`embedding-model`)
- an `EmbeddingStore<TextSegment>` (`embedding-store`)

Each entry under `langchain4j.content-retrievers` becomes a named singleton declarative service bean in the Helidon service registry. You can attach it to AI services or agents using `@Ai.ContentRetriever("name")`, or inject it directly by name.

``` yaml
langchain4j:
  content-retrievers:
    foo-bar-content-retriever:
      provider: lc4j-content-retriever 
      type: embedding-store-content-retriever 
      embedding-store: foo-bar-inmemory-embedding-store 
      embedding-model: foo-bar-embedding-model 
      max-results: 10
      min-score: 0.6
```

- Selects the built-in content retriever provider.
- Explicitly selects the default LangChain4j embedding-store-backed retriever type.
- Names the embedding store bean used for similarity search.
- Sets the embedding model used to convert incoming query text to vectors.

``` java
@Ai.Service
@Ai.ChatModel("foo-bar-chat-model")
@Ai.ContentRetriever("foo-bar-content-retriever") 
public interface FooBarExpert {
    String askFoo(String foo);
}
```

- Binds this AI service to the named content retriever bean from configuration.

``` java
@Service.Singleton
public class RetrieverConsumer {
    RetrieverConsumer(@Service.Named("foo-bar-content-retriever") ContentRetriever retriever) { 
    }
}
```

- Injects the same named content retriever bean directly into another Helidon declarative service.

Configuration properties:

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa8c41-display-name"></span> `display-name` | `VALUE` | `String` |   | Display name for this content retriever configuration |
| <span id="aed316-embedding-model"></span> `embedding-model` | `VALUE` | `String` |   | Explicit embedding model to use in the content retriever |
| <span id="aa55e0-embedding-store"></span> `embedding-store` | `VALUE` | `String` |   | Embedding store to use in the content retriever |
| <span id="a2a276-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, component will be disabled even if configured |
| <span id="a7fe57-max-results"></span> `max-results` | `VALUE` | `Integer` |   | Maximum number of results to return from the retriever |
| <span id="a4bf10-min-score"></span> `min-score` | `VALUE` | `Double` |   | Minimum score threshold for retrieved results |
| <span id="afd89c-type"></span> [`type`](../../../config/io_helidon_integrations_langchain4j_ContentRetrieverType.md) | `VALUE` | `i.h.i.l.ContentRetrieverType` | `EMBEDDING_STORE_CONTENT_RETRIEVER` | Type of content retriever to create |

## In-Memory Embedding Store

Provider key: `lc4j-in-memory`.

In LangChain4j [in-memory embedding store integration](https://docs.langchain4j.dev/integrations/embedding-stores/in-memory), `InMemoryEmbeddingStore` is an in-process vector store implementation suitable for local or lightweight use cases.

In Helidon, this provider creates `InMemoryEmbeddingStore<TextSegment>` instances from `langchain4j.embedding-stores.<name>` configuration entries.

Each entry becomes a named singleton declarative service bean in the Helidon service registry. That named embedding store can be referenced by configured content retrievers and can also be injected by name into other service beans.

If `from-file` is configured, Helidon initializes the store by loading previously persisted embeddings and segments using LangChain4j `InMemoryEmbeddingStore.fromFile(…​)`. If `from-file` is not configured, the store starts empty.

``` yaml
langchain4j:
  embedding-stores:
    foo-bar-inmemory-embedding-store:
      provider: lc4j-in-memory 
      # optional: preload persisted store content
      from-file: "target/foo-bar-inmemory-embedding-store.json" 

  content-retrievers:
    foo-bar-content-retriever:
      provider: lc4j-content-retriever
      embedding-model: foo-bar-embedding-model
      embedding-store: foo-bar-inmemory-embedding-store 
```

- Selects the built-in LangChain4j in-memory embedding store provider.
- Loads previously persisted embeddings and text segments during startup.
- Connects the retriever to the named in-memory embedding store bean.

``` java
@Service.Singleton
public class EmbeddingStoreLifecycle {
    private final InMemoryEmbeddingStore<TextSegment> store;

    EmbeddingStoreLifecycle(@Service.Named("foo-bar-inmemory-embedding-store")
                            InMemoryEmbeddingStore<TextSegment> store) {
        this.store = store;
    }

    @Service.PreDestroy 
    void persistEmbeddingStore() {
        store.serializeToFile(Path.of("target/foo-bar-inmemory-embedding-store.json")); 
    }
}
```

- Invoked by Helidon when the singleton service bean is being shut down.
- Persists current in-memory embeddings and segments to JSON file; the same file can be loaded on next startup using `from-file`.

Configuration properties:

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ae50fb-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this embedding store component is enabled |
| <span id="afeb29-from-file"></span> `from-file` | `VALUE` | `Path` |   | Path to a JSON file used to initialize the in-memory embedding store via `InMemoryEmbeddingStore.fromFile` |

## Additional Information

- [LangChain4j Integration](langchain4j.md)
