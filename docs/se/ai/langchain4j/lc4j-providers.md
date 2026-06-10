# Built-in LangChain4j Providers

## Maven Coordinates

No additional dependencies are required beyond the [LangChain4j integration core dependencies](langchain4j.md#maven-coordinates).

## Content Retriever

Provider key: `lc4j-content-retriever`.

In LangChain4j [RAG][rag], `ContentRetriever` is the component that takes a user query, retrieves relevant content from an underlying data source, and returns ranked content used to augment the prompt.

In Helidon, this provider creates LangChain4j content retrievers from configuration. If `type` is not set, Helidon uses the default `embedding-store-content-retriever` (`ContentRetrieverType.EMBEDDING_STORE_CONTENT_RETRIEVER`) and wires it using the configured embedding model and embedding store.

In a typical RAG setup (see [RAG](rag.md)), a named retriever references:

- an `EmbeddingModel` (`embedding-model`)
- an `EmbeddingStore<TextSegment>` (`embedding-store`)

Each entry under `langchain4j.content-retrievers` becomes a named singleton declarative service bean in the Helidon service registry. You can attach it to AI services or agents using `@Ai.ContentRetriever("name")`, or inject it directly by name.

```yaml [application.yaml]
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

```java
@Ai.Service
@Ai.ChatModel("foo-bar-chat-model")
@Ai.ContentRetriever("foo-bar-content-retriever") 
public interface FooBarExpert {
    String askFoo(String foo);
}
```

- Binds this AI service to the named content retriever bean from configuration.

```java
@Service.Singleton
public class RetrieverConsumer {
    RetrieverConsumer(@Service.Named("foo-bar-content-retriever") ContentRetriever retriever) { 
    }
}
```

- Injects the same named content retriever bean directly into another Helidon declarative service.

Configuration properties:

### Configuration options

<!--@include ../../../config/io.helidon.integrations.langchain4j.ContentRetrieverConfig.md#configuration-options offset=1 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>embedding-model</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Explicit embedding model to use in the content retriever</td>
</tr>
<tr>
<td>
<code>display-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Display name for this content retriever configuration</td>
</tr>
<tr>
<td>
<code>embedding-store</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Embedding store to use in the content retriever</td>
</tr>
<tr>
<td>
<code>max-results</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Maximum number of results to return from the retriever</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.integrations.langchain4j.ContentRetrieverType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ContentRetrieverType">ContentRetrieverType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="EMBEDDING_STORE_CONTENT_RETRIEVER">EMBEDDING_STORE_CONTENT_RETRIEVER</code>
</td>
<td>Type of content retriever to create</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to <code>false</code>, component will be disabled even if configured</td>
</tr>
<tr>
<td>
<code>min-score</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
</td>
<td>Minimum score threshold for retrieved results</td>
</tr>
</tbody>
</table>
<!--/include-->


## In-Memory Embedding Store

Provider key: `lc4j-in-memory`.

In LangChain4j [in-memory embedding store integration][in-memory-embedd], `InMemoryEmbeddingStore` is an in-process vector store implementation suitable for local or lightweight use cases.

In Helidon, this provider creates `InMemoryEmbeddingStore<TextSegment>` instances from `langchain4j.embedding-stores.<name>` configuration entries.

Each entry becomes a named singleton declarative service bean in the Helidon service registry. That named embedding store can be referenced by configured content retrievers and can also be injected by name into other service beans.

If `from-file` is configured, Helidon initializes the store by loading previously persisted embeddings and segments using LangChain4j `InMemoryEmbeddingStore.fromFile(…​)`. If `from-file` is not configured, the store starts empty.

```yaml [application.yaml]
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

```java
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

<!--@include ../../../config/io.helidon.integrations.langchain4j.InMemoryEmbeddingStoreConfig.md#configuration-options offset=1 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>from-file</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path to a JSON file used to initialize the in-memory embedding store via <code>InMemoryEmbeddingStore.fromFile</code></td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether this embedding store component is enabled</td>
</tr>
</tbody>
</table>
<!--/include-->


## Additional Information

- [LangChain4j Integration](langchain4j.md)

[rag]: https://docs.langchain4j.dev/tutorials/rag
[in-memory-embedd]: https://docs.langchain4j.dev/integrations/embedding-stores/in-memory
