# io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser

## Description

A user that is loaded from configuration.

## Usages

- [`security.providers.http-basic-auth.users`](../config/io_helidon_security_providers_httpauth_HttpBasicAuthProvider.md#a18d67-users)
- [`security.providers.http-digest-auth.users`](../config/io_helidon_security_providers_httpauth_HttpDigestAuthProvider.md#a97822-users)
- [`server.features.security.security.providers.http-basic-auth.users`](../config/io_helidon_security_providers_httpauth_HttpBasicAuthProvider.md#a18d67-users)
- [`server.features.security.security.providers.http-digest-auth.users`](../config/io_helidon_security_providers_httpauth_HttpDigestAuthProvider.md#a97822-users)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a59ebd-login"></span> `login` | `VALUE` | `String` | User's login |
| <span id="ad18cf-password"></span> `password` | `VALUE` | `String` | User's password |
| <span id="ac329a-roles"></span> `roles` | `LIST` | `String` | List of roles the user is in |

See the [manifest](../config/manifest.md) for all available types.
