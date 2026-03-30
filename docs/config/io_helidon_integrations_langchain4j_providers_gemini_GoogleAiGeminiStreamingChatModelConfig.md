# io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiStreamingChatModelConfig

## Description

Configuration for LangChain4j model GoogleAiGeminiStreamingChatModel.

## Usages

- [`langchain4j.providers.google-gemini`](../config/config_reference.md#a43f3c-langchain4j-providers-google-gemini)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aefc4e-allow-code-execution"></span> `allow-code-execution` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowCodeExecution(java.lang.Boolean)` |
| <span id="a24bf9-allow-google-maps"></span> `allow-google-maps` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowGoogleMaps(java.lang.Boolean)` |
| <span id="abb9b4-allow-google-search"></span> `allow-google-search` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowGoogleSearch(java.lang.Boolean)` |
| <span id="aa3f89-allow-url-context"></span> `allow-url-context` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#allowUrlContext(java.lang.Boolean)` |
| <span id="a025f7-api-key"></span> `api-key` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#apiKey(java.lang.String)` |
| <span id="af89c6-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#baseUrl(java.lang.String)` |
| <span id="a37566-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="a928d4-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="a4c855-enable-enhanced-civic-answers"></span> `enable-enhanced-civic-answers` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#enableEnhancedCivicAnswers(java.lang.Boolean)` |
| <span id="a5949a-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, GoogleAiGeminiStreamingChatModel will not be available even if configured |
| <span id="a7a6e0-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#frequencyPenalty(java.lang.Double)` |
| <span id="a267b6-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="aa5f81-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="ab8d12-include-code-execution-output"></span> `include-code-execution-output` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#includeCodeExecutionOutput(java.lang.Boolean)` |
| <span id="abe342-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#listeners(java.util.List)` |
| <span id="af5b63-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a394ff-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logRequests(java.lang.Boolean)` |
| <span id="a1f8a7-log-requests-and-responses"></span> `log-requests-and-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logRequestsAndResponses(java.lang.Boolean)` |
| <span id="aba4d9-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logResponses(java.lang.Boolean)` |
| <span id="a01bc5-logger"></span> `logger` | `VALUE` | `o.s.Logger` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logger(org.slf4j.Logger)` |
| <span id="a1d643-logprobs"></span> `logprobs` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#logprobs(java.lang.Integer)` |
| <span id="aae4c9-max-output-tokens"></span> `max-output-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#maxOutputTokens(java.lang.Integer)` |
| <span id="abb6a1-media-resolution"></span> [`media-resolution`](../config/dev_langchain4j_model_googleai_GeminiMediaResolutionLevel.md) | `VALUE` | `d.l.m.g.GeminiMediaResolutionLevel` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#mediaResolution(dev.langchain4j.model.googleai.GeminiMediaResolutionLevel)` |
| <span id="a7e616-media-resolution-per-part-enabled"></span> `media-resolution-per-part-enabled` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#mediaResolutionPerPartEnabled(java.lang.Boolean)` |
| <span id="a8996d-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#modelName(java.lang.String)` |
| <span id="a6ac07-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#presencePenalty(java.lang.Double)` |
| <span id="af2f0f-response-format"></span> `response-format` | `VALUE` | `d.l.m.c.r.ResponseFormat` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#responseFormat(dev.langchain4j.model.chat.request.ResponseFormat)` |
| <span id="a1442f-response-logprobs"></span> `response-logprobs` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#responseLogprobs(java.lang.Boolean)` |
| <span id="a5574d-retrieve-google-maps-widget-token"></span> `retrieve-google-maps-widget-token` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#retrieveGoogleMapsWidgetToken(java.lang.Boolean)` |
| <span id="ab15e0-return-thinking"></span> `return-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#returnThinking(java.lang.Boolean)` |
| <span id="a35d88-safety-settings"></span> `safety-settings` | `LIST` | `d.l.m.g.GeminiSafetySetting` |   | Safety setting, affecting the safety-blocking behavior |
| <span id="a8b284-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#seed(java.lang.Integer)` |
| <span id="abd048-send-thinking"></span> `send-thinking` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#sendThinking(java.lang.Boolean)` |
| <span id="a52102-stop-sequences"></span> `stop-sequences` | `LIST` | `String` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#stopSequences(java.util.List)` |
| <span id="ab2c07-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#temperature(java.lang.Double)` |
| <span id="ac06bc-thinking-config"></span> `thinking-config` | `VALUE` | `d.l.m.g.GeminiThinkingConfig` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#thinkingConfig(dev.langchain4j.model.googleai.GeminiThinkingConfig)` |
| <span id="acfe6e-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#timeout(java.time.Duration)` |
| <span id="a30323-tool-config"></span> `tool-config` | `VALUE` | `d.l.m.g.GeminiFunctionCallingConfig` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#toolConfig(dev.langchain4j.model.googleai.GeminiFunctionCallingConfig)` |
| <span id="a3c1a7-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#topK(java.lang.Integer)` |
| <span id="a57eab-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.googleai.BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
