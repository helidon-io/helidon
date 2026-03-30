# io.helidon.integrations.langchain4j.providers.jlama.JlamaLanguageModelConfig

## Description

Configuration for LangChain4j model JlamaLanguageModel.

## Usages

- [`langchain4j.providers.jlama`](../config/config_reference.md#aaedd8-langchain4j-providers-jlama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a54cd8-auth-token"></span> `auth-token` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#authToken(java.lang.String)` |
| <span id="afcb89-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, JlamaLanguageModel will not be available even if configured |
| <span id="a602ed-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#maxTokens(java.lang.Integer)` |
| <span id="a4e7b4-model-cache-path"></span> `model-cache-path` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#modelCachePath(java.nio.file.Path)` |
| <span id="af00ba-model-name"></span> `model-name` | `VALUE` | `String` |   | Configure the model name |
| <span id="a03ae1-quantize-model-at-runtime"></span> `quantize-model-at-runtime` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)` |
| <span id="af1ad8-temperature"></span> `temperature` | `VALUE` | `Float` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#temperature(java.lang.Float)` |
| <span id="a91854-thread-count"></span> `thread-count` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#threadCount(java.lang.Integer)` |
| <span id="a589a3-working-directory"></span> `working-directory` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#workingDirectory(java.nio.file.Path)` |
| <span id="a84eff-working-quantized-type"></span> [`working-quantized-type`](../config/com_github_tjake_jlama_safetensors_DType.md) | `VALUE` | `c.g.t.j.s.DType` |   | Generated from `dev.langchain4j.model.jlama.JlamaLanguageModel.JlamaLanguageModelBuilder#workingQuantizedType(com.github.tjake.jlama.safetensors.DType)` |

See the [manifest](../config/manifest.md) for all available types.
