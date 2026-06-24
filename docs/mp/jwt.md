# JWT

## Overview

JSON Web Tokens (JWT) are an open, industry standard [(RFC 7519)][rfc-7519]
method for representing claims securely between two parties.

JSON Web Token defines a compact and self-contained way for securely
transmitting information between parties as a JSON object. With JWT Auth you can
integrate security features such as single sign on into your Helidon MP
applications.

## Maven Coordinates

To enable JWT Authentication, either add a dependency on the
[helidon-microprofile bundle](introduction.md) or add the following dependency
to your project’s `pom.xml` (see [Managing
Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.jwt</groupId>
  <artifactId>helidon-microprofile-jwt-auth</artifactId>
</dependency>
```

## Usage

The main configuration point for JWT Auth is a JAX-RS Application class. As this
class is discovered using CDI, it must have a bean defining annotation.

Minimal required setup is done using `@LoginConfig(authMethod = "MP-JWT")`:

```java
@LoginConfig(authMethod = "MP-JWT")
@ApplicationScoped
public class ProtectedApplication extends Application {
}
```

## API

The following interfaces and annotations are used to work with JWT in Helidon
MP:

- `JsonWebToken` - an interface used in CDI beans *(@RequestScoped)* dependency
  injection to obtain the JWT of the currently executing caller.
- `@Claim` - an annotation used by CDI bean *(@RequestScoped)* dependency
  injection to obtain individual claims from the caller’s JWT.
- `ClaimValue` - a proxy interface used with `@Claim` annotation to obtain the
  value of a claim by calling `getValue()`.

## Configuration options

<!--@mdc ::table-collapse -->
<table>
<thead>
<tr>
<th>Value</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a href="#key-algorithm"><code>mp.<wbr>jwt.<wbr>decrypt.<wbr>key.<wbr>algorithm`</code></a></td>
<td><code>String</code></td>
<td></td>
<td>Expected key management algorithm supported by the MP JWT endpoint</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>decrypt.<wbr>key.<wbr>location</code></td>
<td><code>String</code></td>
<td></td>
<td>Private key for decryption of encrypted claims</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>token.<wbr>cookie</code></td>
<td><code>String</code></td>
<td><code>Bearer</code></td>
<td>Specific cookie property name where we should search for JWT property</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>token.<wbr>header</code></td>
<td><code>String</code></td>
<td><code>Authorization</code></td>
<td>Name of the header expected to contain the token</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>verify.<wbr>audiences</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Expected audiences of incoming tokens</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>verify.<wbr>clock.skew</code></td>
<td><code>Integer</code></td>
<td><code>5</code></td>
<td>Clock skew to be accounted for in token expiration and max age validations in seconds</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>verify.<wbr>issuer</code></td>
<td><code>String</code></td>
<td></td>
<td>Expected issuer in incoming requests</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>verify.<wbr>publickey</code></td>
<td><code>String</code></td>
<td></td>
<td>String representation of the public key</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>verify.<wbr>publickey.<wbr>location</code></td>
<td><code>String</code></td>
<td></td>
<td>Path to public key</td>
</tr>
<tr>
<td><code>mp.<wbr>jwt.<wbr>verify.<wbr>token.<wbr>age</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Maximal expected token age in seconds</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>allow-impersonation</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to allow impersonation by explicitly overriding username from outbound requests using <code>io.<wbr>helidon.<wbr>security.<wbr>EndpointConfig<wbr>#PROPERTY_<wbr>OUTBOUND_<wbr>ID</code>property</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>atn-token.<wbr>default-key-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Default JWT key ID which should be used</td>
</tr>
<tr>
<td><a href="../config/io.helidon.security.util.TokenHandler.md"><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>atn-token.<wbr>handler</code></a></td>
<td><code>TokenHandler</code></td>
<td></td>
<td>Token handler to extract username from request</td>
</tr>
<tr>
<td><a href="../config/io.helidon.common.configurable.Resource.md"><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>atn-token.jwk.<wbr>resource</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>JWK resource for authenticating the request</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>atn-token.<wbr>jwt-audience</code></td>
<td><code>String</code></td>
<td></td>
<td>Audience expected in inbound JWTs</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>atn-token.<wbr>verify-key</code></td>
<td><code>String</code></td>
<td></td>
<td>Path to public key</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>authenticate</code></td>
<td><code>Booleean</code></td>
<td><code>true</code></td>
<td>Whether to authenticate requests</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>load-on-startup</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to load JWK verification keys on server startup Default value is <code>false</code></td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>optional</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td><a href="../config/io.helidon.security.SubjectType.md"><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>principal-type</code></a></td>
<td><code>SubjectType</code></td>
<td><code>USER</code></td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>jwt-groups-path</code></td>
<td><code>String</code></td>
<td><code>groups</code></td>
<td>Path to the JWT payload claim containing groups to add as role grants. Nested object claims can be configured with slash-separated path segments, such as <code>realm/groups</code></td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>jwt-groups-separator</code></td>
<td><code>String</code></td>
<td></td>
<td>Separator used to split a string claim value into multiple groups. This is used only when <code>jwt-groups-path</code> is configured to a custom path other than <code>groups</code>; setting only <code>jwt-groups-separator</code> has no effect.</td>
</tr>
<tr>
<td><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>propagate</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td><a href="../config/io.helidon.security.providers.common.OutboundConfig.md"><code>security.<wbr>providers.<wbr>mp-jwt-auth.<wbr>sign-token</code></a></td>
<td><code>OutboundConfig</code></td>
<td></td>
<td>Configuration of outbound rules</td>
</tr>
</tbody>
</table>
<!--@mdc :: -->

A configuration example in `microprofile-config.properties`:

```properties [microprofile-config.properties]
mp.jwt.verify.issuer=https://{PublicIssuerDomain}/oauth2/default
mp.jwt.verify.publickey.location=${mp.jwt.verify.issuer}/v1/keys
```

### Key Algorithm

Allowed values for `mp.jwt.decrypt.key.algorithm`:

| Value        | Description             |
|--------------|-------------------------|
| RSA-OAEP     | 	RSA-OAEP Algorithm     |
| RSA-OAEP-256 | 	RSA-OAEP-256 Algorithm |

## Examples

```java
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

```java
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

```properties [microprofile-config.properties]
mp.jwt.verify.issuer=https://{IssuerPublicDomain}/oauth2/default
mp.jwt.verify.publickey.location=${mp.jwt.verify.issuer}/v1/keys
```

Obtain the Security Token from external issuer:

```shell [Terminal]
TOKEN=sdf4dDSWFcswdsffDSasEgv...
```

Run the application and execute an http request against it:

```shell [Terminal]
curl -X GET -I -H "Authorization: Bearer $TOKEN" http://localhost:8080/hello
```

```log [Output]
HTTP/1.1 200 OK
Date: 08.06.2022 10:33:47 EEST
connection: keep-alive
content-length: 28

Hello, secure@helidon.io!
```

Which means that the request successfully passed authentication.

## Additional Information

Learn more about JWT authentication at:  
[Eclipse MicroProfile Interoperable JWT RBAC][eclipse-micropro]

## Reference

- [MicroProfile JWT Auth Spec][microprofile-jwt]
- [MicroProfile JWT Auth GitHub Repository][microprofile-jwt-2]

[rfc-7519]: https://datatracker.ietf.org/doc/html/rfc7519
[eclipse-micropro]: https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html#_introduction
[microprofile-jwt]: https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html
[microprofile-jwt-2]: https://github.com/eclipse/microprofile-jwt-auth
