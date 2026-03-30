# io.helidon.integrations.langchain4j.providers.openai.OpenAiLanguageModelConfig

## Description

Configuration for LangChain4j model OpenAiLanguageModel.

## Usages

- [`langchain4j.providers.open-ai`](../config/config_reference.md#a76420-langchain4j-providers-open-ai)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aad3e8-api-key"></span> `api-key` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#apiKey(java.lang.String)` |
| <span id="a70497-base-url"></span> `base-url` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#baseUrl(java.lang.String)` |
| <span id="a83c20-custom-headers"></span> `custom-headers` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#customHeaders(java.util.Map)` |
| <span id="a480fc-custom-query-params"></span> `custom-query-params` | `MAP` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#customQueryParams(java.util.Map)` |
| <span id="a6bab3-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OpenAiLanguageModel will not be available even if configured |
| <span id="a751b4-http-client-builder"></span> `http-client-builder` | `VALUE` | `d.l.h.c.HttpClientBuilder` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#httpClientBuilder(dev.langchain4j.http.client.HttpClientBuilder)` |
| <span id="a4999f-http-client-builder-discover-services"></span> `http-client-builder-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `http-client-builder` |
| <span id="a32610-log-requests"></span> `log-requests` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#logRequests(java.lang.Boolean)` |
| <span id="a39a73-log-responses"></span> `log-responses` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#logResponses(java.lang.Boolean)` |
| <span id="a17c57-logger"></span> `logger` | `VALUE` | `o.s.Logger` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#logger(org.slf4j.Logger)` |
| <span id="ae9df5-max-retries"></span> `max-retries` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#maxRetries(java.lang.Integer)` |
| <span id="a5f93b-model-name"></span> `model-name` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#modelName(java.lang.String)` |
| <span id="a32bb4-organization-id"></span> `organization-id` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#organizationId(java.lang.String)` |
| <span id="ab0b7b-project-id"></span> `project-id` | `VALUE` | `String` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#projectId(java.lang.String)` |
| <span id="aaad4f-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#temperature(java.lang.Double)` |
| <span id="acf53a-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Generated from `dev.langchain4j.model.openai.OpenAiLanguageModel.OpenAiLanguageModelBuilder#timeout(java.time.Duration)` |

See the [manifest](../config/manifest.md) for all available types.
