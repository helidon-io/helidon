# io.helidon.integrations.langchain4j.providers.openai.OpenAiChatModelConfig

## Description

Configuration for LangChain4j model OpenAiChatModel.

## Usages

- [`langchain4j.providers.open-ai`](../config/config_reference.md#af16db-langchain4j-providers-open-ai)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a2ad3d-api-key"></span> `api-key` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#apiKey(java.lang.String)` |
| <span id="aff19a-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#baseUrl(java.lang.String)` |
| <span id="a1414f-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customHeaders(java.util.Map)` |
| <span id="aaa06d-custom-parameters"></span> `custom-parameters` | `MAP` | `Object` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customParameters(java.util.Map)` |
| <span id="ac7d3d-custom-query-params"></span> `custom-query-params` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#customQueryParams(java.util.Map)` |
| <span id="a49ad3-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="a8666c-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="afce05-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OpenAiChatModel will not be available even if configured |
| <span id="a14a49-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#frequencyPenalty(java.lang.Double)` |
| <span id="ae2a61-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="a98227-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="a1e52b-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#listeners(java.util.List)` |
| <span id="ae3dfd-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a4de8a-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logRequests(java.lang.Boolean)` |
| <span id="a09449-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logResponses(java.lang.Boolean)` |
| <span id="ac2af9-logger"></span> `logger` | `VALUE` | `o.s.Logger` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logger(org.slf4j.Logger)` |
| <span id="ac1beb-logit-bias"></span> `logit-bias` | `MAP` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#logitBias(java.util.Map)` |
| <span id="ae4949-max-completion-tokens"></span> `max-completion-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxCompletionTokens(java.lang.Integer)` |
| <span id="af39ab-max-retries"></span> `max-retries` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxRetries(java.lang.Integer)` |
| <span id="a766de-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#maxTokens(java.lang.Integer)` |
| <span id="a0f593-metadata"></span> `metadata` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#metadata(java.util.Map)` |
| <span id="a4f26f-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#modelName(java.lang.String)` |
| <span id="a96c0d-organization-id"></span> `organization-id` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#organizationId(java.lang.String)` |
| <span id="a1daa3-parallel-tool-calls"></span> `parallel-tool-calls` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#parallelToolCalls(java.lang.Boolean)` |
| <span id="a2b07a-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#presencePenalty(java.lang.Double)` |
| <span id="a91ecb-project-id"></span> `project-id` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#projectId(java.lang.String)` |
| <span id="ae5803-reasoning-effort"></span> `reasoning-effort` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#reasoningEffort(java.lang.String)` |
| <span id="ae72c9-response-format"></span> `response-format` | `VALUE` | `String` |   | Enable a "JSON mode" in the model configuration |
| <span id="ac7a44-return-thinking"></span> `return-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#returnThinking(java.lang.Boolean)` |
| <span id="ad4fae-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#seed(java.lang.Integer)` |
| <span id="ad671f-send-thinking"></span> `send-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#sendThinking(java.lang.Boolean)` |
| <span id="a5e100-service-tier"></span> `service-tier` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#serviceTier(java.lang.String)` |
| <span id="a95b1f-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#stop(java.util.List)` |
| <span id="a763a2-store"></span> `store` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#store(java.lang.Boolean)` |
| <span id="a9664a-strict-json-schema"></span> `strict-json-schema` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#strictJsonSchema(java.lang.Boolean)` |
| <span id="a109ce-strict-tools"></span> `strict-tools` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#strictTools(java.lang.Boolean)` |
| <span id="a27dec-supported-capabilities"></span> [`supported-capabilities`](../config/dev_langchain4j_model_chat_Capability.md) | `LIST` | `d.l.m.c.Capability` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#supportedCapabilities(java.util.Set)` |
| <span id="a850e6-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#temperature(java.lang.Double)` |
| <span id="a0ba74-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#timeout(java.time.Duration)` |
| <span id="a31752-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#topP(java.lang.Double)` |
| <span id="ae728f-user"></span> `user` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder#user(java.lang.String)` |

See the [manifest](../config/manifest.md) for all available types.
