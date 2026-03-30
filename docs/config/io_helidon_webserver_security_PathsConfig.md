# io.helidon.webserver.security.PathsConfig

## Description

Configuration of a single path security setup.

## Usages

- [`server.features.security.paths`](../config/io_helidon_webserver_security_SecurityFeature.md#accc93-paths)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="afdf21-audit"></span> `audit` | `VALUE` | `Boolean` | Whether to audit this request - defaults to false, if enabled, request is audited with event type "request" |
| <span id="a55838-audit-event-type"></span> `audit-event-type` | `VALUE` | `String` | Override for event-type, defaults to `SecurityHandler#DEFAULT_AUDIT_EVENT_TYPE` |
| <span id="a6a816-audit-message-format"></span> `audit-message-format` | `VALUE` | `String` | Override for audit message format, defaults to `SecurityHandler#DEFAULT_AUDIT_MESSAGE_FORMAT` |
| <span id="af770c-authenticate"></span> `authenticate` | `VALUE` | `Boolean` | If called, request will go through authentication process - defaults to false (even if authorize is true) |
| <span id="a67160-authentication-optional"></span> `authentication-optional` | `VALUE` | `Boolean` | If called, authentication failure will not abort request and will continue as anonymous (defaults to false) |
| <span id="a65dfc-authenticator"></span> `authenticator` | `VALUE` | `String` | Use a named authenticator (as supported by security - if not defined, default authenticator is used) |
| <span id="a14aae-authorize"></span> `authorize` | `VALUE` | `Boolean` | Enable authorization for this route |
| <span id="a4547f-authorizer"></span> `authorizer` | `VALUE` | `String` | Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is permitted) |
| <span id="a75a39-methods"></span> `methods` | `LIST` | `i.h.w.s.S.PathConfigCustomMethods` | HTTP methods to match when applying this configured path |
| <span id="aeaf0b-path"></span> `path` | `VALUE` | `String` | Path to secure |
| <span id="a68b5a-roles-allowed"></span> `roles-allowed` | `LIST` | `String` | An array of allowed roles for this path - must have a security provider supporting roles (either authentication or authorization provider) |
| <span id="a03d7c-sockets"></span> `sockets` | `LIST` | `String` | List of sockets this configuration should be applied to |

See the [manifest](../config/manifest.md) for all available types.
