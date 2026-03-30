# io.helidon.integrations.langchain4j.providers.ollama.OllamaStreamingChatModelConfig

## Description

Configuration for LangChain4j model OllamaStreamingChatModel.

## Usages

- [`langchain4j.providers.ollama`](../config/config_reference.md#ae83f5-langchain4j-providers-ollama)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a607ce-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#baseUrl(java.lang.String)` |
| <span id="aaf434-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#customHeaders(java.util.Map)` |
| <span id="a4455e-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="a3203d-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="ab0ad9-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OllamaStreamingChatModel will not be available even if configured |
| <span id="a4ce33-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="a07c5f-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="adbb6a-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#listeners(java.util.List)` |
| <span id="a1b96d-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a9903b-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logRequests(java.lang.Boolean)` |
| <span id="ad1d07-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logResponses(java.lang.Boolean)` |
| <span id="a6c117-logger"></span> `logger` | `VALUE` | `o.s.Logger` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#logger(org.slf4j.Logger)` |
| <span id="ab3007-min-p"></span> `min-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#minP(java.lang.Double)` |
| <span id="ad9c75-mirostat"></span> `mirostat` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostat(java.lang.Integer)` |
| <span id="ae81b8-mirostat-eta"></span> `mirostat-eta` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostatEta(java.lang.Double)` |
| <span id="ab4218-mirostat-tau"></span> `mirostat-tau` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#mirostatTau(java.lang.Double)` |
| <span id="aafaea-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#modelName(java.lang.String)` |
| <span id="a0a204-num-ctx"></span> `num-ctx` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#numCtx(java.lang.Integer)` |
| <span id="aa1e29-num-predict"></span> `num-predict` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#numPredict(java.lang.Integer)` |
| <span id="a61887-repeat-last-n"></span> `repeat-last-n` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#repeatLastN(java.lang.Integer)` |
| <span id="a19e07-repeat-penalty"></span> `repeat-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#repeatPenalty(java.lang.Double)` |
| <span id="a5650d-response-format"></span> `response-format` | `VALUE` | `d.l.m.c.r.ResponseFormat` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)` |
| <span id="a1df43-return-thinking"></span> `return-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#returnThinking(java.lang.Boolean)` |
| <span id="abe0f1-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#seed(java.lang.Integer)` |
| <span id="a23701-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#stop(java.util.List)` |
| <span id="ad757d-supported-capabilities"></span> [`supported-capabilities`](../config/dev_langchain4j_model_chat_Capability.md) | `LIST` | `d.l.m.c.Capability` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#supportedCapabilities(java.util.Set)` |
| <span id="a2f272-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#temperature(java.lang.Double)` |
| <span id="a70e63-think"></span> `think` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#think(java.lang.Boolean)` |
| <span id="a35bc3-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#timeout(java.time.Duration)` |
| <span id="a55aa2-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#topK(java.lang.Integer)` |
| <span id="ad64fe-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.ollama.OllamaBaseChatModel.Builder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
