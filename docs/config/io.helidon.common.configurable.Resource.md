# io.<wbr>helidon.<wbr>common.<wbr>configurable.<wbr>Resource

## Description

Configuration of a resource

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>path</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>Resource is located on filesystem</td>
</tr>
<tr>
<td>
<code>proxy-<wbr>port</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>80</code>
</td>
<td>Port of the proxy when using URI</td>
</tr>
<tr>
<td>
<code>resource-<wbr>path</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Resource is located on classpath</td>
</tr>
<tr>
<td>
<code>use-<wbr>proxy</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to use proxy</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Description of this resource when configured through plain text or binary</td>
</tr>
<tr>
<td>
<code>proxy-<wbr>host</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Host of the proxy when using URI</td>
</tr>
<tr>
<td>
<code>uri</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>Resource is available on a <code>java.<wbr>net.<wbr>URI</code></td>
</tr>
<tr>
<td>
<code>content</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Binary content of the resource (base64 encoded)</td>
</tr>
<tr>
<td>
<code>content-<wbr>plain</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Plain content of the resource (text)</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>clients.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.privateKey.pem.CertChainConfig.md#resource"><code>clients.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.privateKey.pem.CertificatesConfig.md#resource"><code>clients.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.privateKey.pem.KeyConfig.md#resource"><code>clients.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>clients.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>clients.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.trust.pem.CertChainConfig.md#resource"><code>clients.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.trust.pem.CertificatesConfig.md#resource"><code>clients.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.trust.pem.KeyConfig.md#resource"><code>clients.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.clients.tls.trust.pem.PublicKeyConfig.md#resource"><code>clients.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.integrations.oci.ConfigMethodConfig.md#private-key"><code>helidon.<wbr>oci.<wbr>authentication.<wbr>config.<wbr>private-<wbr>key</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.DecryptionKeysConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>decryption-<wbr>keys.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.OidcMetadataConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>oidc-<wbr>metadata.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.SignJwkConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>sign-<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.DecryptionKeysConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>decryption-<wbr>keys.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.OidcMetadataConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>oidc-<wbr>metadata.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.SignJwkConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>sign-<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertChainConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertificatesConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.KeyConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertChainConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertificatesConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.KeyConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.PublicKeyConfig.md#resource"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.jwt.atnToken.JwkConfig.md#resource"><code>security.<wbr>providers.<wbr>jwt.<wbr>atn-<wbr>token.<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.jwt.signToken.JwkConfig.md#resource"><code>security.<wbr>providers.<wbr>jwt.<wbr>sign-<wbr>token.<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.tenants.DecryptionKeysConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>decryption-<wbr>keys.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.tenants.OidcMetadataConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>oidc-<wbr>metadata.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.tenants.SignJwkConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>sign-<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.CertChainConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.CertificatesConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.KeyConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.trust.pem.CertChainConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.trust.pem.CertificatesConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.trust.pem.KeyConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.security.providers.oidc.webclient.tls.trust.pem.PublicKeyConfig.md#resource"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.DecryptionKeysConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>decryption-<wbr>keys.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.OidcMetadataConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>oidc-<wbr>metadata.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.SignJwkConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>sign-<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.DecryptionKeysConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>decryption-<wbr>keys.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.OidcMetadataConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>oidc-<wbr>metadata.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.SignJwkConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>sign-<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertChainConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertificatesConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.KeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertChainConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertificatesConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.KeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.PublicKeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.jwt.atnToken.JwkConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>atn-<wbr>token.<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.jwt.signToken.JwkConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>sign-<wbr>token.<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.tenants.DecryptionKeysConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>decryption-<wbr>keys.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.tenants.OidcMetadataConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>oidc-<wbr>metadata.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.tenants.SignJwkConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>sign-<wbr>jwk.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.CertChainConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.CertificatesConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.KeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.CertChainConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.CertificatesConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.KeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.PublicKeyConfig.md#resource"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.privateKey.pem.CertChainConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.privateKey.pem.CertificatesConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.privateKey.pem.KeyConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.trust.pem.CertChainConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.trust.pem.CertificatesConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.trust.pem.KeyConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.sockets.tls.trust.pem.PublicKeyConfig.md#resource"><code>server.<wbr>sockets.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.privateKey.pem.CertChainConfig.md#resource"><code>server.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.privateKey.pem.CertificatesConfig.md#resource"><code>server.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.privateKey.pem.KeyConfig.md#resource"><code>server.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.privateKey.pem.PublicKeyConfig.md#resource"><code>server.<wbr>tls.<wbr>private-<wbr>key.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.common.pki.KeystoreKeys.md#resource"><code>server.<wbr>tls.<wbr>trust.<wbr>keystore.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.trust.pem.CertChainConfig.md#resource"><code>server.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>cert-<wbr>chain.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.trust.pem.CertificatesConfig.md#resource"><code>server.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>certificates.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.trust.pem.KeyConfig.md#resource"><code>server.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.server.tls.trust.pem.PublicKeyConfig.md#resource"><code>server.<wbr>tls.<wbr>trust.<wbr>pem.<wbr>public-<wbr>key.<wbr>resource</code></a>
- <a href="io.helidon.TracingConfig.md#client-cert-pem"><code>tracing.<wbr>client-<wbr>cert-<wbr>pem</code></a>
- <a href="io.helidon.TracingConfig.md#private-key-pem"><code>tracing.<wbr>private-<wbr>key-<wbr>pem</code></a>
- <a href="io.helidon.TracingConfig.md#trusted-cert-pem"><code>tracing.<wbr>trusted-<wbr>cert-<wbr>pem</code></a>

---

See the [manifest](manifest.md) for all available types.
