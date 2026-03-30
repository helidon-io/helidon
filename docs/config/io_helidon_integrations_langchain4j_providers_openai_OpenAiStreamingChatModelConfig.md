# io.helidon.integrations.langchain4j.providers.openai.OpenAiStreamingChatModelConfig

## Description

Configuration for LangChain4j model OpenAiStreamingChatModel.

## Usages

- [`langchain4j.providers.open-ai`](../config/config_reference.md#ac2039-langchain4j-providers-open-ai)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="accc43-accumulate-tool-call-id"></span> `accumulate-tool-call-id` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#accumulateToolCallId(java.lang.Boolean)` |
| <span id="a3e6de-api-key"></span> `api-key` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#apiKey(java.lang.String)` |
| <span id="a9b52b-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#baseUrl(java.lang.String)` |
| <span id="ab056d-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#customHeaders(java.util.Map)` |
| <span id="a104e8-custom-parameters"></span> `custom-parameters` | `MAP` | `Object` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#customParameters(java.util.Map)` |
| <span id="ac4613-custom-query-params"></span> `custom-query-params` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#customQueryParams(java.util.Map)` |
| <span id="a19a63-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="ac5841-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="aa9d7f-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OpenAiStreamingChatModel will not be available even if configured |
| <span id="aa9592-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#frequencyPenalty(java.lang.Double)` |
| <span id="a82698-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="a1a0c7-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="a8cfc0-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#listeners(java.util.List)` |
| <span id="a69ed7-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="ab339f-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logRequests(java.lang.Boolean)` |
| <span id="aacf34-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logResponses(java.lang.Boolean)` |
| <span id="a8c2e8-logger"></span> `logger` | `VALUE` | `o.s.Logger` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logger(org.slf4j.Logger)` |
| <span id="a028fa-logit-bias"></span> `logit-bias` | `MAP` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#logitBias(java.util.Map)` |
| <span id="ad1f65-max-completion-tokens"></span> `max-completion-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#maxCompletionTokens(java.lang.Integer)` |
| <span id="a515b9-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#maxTokens(java.lang.Integer)` |
| <span id="ac4a31-metadata"></span> `metadata` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#metadata(java.util.Map)` |
| <span id="a437c3-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#modelName(java.lang.String)` |
| <span id="aefde4-organization-id"></span> `organization-id` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#organizationId(java.lang.String)` |
| <span id="a6f33d-parallel-tool-calls"></span> `parallel-tool-calls` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#parallelToolCalls(java.lang.Boolean)` |
| <span id="a73b91-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#presencePenalty(java.lang.Double)` |
| <span id="aa2a97-project-id"></span> `project-id` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#projectId(java.lang.String)` |
| <span id="ab50aa-reasoning-effort"></span> `reasoning-effort` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#reasoningEffort(java.lang.String)` |
| <span id="ae1f27-response-format"></span> `response-format` | `VALUE` | `String` |   | Enable a "JSON mode" in the model configuration |
| <span id="adc3f0-return-thinking"></span> `return-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#returnThinking(java.lang.Boolean)` |
| <span id="a095dc-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#seed(java.lang.Integer)` |
| <span id="a0bb06-send-thinking"></span> `send-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#sendThinking(java.lang.Boolean)` |
| <span id="afc41a-service-tier"></span> `service-tier` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#serviceTier(java.lang.String)` |
| <span id="a17131-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#stop(java.util.List)` |
| <span id="a59cda-store"></span> `store` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#store(java.lang.Boolean)` |
| <span id="a75509-strict-json-schema"></span> `strict-json-schema` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#strictJsonSchema(java.lang.Boolean)` |
| <span id="ad5c51-strict-tools"></span> `strict-tools` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#strictTools(java.lang.Boolean)` |
| <span id="ab5eda-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#temperature(java.lang.Double)` |
| <span id="a38e67-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#timeout(java.time.Duration)` |
| <span id="a42593-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#topP(java.lang.Double)` |
| <span id="a9291e-user"></span> `user` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder#user(java.lang.String)` |

See the [manifest](../config/manifest.md) for all available types.
