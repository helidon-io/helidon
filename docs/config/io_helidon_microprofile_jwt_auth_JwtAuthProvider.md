# io.helidon.microprofile.jwt.auth.JwtAuthProvider

## Description

MP-JWT Auth configuration is defined by the spec (options prefixed with \`mp.jwt.\`), and we add a few configuration options for the security provider (options prefixed with \`security.providers.mp-jwt-auth.\`).

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a60991-mp-jwt-decrypt-key-algorithm"></span> [`mp.jwt.decrypt.key.algorithm`](../config/io_helidon_microprofile_jwt_auth_JwtAuthProviderMp_jwt_decrypt_key_algorithm.md) | `VALUE` | `i.h.m.j.a.J.j.d.k.algorithm` |   | Expected key management algorithm supported by the MP JWT endpoint |
| <span id="a8604c-mp-jwt-decrypt-key-location"></span> `mp.jwt.decrypt.key.location` | `VALUE` | `String` |   | Private key for decryption of encrypted claims |
| <span id="a703a3-mp-jwt-token-cookie"></span> `mp.jwt.token.cookie` | `VALUE` | `String` | `Bearer` | Specific cookie property name where we should search for JWT property |
| <span id="a2f093-mp-jwt-token-header"></span> `mp.jwt.token.header` | `VALUE` | `String` | `Authorization` | Name of the header expected to contain the token |
| <span id="a29ce2-mp-jwt-verify-audiences"></span> `mp.jwt.verify.audiences` | `LIST` | `String` |   | Expected audiences of incoming tokens |
| <span id="a72f1e-mp-jwt-verify-clock-skew"></span> `mp.jwt.verify.clock.skew` | `VALUE` | `Integer` | `5` | Clock skew to be accounted for in token expiration and max age validations in seconds |
| <span id="aaa9e2-mp-jwt-verify-issuer"></span> `mp.jwt.verify.issuer` | `VALUE` | `String` |   | Expected issuer in incoming requests |
| <span id="a5201e-mp-jwt-verify-publickey"></span> `mp.jwt.verify.publickey` | `VALUE` | `String` |   | String representation of the public key |
| <span id="a1fdff-mp-jwt-verify-publickey-location"></span> `mp.jwt.verify.publickey.location` | `VALUE` | `String` |   | Path to public key |
| <span id="abc722-mp-jwt-verify-token-age"></span> `mp.jwt.verify.token.age` | `VALUE` | `Integer` |   | Maximal expected token age in seconds |
| <span id="ab6b52-security-providers-mp-jwt-auth-allow-impersonation"></span> `security.providers.mp-jwt-auth.allow-impersonation` | `VALUE` | `Boolean` | `false` | Whether to allow impersonation by explicitly overriding username from outbound requests using `io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID` property |
| <span id="ad75f3-security-providers-mp-jwt-auth-atn-token-default-key-id"></span> `security.providers.mp-jwt-auth.atn-token.default-key-id` | `VALUE` | `String` |   | Default JWT key ID which should be used |
| <span id="a18931-security-providers-mp-jwt-auth-atn-token-handler"></span> [`security.providers.mp-jwt-auth.atn-token.handler`](../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to extract username from request |
| <span id="a1127b-security-providers-mp-jwt-auth-atn-token-jwk-resource"></span> [`security.providers.mp-jwt-auth.atn-token.jwk.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | JWK resource for authenticating the request |
| <span id="a07466-security-providers-mp-jwt-auth-atn-token-jwt-audience"></span> `security.providers.mp-jwt-auth.atn-token.jwt-audience` | `VALUE` | `String` |   | Audience expected in inbound JWTs |
| <span id="adc6c6-security-providers-mp-jwt-auth-atn-token-verify-key"></span> `security.providers.mp-jwt-auth.atn-token.verify-key` | `VALUE` | `String` |   | Path to public key |
| <span id="a3d7f3-security-providers-mp-jwt-auth-authenticate"></span> `security.providers.mp-jwt-auth.authenticate` | `VALUE` | `Boolean` | `true` | Whether to authenticate requests |
| <span id="af6ab6-security-providers-mp-jwt-auth-load-on-startup"></span> `security.providers.mp-jwt-auth.load-on-startup` | `VALUE` | `Boolean` | `false` | Whether to load JWK verification keys on server startup Default value is `false` |
| <span id="aed295-security-providers-mp-jwt-auth-optional"></span> `security.providers.mp-jwt-auth.optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="ace59e-security-providers-mp-jwt-auth-principal-type"></span> [`security.providers.mp-jwt-auth.principal-type`](../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a1f03b-security-providers-mp-jwt-auth-propagate"></span> `security.providers.mp-jwt-auth.propagate` | `VALUE` | `Boolean` | `true` | Whether to propagate identity |
| <span id="a09fd1-security-providers-mp-jwt-auth-sign-token"></span> [`security.providers.mp-jwt-auth.sign-token`](../config/io_helidon_security_providers_common_OutboundConfig.md) | `VALUE` | `i.h.s.p.c.OutboundConfig` |   | Configuration of outbound rules |

See the [manifest](../config/manifest.md) for all available types.
