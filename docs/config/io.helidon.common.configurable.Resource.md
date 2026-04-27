# io.helidon.common.configurable.Resource

## Description

Configuration of a resource

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Resource is located on filesystem</td>
</tr>
<tr>
<td>
<code>proxy-port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">80</code>
</td>
<td>Port of the proxy when using URI</td>
</tr>
<tr>
<td>
<code>resource-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Resource is located on classpath</td>
</tr>
<tr>
<td>
<code>use-proxy</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to use proxy</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Description of this resource when configured through plain text or binary</td>
</tr>
<tr>
<td>
<code>proxy-host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Host of the proxy when using URI</td>
</tr>
<tr>
<td>
<code>uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>Resource is available on a <code>java.net.URI</code></td>
</tr>
<tr>
<td>
<code>content</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Binary content of the resource (base64 encoded)</td>
</tr>
<tr>
<td>
<code>content-plain</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Plain content of the resource (text)</td>
</tr>
</tbody>
</table>



## Usages

- [`clients.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`clients.tls.private-key.pem.cert-chain.resource`](io.helidon.clients.tls.privateKey.pem.CertChainConfig.md#resource)
- [`clients.tls.private-key.pem.certificates.resource`](io.helidon.clients.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`clients.tls.private-key.pem.key.resource`](io.helidon.clients.tls.privateKey.pem.KeyConfig.md#resource)
- [`clients.tls.private-key.pem.public-key.resource`](io.helidon.clients.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`clients.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`clients.tls.trust.pem.cert-chain.resource`](io.helidon.clients.tls.trust.pem.CertChainConfig.md#resource)
- [`clients.tls.trust.pem.certificates.resource`](io.helidon.clients.tls.trust.pem.CertificatesConfig.md#resource)
- [`clients.tls.trust.pem.key.resource`](io.helidon.clients.tls.trust.pem.KeyConfig.md#resource)
- [`clients.tls.trust.pem.public-key.resource`](io.helidon.clients.tls.trust.pem.PublicKeyConfig.md#resource)
- [`helidon.oci.authentication.config.private-key`](io.helidon.integrations.oci.ConfigMethodConfig.md#private-key)
- [`security.providers.idcs-role-mapper.oidc-config.decryption-keys.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.DecryptionKeysConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.oidc-metadata.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.OidcMetadataConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.sign-jwk.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.SignJwkConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.tenants.decryption-keys.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.DecryptionKeysConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.tenants.oidc-metadata.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.OidcMetadataConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.tenants.sign-jwk.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.SignJwkConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.cert-chain.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertChainConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.certificates.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.key.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.KeyConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.public-key.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.cert-chain.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertChainConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.certificates.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertificatesConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.key.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.KeyConfig.md#resource)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.public-key.resource`](io.helidon.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.PublicKeyConfig.md#resource)
- [`security.providers.jwt.atn-token.jwk.resource`](io.helidon.security.providers.jwt.atnToken.JwkConfig.md#resource)
- [`security.providers.jwt.sign-token.jwk.resource`](io.helidon.security.providers.jwt.signToken.JwkConfig.md#resource)
- [`security.providers.oidc.tenants.decryption-keys.resource`](io.helidon.security.providers.oidc.tenants.DecryptionKeysConfig.md#resource)
- [`security.providers.oidc.tenants.oidc-metadata.resource`](io.helidon.security.providers.oidc.tenants.OidcMetadataConfig.md#resource)
- [`security.providers.oidc.tenants.sign-jwk.resource`](io.helidon.security.providers.oidc.tenants.SignJwkConfig.md#resource)
- [`security.providers.oidc.webclient.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`security.providers.oidc.webclient.tls.private-key.pem.cert-chain.resource`](io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.CertChainConfig.md#resource)
- [`security.providers.oidc.webclient.tls.private-key.pem.certificates.resource`](io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`security.providers.oidc.webclient.tls.private-key.pem.key.resource`](io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.KeyConfig.md#resource)
- [`security.providers.oidc.webclient.tls.private-key.pem.public-key.resource`](io.helidon.security.providers.oidc.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`security.providers.oidc.webclient.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`security.providers.oidc.webclient.tls.trust.pem.cert-chain.resource`](io.helidon.security.providers.oidc.webclient.tls.trust.pem.CertChainConfig.md#resource)
- [`security.providers.oidc.webclient.tls.trust.pem.certificates.resource`](io.helidon.security.providers.oidc.webclient.tls.trust.pem.CertificatesConfig.md#resource)
- [`security.providers.oidc.webclient.tls.trust.pem.key.resource`](io.helidon.security.providers.oidc.webclient.tls.trust.pem.KeyConfig.md#resource)
- [`security.providers.oidc.webclient.tls.trust.pem.public-key.resource`](io.helidon.security.providers.oidc.webclient.tls.trust.pem.PublicKeyConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.decryption-keys.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.DecryptionKeysConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.oidc-metadata.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.OidcMetadataConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.sign-jwk.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.SignJwkConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.tenants.decryption-keys.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.DecryptionKeysConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.tenants.oidc-metadata.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.OidcMetadataConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.tenants.sign-jwk.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.SignJwkConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.cert-chain.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertChainConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.certificates.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.key.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.KeyConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem.public-key.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.cert-chain.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertChainConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.certificates.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.CertificatesConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.key.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.KeyConfig.md#resource)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem.public-key.resource`](io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.webclient.tls.trust.pem.PublicKeyConfig.md#resource)
- [`server.features.security.security.providers.jwt.atn-token.jwk.resource`](io.helidon.server.features.security.security.providers.jwt.atnToken.JwkConfig.md#resource)
- [`server.features.security.security.providers.jwt.sign-token.jwk.resource`](io.helidon.server.features.security.security.providers.jwt.signToken.JwkConfig.md#resource)
- [`server.features.security.security.providers.oidc.tenants.decryption-keys.resource`](io.helidon.server.features.security.security.providers.oidc.tenants.DecryptionKeysConfig.md#resource)
- [`server.features.security.security.providers.oidc.tenants.oidc-metadata.resource`](io.helidon.server.features.security.security.providers.oidc.tenants.OidcMetadataConfig.md#resource)
- [`server.features.security.security.providers.oidc.tenants.sign-jwk.resource`](io.helidon.server.features.security.security.providers.oidc.tenants.SignJwkConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.pem.cert-chain.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.CertChainConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.pem.certificates.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.pem.key.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.KeyConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.pem.public-key.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.pem.cert-chain.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.CertChainConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.pem.certificates.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.CertificatesConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.pem.key.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.KeyConfig.md#resource)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.pem.public-key.resource`](io.helidon.server.features.security.security.providers.oidc.webclient.tls.trust.pem.PublicKeyConfig.md#resource)
- [`server.sockets.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.sockets.tls.private-key.pem.cert-chain.resource`](io.helidon.server.sockets.tls.privateKey.pem.CertChainConfig.md#resource)
- [`server.sockets.tls.private-key.pem.certificates.resource`](io.helidon.server.sockets.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`server.sockets.tls.private-key.pem.key.resource`](io.helidon.server.sockets.tls.privateKey.pem.KeyConfig.md#resource)
- [`server.sockets.tls.private-key.pem.public-key.resource`](io.helidon.server.sockets.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`server.sockets.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.sockets.tls.trust.pem.cert-chain.resource`](io.helidon.server.sockets.tls.trust.pem.CertChainConfig.md#resource)
- [`server.sockets.tls.trust.pem.certificates.resource`](io.helidon.server.sockets.tls.trust.pem.CertificatesConfig.md#resource)
- [`server.sockets.tls.trust.pem.key.resource`](io.helidon.server.sockets.tls.trust.pem.KeyConfig.md#resource)
- [`server.sockets.tls.trust.pem.public-key.resource`](io.helidon.server.sockets.tls.trust.pem.PublicKeyConfig.md#resource)
- [`server.tls.private-key.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.tls.private-key.pem.cert-chain.resource`](io.helidon.server.tls.privateKey.pem.CertChainConfig.md#resource)
- [`server.tls.private-key.pem.certificates.resource`](io.helidon.server.tls.privateKey.pem.CertificatesConfig.md#resource)
- [`server.tls.private-key.pem.key.resource`](io.helidon.server.tls.privateKey.pem.KeyConfig.md#resource)
- [`server.tls.private-key.pem.public-key.resource`](io.helidon.server.tls.privateKey.pem.PublicKeyConfig.md#resource)
- [`server.tls.trust.keystore.resource`](io.helidon.common.pki.KeystoreKeys.md#resource)
- [`server.tls.trust.pem.cert-chain.resource`](io.helidon.server.tls.trust.pem.CertChainConfig.md#resource)
- [`server.tls.trust.pem.certificates.resource`](io.helidon.server.tls.trust.pem.CertificatesConfig.md#resource)
- [`server.tls.trust.pem.key.resource`](io.helidon.server.tls.trust.pem.KeyConfig.md#resource)
- [`server.tls.trust.pem.public-key.resource`](io.helidon.server.tls.trust.pem.PublicKeyConfig.md#resource)
- [`tracing.client-cert-pem`](io.helidon.TracingConfig.md#client-cert-pem)
- [`tracing.private-key-pem`](io.helidon.TracingConfig.md#private-key-pem)
- [`tracing.trusted-cert-pem`](io.helidon.TracingConfig.md#trusted-cert-pem)

---

See the [manifest](manifest.md) for all available types.
