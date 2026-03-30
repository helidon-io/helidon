# io.helidon.integrations.langchain4j.providers.jlama.JlamaStreamingChatModelConfig

## Description

Configuration for LangChain4j model JlamaStreamingChatModel.

## Usages

- [`langchain4j.providers.jlama`](../config/config_reference.md#a40390-langchain4j-providers-jlama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a33ccb-auth-token"></span> `auth-token` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#authToken(java.lang.String)` |
| <span id="aa1ba8-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, JlamaStreamingChatModel will not be available even if configured |
| <span id="a84b79-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#maxTokens(java.lang.Integer)` |
| <span id="a2a5b5-model-cache-path"></span> `model-cache-path` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#modelCachePath(java.nio.file.Path)` |
| <span id="a502d4-model-name"></span> `model-name` | `VALUE` | `String` |   | Configure the model name |
| <span id="a5048b-quantize-model-at-runtime"></span> `quantize-model-at-runtime` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#quantizeModelAtRuntime(java.lang.Boolean)` |
| <span id="ac321b-temperature"></span> `temperature` | `VALUE` | `Float` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#temperature(java.lang.Float)` |
| <span id="a1f1d0-thread-count"></span> `thread-count` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#threadCount(java.lang.Integer)` |
| <span id="aaaabb-working-directory"></span> `working-directory` | `VALUE` | `Path` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#workingDirectory(java.nio.file.Path)` |
| <span id="a4e893-working-quantized-type"></span> [`working-quantized-type`](../config/com_github_tjake_jlama_safetensors_DType.md) | `VALUE` | `c.g.t.j.s.DType` |   | Generated from `dev.langchain4j.model.jlama.JlamaStreamingChatModel.JlamaStreamingChatModelBuilder#workingQuantizedType(com.github.tjake.jlama.safetensors.DType)` |

See the [manifest](../config/manifest.md) for all available types.
