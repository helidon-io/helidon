# io.helidon.integrations.langchain4j.providers.mock.MockChatModel

## Description

Configuration blueprint for

MockChatModel

.

## Usages

- [`langchain4j.providers.helidon-mock`](../config/config_reference.md#a5268a-langchain4j-providers-helidon-mock)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac9963-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false` , MockChatModel will not be available even if configured |
| <span id="aa2a29-rules"></span> [`rules`](../config/io_helidon_integrations_langchain4j_providers_mock_MockChatRule.md) | `LIST` | `i.h.i.l.p.m.MockChatRule` |   | The list of `MockChatRule`s that the mock chat model evaluates |

See the [manifest](../config/manifest.md) for all available types.
