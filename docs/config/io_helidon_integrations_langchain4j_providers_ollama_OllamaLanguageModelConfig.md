# io.helidon.integrations.langchain4j.providers.ollama.OllamaLanguageModelConfig

## Description

Configuration for LangChain4j model OllamaLanguageModel.

## Usages

- [`langchain4j.providers.ollama`](../config/config_reference.md#aceb19-langchain4j-providers-ollama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a937a3-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#baseUrl(java.lang.String)` |
| <span id="aea319-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#customHeaders(java.util.Map)` |
| <span id="adc48d-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OllamaLanguageModel will not be available even if configured |
| <span id="a49cd1-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="a8cbae-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="ab357a-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#logRequests(java.lang.Boolean)` |
| <span id="a57ca5-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#logResponses(java.lang.Boolean)` |
| <span id="aaefae-max-retries"></span> `max-retries` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#maxRetries(java.lang.Integer)` |
| <span id="a03c5a-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#modelName(java.lang.String)` |
| <span id="a8c04c-num-ctx"></span> `num-ctx` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#numCtx(java.lang.Integer)` |
| <span id="a87eaf-num-predict"></span> `num-predict` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#numPredict(java.lang.Integer)` |
| <span id="acc3df-repeat-penalty"></span> `repeat-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#repeatPenalty(java.lang.Double)` |
| <span id="a7cca3-response-format"></span> `response-format` | `VALUE` | `d.l.m.c.r.ResponseFormat` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)` |
| <span id="a109c5-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#seed(java.lang.Integer)` |
| <span id="add86c-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#stop(java.util.List)` |
| <span id="ab3443-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#temperature(java.lang.Double)` |
| <span id="a434d2-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#timeout(java.time.Duration)` |
| <span id="a4ea68-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#topK(java.lang.Integer)` |
| <span id="a28ea5-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaLanguageModel.OllamaLanguageModelBuilder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
