# io.helidon.integrations.langchain4j.providers.mock.MockChatRule

## Description

Configuration blueprint for

MockChatRule

.

## Usages

- [`langchain4j.providers.helidon-mock.rules`](../config/io_helidon_integrations_langchain4j_providers_mock_MockStreamingChatModel.md#a4728e-rules)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a695f3-pattern"></span> `pattern` | `VALUE` | `Pattern` | The regular expression pattern that this rule matches |
| <span id="ad2abc-response"></span> `response` | `VALUE` | `String` | Static text response that will be returned when the pattern matches |
| <span id="a1e3f8-template"></span> `template` | `VALUE` | `String` | Response template (e.g., using placeholders ex.: '\$1' for regex pattern group 1) used when the pattern matches |

See the [manifest](../config/manifest.md) for all available types.
