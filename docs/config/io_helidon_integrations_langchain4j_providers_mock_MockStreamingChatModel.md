# io.helidon.integrations.langchain4j.providers.mock.MockStreamingChatModel

## Description

Configuration blueprint for

MockStreamingChatModel

.

## Usages

- [`langchain4j.providers.helidon-mock`](../config/config_reference.md#a2fc71-langchain4j-providers-helidon-mock)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="acca9f-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false` , MockChatModel will not be available even if configured |
| <span id="a4728e-rules"></span> [`rules`](../config/io_helidon_integrations_langchain4j_providers_mock_MockChatRule.md) | `LIST` | `i.h.i.l.p.m.MockChatRule` |   | The list of `MockChatRule`s that the mock chat model evaluates |

See the [manifest](../config/manifest.md) for all available types.
