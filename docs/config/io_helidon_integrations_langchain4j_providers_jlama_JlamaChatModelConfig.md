# io.helidon.integrations.langchain4j.providers.jlama.JlamaChatModelConfig

## Description

Configuration for LangChain4j model JlamaChatModel.

## Usages

- [`langchain4j.providers.jlama`](../config/config_reference.md#a0b6bc-langchain4j-providers-jlama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af4b38-auth-token"></span> `auth-token` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#authToken(java.lang.String)` |
| <span id="af0cd3-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, JlamaChatModel will not be available even if configured |
| <span id="aa3287-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#maxTokens(java.lang.Integer)` |
| <span id="ad24b2-model-cache-path"></span> `model-cache-path` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#modelCachePath(java.nio.file.Path)` |
| <span id="afa0fc-model-name"></span> `model-name` | `VALUE` | `String` |   | Configure the model name |
| <span id="a11d12-quantize-model-at-runtime"></span> `quantize-model-at-runtime` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)` |
| <span id="a644ff-temperature"></span> `temperature` | `VALUE` | `Float` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#temperature(java.lang.Float)` |
| <span id="aacd12-thread-count"></span> `thread-count` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#threadCount(java.lang.Integer)` |
| <span id="a82adf-working-directory"></span> `working-directory` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#workingDirectory(java.nio.file.Path)` |
| <span id="a2c1de-working-quantized-type"></span> [`working-quantized-type`](../config/com_github_tjake_jlama_safetensors_DType.md) | `VALUE` | `c.g.t.j.s.DType` |   | Generated from `dev.langchain4j.model.jlama.JlamaChatModel.JlamaChatModelBuilder#workingQuantizedType(com.github.tjake.jlama.safetensors.DType)` |

See the [manifest](../config/manifest.md) for all available types.
