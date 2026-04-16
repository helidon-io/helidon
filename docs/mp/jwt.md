# JWT Authentication

## Overview

JSON Web Tokens (JWT) are an open, industry standard [(RFC 7519)](https://datatracker.ietf.org/doc/html/rfc7519) method for representing claims securely between two parties.

JSON Web Token defines a compact and self-contained way for securely transmitting information between parties as a JSON object. With JWT Auth you can integrate security features such as single sign on into your Helidon MP applications.

## Maven Coordinates

To enable JWT Authentication, either add a dependency on the [helidon-microprofile bundle](../mp/introduction/microprofile.md) or add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.jwt</groupId>
    <artifactId>helidon-microprofile-jwt-auth</artifactId>
</dependency>
```

## Usage

The main configuration point for JWT Auth is a JAX-RS Application class. As this class is discovered using CDI, it must have a bean defining annotation.

Minimal required setup is done using `@LoginConfig(authMethod = "MP-JWT")`:

``` java
@LoginConfig(authMethod = "MP-JWT")
@ApplicationScoped
public class ProtectedApplication extends Application {
}
```

## API

The following interfaces and annotations are used to work with JWT in Helidon MP:

- `JsonWebToken` - an interface used in CDI beans *(@RequestScoped)* dependency injection to obtain the JWT of the currently executing caller.
- `@Claim` - an annotation used by CDI bean *(@RequestScoped)* dependency injection to obtain individual claims from the caller’s JWT.
- `ClaimValue` - a proxy interface used with `@Claim` annotation to оbtain the value of a claim by calling `getValue()`.

## Configuration

### Configuration options

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

A configuration example in `microprofile-config.properties`:

``` properties
mp.jwt.verify.issuer=https://{PublicIssuerDomain}/oauth2/default
mp.jwt.verify.publickey.location=${mp.jwt.verify.issuer}/v1/keys
```

## Examples

``` java
@Path("/hello")
public class HelloResource {

    @GET
    @Produces(TEXT_PLAIN)
    public String hello(@Context SecurityContext context) {
        Optional<Principal> userPrincipal = context.userPrincipal();
        return "Hello, " + userPrincipal.get().getName() + "!";
    }
}
```

Do not forget to annotate the `HelloApplication` class to enable JWT:

``` java
@LoginConfig(authMethod = "MP-JWT")
@ApplicationScoped
public class HelloApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(HelloResource.class);
    }
}
```

Add the following configuration in `microprofile-config.properties`:

``` properties
mp.jwt.verify.issuer=https://{IssuerPublicDomain}/oauth2/default
mp.jwt.verify.publickey.location=${mp.jwt.verify.issuer}/v1/keys
```

Obtain the Security Token from external issuer:

``` bash
TOKEN=sdf4dDSWFcswdsffDSasEgv...
```

Run the application and execute an http request against it:

``` bash
curl -X GET -I -H "Authorization: Bearer $TOKEN" http://localhost:8080/hello
```

*Curl output*

``` bash
HTTP/1.1 200 OK
Date: 08.06.2022 10:33:47 EEST
connection: keep-alive
content-length: 28

Hello, secure@helidon.io!
```

which means that the request successfully passed authentication.

## Additional Information

Learn more about JWT authentication at:  
[Eclipse MicroProfile Interoperable JWT RBAC](https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1#_introduction)

## Reference

- [MicroProfile JWT Auth Spec](https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html)
- [MicroProfile JWT Auth GitHub Repository](https://github.com/eclipse/microprofile-jwt-auth)
