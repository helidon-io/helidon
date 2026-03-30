# io.helidon.security.ProviderSelectionPolicyType

## Description

This type is an enumeration.

## Usages

- [`security.provider-policy.type`](io_helidon_security_Security.md#a28411-provider-policy-type)
- [`server.features.security.security.provider-policy.type`](io_helidon_security_Security.md#a28411-provider-policy-type)

## Allowed Values

| Value | Description |
|----|----|
| `FIRST` | Choose first provider from the list by default |
| `COMPOSITE` | Can compose multiple providers together to form a single logical provider |
| `CLASS` | Explicit class for a custom `ProviderSelectionPolicyType` |

See the [manifest](../config/manifest.md) for all available types.
