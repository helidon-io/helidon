# io.helidon.integrations.langchain4j.providers.jlama.JlamaEmbeddingModelConfig

## Description

Configuration for LangChain4j model JlamaEmbeddingModel.

## Usages

- [`langchain4j.providers.jlama`](../config/config_reference.md#a2ba60-langchain4j-providers-jlama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac6e53-auth-token"></span> `auth-token` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#authToken(java.lang.String)` |
| <span id="a01169-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, JlamaEmbeddingModel will not be available even if configured |
| <span id="a06dd9-model-cache-path"></span> `model-cache-path` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#modelCachePath(java.nio.file.Path)` |
| <span id="a77594-model-name"></span> `model-name` | `VALUE` | `String` |   | Configure the model name |
| <span id="a396e9-pooling-type"></span> [`pooling-type`](../config/com_github_tjake_jlama_model_functions_Generator_PoolingType.md) | `VALUE` | `c.g.t.j.m.f.G.PoolingType` |   | Generated from `dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#poolingType(com.github.tjake.jlama.model.functions.Generator.PoolingType)` |
| <span id="aa6135-quantize-model-at-runtime"></span> `quantize-model-at-runtime` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)` |
| <span id="a233b1-thread-count"></span> `thread-count` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#threadCount(java.lang.Integer)` |
| <span id="a6f4c6-working-directory"></span> `working-directory` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaEmbeddingModel.JlamaEmbeddingModelBuilder#workingDirectory(java.nio.file.Path)` |

See the [manifest](../config/manifest.md) for all available types.
