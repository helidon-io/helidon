# io.helidon.microprofile.jwt.auth.JwtAuthProvider

## Description

MP-JWT Auth configuration is defined by the spec (options prefixed with &#x60;mp.jwt.&#x60;), and we add a few configuration options for the security provider (options prefixed with &#x60;security.providers.mp-jwt-auth.&#x60;)

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>mp.jwt.verify.publickey</code></td>
<td><code>String</code></td>
<td></td>
<td>String representation of the public key</td>
</tr>
<tr>
<td><code>mp.jwt.decrypt.key.location</code></td>
<td><code>String</code></td>
<td></td>
<td>Private key for decryption of encrypted claims</td>
</tr>
<tr>
<td><code>mp.jwt.verify.clock.skew</code></td>
<td><code>Integer</code></td>
<td><code>5</code></td>
<td>Clock skew to be accounted for in token expiration and max age validations in seconds</td>
</tr>
<tr>
<td><a id="mp-jwt-decrypt-key-algorithm"></a><a href="io.helidon.microprofile.jwt.auth.JwtAuthProviderMp.jwt.decrypt.key.algorithm.md"><code>mp.jwt.decrypt.key.algorithm</code></a></td>
<td><code>algorithm</code></td>
<td></td>
<td>Expected key management algorithm supported by the MP JWT endpoint</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.propagate</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td><code>mp.jwt.token.cookie</code></td>
<td><code>String</code></td>
<td><code>Bearer</code></td>
<td>Specific cookie property name where we should search for JWT property</td>
</tr>
<tr>
<td><code>mp.jwt.verify.audiences</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Expected audiences of incoming tokens</td>
</tr>
<tr>
<td><a id="security-providers-mp-jwt-auth-atn-token-jwk-resource"></a><a href="io.helidon.common.configurable.Resource.md"><code>security.providers.mp-jwt-auth.atn-token.jwk.resource</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>JWK resource for authenticating the request</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.atn-token.jwt-audience</code></td>
<td><code>String</code></td>
<td></td>
<td>Audience expected in inbound JWTs</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.load-on-startup</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to load JWK verification keys on server startup Default value is &lt;code&gt;false&lt;/code&gt;</td>
</tr>
<tr>
<td><code>mp.jwt.verify.publickey.location</code></td>
<td><code>String</code></td>
<td></td>
<td>Path to public key</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.atn-token.default-key-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Default JWT key ID which should be used</td>
</tr>
<tr>
<td><a id="security-providers-mp-jwt-auth-sign-token"></a><a href="io.helidon.security.providers.common.OutboundConfig.md"><code>security.providers.mp-jwt-auth.sign-token</code></a></td>
<td><code>OutboundConfig</code></td>
<td></td>
<td>Configuration of outbound rules</td>
</tr>
<tr>
<td><code>mp.jwt.verify.issuer</code></td>
<td><code>String</code></td>
<td></td>
<td>Expected issuer in incoming requests</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.atn-token.verify-key</code></td>
<td><code>String</code></td>
<td></td>
<td>Path to public key</td>
</tr>
<tr>
<td><a id="security-providers-mp-jwt-auth-atn-token-handler"></a><a href="io.helidon.security.util.TokenHandler.md"><code>security.providers.mp-jwt-auth.atn-token.handler</code></a></td>
<td><code>TokenHandler</code></td>
<td></td>
<td>Token handler to extract username from request</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.allow-impersonation</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to allow impersonation by explicitly overriding username from outbound requests using &lt;code&gt;io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID&lt;/code&gt; property</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.authenticate</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to authenticate requests</td>
</tr>
<tr>
<td><code>mp.jwt.token.header</code></td>
<td><code>String</code></td>
<td><code>Authorization</code></td>
<td>Name of the header expected to contain the token</td>
</tr>
<tr>
<td><code>mp.jwt.verify.token.age</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Maximal expected token age in seconds</td>
</tr>
<tr>
<td><a id="security-providers-mp-jwt-auth-principal-type"></a><a href="io.helidon.security.SubjectType.md"><code>security.providers.mp-jwt-auth.principal-type</code></a></td>
<td><code>SubjectType</code></td>
<td><code>USER</code></td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td><code>security.providers.mp-jwt-auth.optional</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether authentication is required</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
