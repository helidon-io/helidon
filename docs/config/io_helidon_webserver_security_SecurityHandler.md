# io.helidon.webserver.security.SecurityHandler

## Description

Configuration of a

io.helidon.webserver.security.SecurityHandler

.

## Usages

- [`server.features.security.defaults`](../config/io_helidon_webserver_security_SecurityFeature.md#abe190-defaults)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a25c1e-audit"></span> `audit` | `VALUE` | `Boolean` | Whether to audit this request - defaults to false, if enabled, request is audited with event type "request" |
| <span id="a00848-audit-event-type"></span> `audit-event-type` | `VALUE` | `String` | Override for event-type, defaults to `SecurityHandler#DEFAULT_AUDIT_EVENT_TYPE` |
| <span id="a7bc5f-audit-message-format"></span> `audit-message-format` | `VALUE` | `String` | Override for audit message format, defaults to `SecurityHandler#DEFAULT_AUDIT_MESSAGE_FORMAT` |
| <span id="a576f1-authenticate"></span> `authenticate` | `VALUE` | `Boolean` | If called, request will go through authentication process - defaults to false (even if authorize is true) |
| <span id="a96592-authentication-optional"></span> `authentication-optional` | `VALUE` | `Boolean` | If called, authentication failure will not abort request and will continue as anonymous (defaults to false) |
| <span id="a21ab6-authenticator"></span> `authenticator` | `VALUE` | `String` | Use a named authenticator (as supported by security - if not defined, default authenticator is used) |
| <span id="ab042b-authorize"></span> `authorize` | `VALUE` | `Boolean` | Enable authorization for this route |
| <span id="a4bd45-authorizer"></span> `authorizer` | `VALUE` | `String` | Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is permitted) |
| <span id="a97275-roles-allowed"></span> `roles-allowed` | `LIST` | `String` | An array of allowed roles for this path - must have a security provider supporting roles (either authentication or authorization provider) |
| <span id="aa4b7d-sockets"></span> `sockets` | `LIST` | `String` | List of sockets this configuration should be applied to |

See the [manifest](../config/manifest.md) for all available types.
