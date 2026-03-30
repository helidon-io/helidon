# io.helidon.integrations.langchain4j.providers.ollama.OllamaChatModelConfig

## Description

Configuration for LangChain4j model OllamaChatModel.

## Usages

- [`langchain4j.providers.ollama`](../config/config_reference.md#a20890-langchain4j-providers-ollama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af634b-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#baseUrl(java.lang.String)` |
| <span id="a6cb5a-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#customHeaders(java.util.Map)` |
| <span id="ae6f90-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="ae95cd-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="a10b19-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OllamaChatModel will not be available even if configured |
| <span id="a85b62-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="ab261e-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="ad3781-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#listeners(java.util.List)` |
| <span id="a2c7f0-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a47d21-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logRequests(java.lang.Boolean)` |
| <span id="a79768-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logResponses(java.lang.Boolean)` |
| <span id="a770ac-logger"></span> `logger` | `VALUE` | `o.s.Logger` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logger(org.slf4j.Logger)` |
| <span id="a88533-max-retries"></span> `max-retries` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder#maxRetries(java.lang.Integer)` |
| <span id="a378e1-min-p"></span> `min-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#minP(java.lang.Double)` |
| <span id="a23ebc-mirostat"></span> `mirostat` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostat(java.lang.Integer)` |
| <span id="a59ab1-mirostat-eta"></span> `mirostat-eta` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostatEta(java.lang.Double)` |
| <span id="a88814-mirostat-tau"></span> `mirostat-tau` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostatTau(java.lang.Double)` |
| <span id="ac4e4b-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#modelName(java.lang.String)` |
| <span id="aaca9c-num-ctx"></span> `num-ctx` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#numCtx(java.lang.Integer)` |
| <span id="a78e00-num-predict"></span> `num-predict` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#numPredict(java.lang.Integer)` |
| <span id="a1360a-repeat-last-n"></span> `repeat-last-n` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#repeatLastN(java.lang.Integer)` |
| <span id="a81115-repeat-penalty"></span> `repeat-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#repeatPenalty(java.lang.Double)` |
| <span id="aa0190-response-format"></span> `response-format` | `VALUE` | `d.l.m.c.r.ResponseFormat` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)` |
| <span id="aad587-return-thinking"></span> `return-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#returnThinking(java.lang.Boolean)` |
| <span id="a545b7-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#seed(java.lang.Integer)` |
| <span id="a8d295-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#stop(java.util.List)` |
| <span id="a00a62-supported-capabilities"></span> [`supported-capabilities`](../config/dev_langchain4j_model_chat_Capability.md) | `LIST` | `d.l.m.c.Capability` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#supportedCapabilities(java.util.Set)` |
| <span id="a50119-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#temperature(java.lang.Double)` |
| <span id="aededb-think"></span> `think` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#think(java.lang.Boolean)` |
| <span id="a769be-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#timeout(java.time.Duration)` |
| <span id="ab296c-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#topK(java.lang.Integer)` |
| <span id="a5f898-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
