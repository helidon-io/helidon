# io.helidon.integrations.langchain4j.providers.ollama.OllamaEmbeddingModelConfig

## Description

Configuration for LangChain4j model OllamaEmbeddingModel.

## Usages

- [`langchain4j.providers.ollama`](../config/config_reference.md#a503d7-langchain4j-providers-ollama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a82adc-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#baseUrl(java.lang.String)` |
| <span id="a2985e-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#customHeaders(java.util.Map)` |
| <span id="aa6216-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OllamaEmbeddingModel will not be available even if configured |
| <span id="ab3cf3-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="ab8c1d-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="aab75e-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#logRequests(java.lang.Boolean)` |
| <span id="a72cd2-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#logResponses(java.lang.Boolean)` |
| <span id="ac3745-max-retries"></span> `max-retries` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#maxRetries(java.lang.Integer)` |
| <span id="a423cc-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#modelName(java.lang.String)` |
| <span id="a63213-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder#timeout(java.time.Duration)` |

See the [manifest](../config/manifest.md) for all available types.
