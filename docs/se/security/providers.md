# Security Providers

## Contents

- [Implemented Security Providers](#implemented-security-providers)
- [Maven Coordinates](#maven-coordinates)
- [Reference](#reference)

## Implemented Security Providers

Helidon provides the following security providers for endpoint protection:

| Provider | Type | Outbound supported | Description |
|----|----|----|----|
| [OIDC Provider](#oidc-provider) | Authentication | ✅ | Open ID Connect supporting JWT, Scopes, Groups and OIDC code flow |
| [HTTP Basic Authentication](#http-basic-authentication-provider) | Authentication | ✅ | HTTP Basic Authentication support |
| [HTTP Digest Authentication](#http-digest-authentication-provider) | Authentication | 🚫 | HTTP Digest Authentication support |
| [Header Assertion](#header-authentication-provider) | Authentication | ✅ | Asserting a user based on a header value |
| [HTTP Signatures](#http-signatures-provider) | Authentication | ✅ | Protecting service to service communication through signatures |
| [IDCS Roles](#idcs-role-mapper) | Role Mapping | 🚫 | Retrieves roles from IDCS provider for authenticated user |
| [ABAC Authorization](#abac-provider) | Authorization | 🚫 | Attribute based access control authorization policies |

The following providers are no longer evolved:

| Provider | Type | Outbound supported | Description |
|----|----|----|----|
| [Google Login](#google-login-provider) | Authentication | ✅ | **Deprecated**! Authenticates a token from request against Google servers |
| [JWT Provider](#jwt-provider) | Authentication | ✅ | JWT tokens passed from frontend |

## OIDC Provider

Open ID Connect security provider.

### Maven Coordinates

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-oidc</artifactId>
</dependency>
```

#### Overview

In Helidon SE, we need to register the redirection support with routing (in addition to `SecurityFeature` that integrates with `WebServer`). This is not required when `redirect` is set to false.

Adding support for OIDC redirects

``` java
WebServer.builder()
        .addFeature(SecurityFeature.builder()
                            .config(config.get("security"))
                            .build())
        .routing(r -> r.addFeature(OidcFeature.create(config)))
        .build();
```

#### Configuration options

<table class="tableblock frame-all grid-all stretch">
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr>
<th class="tableblock halign-left valign-top">Key</th>
<th class="tableblock halign-left valign-top">Kind</th>
<th class="tableblock halign-left valign-top">Type</th>
<th class="tableblock halign-left valign-top">Default Value</th>
<th class="tableblock halign-left valign-top">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3a883-access-token-ip-check"></span> <code>access-token-ip-check</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to check if current IP address matches the one access token was issued for</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a7e9b5-client-credentials-config"></span> <a href="../../se/security/../../config/io_helidon_security_providers_oidc_common_ClientCredentialsConfig.html"><code>client-credentials-config</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.ClientCredentialsConfig</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Set the configuration related to the client credentials flow</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a9b107-cookie-domain"></span> <code>cookie-domain</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Domain the cookie is valid for</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a593cb-cookie-encryption-enabled"></span> <code>cookie-encryption-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt token cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aaabc9-cookie-encryption-id-enabled"></span> <code>cookie-encryption-id-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt id token cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab14ce-cookie-encryption-name"></span> <code>cookie-encryption-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top">Name of the encryption configuration available through
Security#encrypt(String, byte[)&lt;/code&gt; and &lt;code&gt;Security#decrypt(String, String)&lt;/code&gt;]</td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ac0d77-cookie-encryption-password"></span> <code>cookie-encryption-password</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>LIST</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Master password for encryption/decryption of cookies</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad8fc7-cookie-encryption-refresh-enabled"></span> <code>cookie-encryption-refresh-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt refresh token cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5da71-cookie-encryption-state-enabled"></span> <code>cookie-encryption-state-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt state cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a16963-cookie-encryption-tenant-enabled"></span> <code>cookie-encryption-tenant-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt tenant name cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad2ea9-cookie-http-only"></span> <code>cookie-http-only</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, if set to true, the HttpOnly attribute will be configured</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ae7d0f-cookie-max-age-seconds"></span> <code>cookie-max-age-seconds</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Long</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, used to set MaxAge attribute of the cookie, defining how long the cookie is valid</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a423f0-cookie-name"></span> <code>cookie-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of the cookie to use</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a47aa0-cookie-name-id-token"></span> <code>cookie-name-id-token</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID_2</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of the cookie to use for id token</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="afcd78-cookie-name-refresh-token"></span> <code>cookie-name-refresh-token</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID_3</code></p></td>
<td class="tableblock halign-left valign-top"><p>The name of the cookie to use for the refresh token</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3e5f9-cookie-name-state"></span> <code>cookie-name-state</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID_3</code></p></td>
<td class="tableblock halign-left valign-top"><p>The name of the cookie to use for the state storage</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="afcf69-cookie-name-tenant"></span> <code>cookie-name-tenant</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>HELIDON_TENANT</code></p></td>
<td class="tableblock halign-left valign-top"><p>The name of the cookie to use for the tenant name</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ae5841-cookie-path"></span> <code>cookie-path</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>/</code></p></td>
<td class="tableblock halign-left valign-top"><p>Path the cookie is valid for</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0a656-cookie-same-site"></span> <a href="../../se/security/../../config/io_helidon_http_SetCookie_SameSite.html"><code>cookie-same-site</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.h.S.SameSite</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>LAX</code></p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, used to set the SameSite cookie value</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0f553-cookie-secure"></span> <code>cookie-secure</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, if set to true, the Secure attribute will be configured</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a70aa3-cookie-use"></span> <code>cookie-use</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to use cookie to store JWT between requests</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad1309-cors"></span> <a href="../../se/security/../../config/io_helidon_cors_CrossOriginConfig.html"><code>cors</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.c.CrossOriginConfig</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Assign cross-origin resource sharing settings</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="afd33a-force-https-redirects"></span> <code>force-https-redirects</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Force HTTPS for redirects to identity provider</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a069b5-frontend-uri"></span> <code>frontend-uri</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Full URI of this application that is visible from user browser</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="abf3fb-header-token"></span> <a href="../../se/security/../../config/io_helidon_security_util_TokenHandler.html"><code>header-token</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.u.TokenHandler</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>A <code>TokenHandler</code> to process header containing a JWT</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a866f7-header-use"></span> <code>header-use</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to expect JWT in a header field</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5a4ae-id-token-signature-validation"></span> <code>id-token-signature-validation</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether id token signature check should be enabled</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab5728-max-redirects"></span> <code>max-redirects</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Integer</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>5</code></p></td>
<td class="tableblock halign-left valign-top"><p>Configure maximal number of redirects when redirecting to an OIDC provider within a single authentication attempt</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab32e8-optional"></span> <code>optional</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether authentication is required</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="acf040-outbound"></span> <a href="../../se/security/../../config/io_helidon_security_providers_common_OutboundTarget.html"><code>outbound</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>LIST</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.c.OutboundTarget</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Add a new target configuration</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aebe14-outbound-type"></span> <a href="../../se/security/../../config/io_helidon_security_providers_oidc_common_OidcOutboundType.html"><code>outbound-type</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.OidcOutboundType</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>USER_JWT</code></p></td>
<td class="tableblock halign-left valign-top"><p>Type of the OIDC outbound</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a6ccfb-pkce-challenge-method"></span> <a href="../../se/security/../../config/io_helidon_security_providers_oidc_common_PkceChallengeMethod.html"><code>pkce-challenge-method</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.PkceChallengeMethod</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>S256</code></p></td>
<td class="tableblock halign-left valign-top"><p>Proof Key Code Exchange (PKCE) challenge creation method</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a4050c-pkce-enabled"></span> <code>pkce-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether this provider should support PKCE</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad84e8-propagate"></span> <code>propagate</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to propagate identity</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a03394-proxy-port"></span> <code>proxy-port</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Integer</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>80</code></p></td>
<td class="tableblock halign-left valign-top"><p>Proxy port</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aa5960-query-id-token-param-name"></span> <code>query-id-token-param-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>id_token</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of a query parameter that contains the JWT id token when parameter is used</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a4e9c0-query-param-name"></span> <code>query-param-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>accessToken</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of a query parameter that contains the JWT access token when parameter is used</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0b01e-query-param-tenant-name"></span> <code>query-param-tenant-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>h_tenant</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of a query parameter that contains the tenant name when the parameter is used</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3d4be-query-param-use"></span> <code>query-param-use</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to use a query parameter to send JWT token from application to this server</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aa4ba9-redirect"></span> <code>redirect</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>By default, the client should redirect to the identity server for the user to log in</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="adf51c-redirect-attempt-param"></span> <code>redirect-attempt-param</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>h_ra</code></p></td>
<td class="tableblock halign-left valign-top"><p>Configure the parameter used to store the number of attempts in redirect</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a21f20-redirect-uri"></span> <code>redirect-uri</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>/oidc/redirect</code></p></td>
<td class="tableblock halign-left valign-top"><p>URI to register web server component on, used by the OIDC server to redirect authorization requests to after a user logs in or approves scopes</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aef163-tenants"></span> <a href="../../se/security/../../config/io_helidon_security_providers_oidc_common_TenantConfig.html"><code>tenants</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.TenantConfig</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Configurations of the tenants</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5f4ac-token-signature-validation"></span> <code>token-signature-validation</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether access token signature check should be enabled</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a50324-use-jwt-groups"></span> <code>use-jwt-groups</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Claim <code>groups</code> from JWT will be used to automatically add groups to current subject (may be used with <code>jakarta.annotation.security.RolesAllowed</code> annotation)</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a85467-webclient"></span> <a href="../../se/security/../../config/io_helidon_webclient_api_WebClient.html"><code>webclient</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.w.a.WebClient</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>WebClient configuration used for outbound requests to the identity server. This configuration sets the values to the OIDC WebClient default configuration</p></td>
</tr>
</tbody>
</table>

##### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af9976-proxy-host"></span> `proxy-host` | `VALUE` | `String` |   | Proxy host to use |
| <span id="aa965f-proxy-protocol"></span> `proxy-protocol` | `VALUE` | `String` | `http` | Proxy protocol to use when proxy is used |
| <span id="abf0b4-relative-uris"></span> `relative-uris` | `VALUE` | `Boolean` | `false` | Can be set to `true` to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists |

### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/idcs-login) on GitHub.

Configuration example

``` yaml
security:
  providers:
  - oidc:
      client-id: "client-id-of-this-service"
      client-secret: "${CLEAR=changeit}"
      identity-uri: "https://your-tenant.identity-server.com"
      frontend-uri: "http://my-service:8080"
      audience: "http://my-service"
      outbound:
        - name: "internal-services"
          hosts: ["*.example.org"]
          outbound-token:
            header: "X-Internal-Auth"
```

### How does it work?

At Helidon startup, if OIDC provider is configured, the following will happen:

1.  `client-id`, `client-secret`, and `identityUri` are validated - these must provide values
2.  Unless all resources are configured as local resources, the provider attempts to contact the `oidc-metadata.resource` endpoint to retrieve all endpoints

At runtime, depending on configuration…​

If a request comes without a token or with insufficient scopes:

1.  If `redirect` is set to `true` (default), request is redirected to the authorization endpoint of the identity server. If set to false, `401` is returned
2.  User authenticates against the identity server
3.  The identity server redirects back to Helidon service with a code
4.  Helidon service contacts the identity server’s token endpoint, to exchange the code for a JWT
5.  The JWT is stored in a cookie (if cookie support is enabled, which it is by default)
6.  Helidon service redirects to original endpoint (on itself)

Helidon obtains a token from request (from cookie, header, or query parameter):

1.  Token is parsed as a singed JWT
2.  We validate the JWT signature either against local JWK or against the identity server’s introspection endpoint depending on configuration
3.  We validate the issuer and audience of the token if it matches the configured values
4.  A subject is created from the JWT, including scopes from the token
5.  We validate that we have sufficient scopes to proceed, and return `403` if not
6.  Handling is returned to security to process other security providers

### Multiple tenants

The OIDC provider also supports multiple tenants. To enable this feature, it is required to do several steps.

1.  To enable the default multi-tenant support, add the `multi-tenant: true` option to the OIDC provider configuration
2.  Specify the desired way to provide the tenant name. This step is done over adding the `tenant-id-style` configuration option. For more information, see the table below
3.  Add the tenants section to the OIDC provider configuration

``` yaml
tenants:
   - name: "example-tenant"
     # ... tenant configuration options
```

There are four ways to provide the required tenant information to Helidon by default.

<table class="tableblock frame-all grid-all stretch">
<caption>Table 1. Possible <code>tenant-id-style</code> configuration options</caption>
<colgroup>
<col style="width: 22%" />
<col style="width: 44%" />
<col style="width: 33%" />
</colgroup>
<thead>
<tr>
<th class="tableblock halign-left valign-top">key</th>
<th class="tableblock halign-left valign-top">description</th>
<th class="tableblock halign-left valign-top">additional config options</th>
</tr>
</thead>
<tbody>
<tr>
<td class="tableblock halign-left valign-top"><p><code>host-header</code></p></td>
<td class="tableblock halign-left valign-top"><p>Tenant configuration will be selected based on your host present in the <code>Host</code> header value.</p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><code>domain</code></p></td>
<td class="tableblock halign-left valign-top"><p>Similar to the <code>host-header</code> style, but now the tenant name is identified just as a part of the host name. By default, it selects the third domain level.</p>
<p>Example: Host header value from inbound request is <code>my.helidon.com</code> → domain level 3 is <code>my</code>, domain level 2 is <code>helidon</code> and domain level 1 is <code>com</code>.</p></td>
<td class="tableblock halign-left valign-top"><pre class="highlight"><code>tenant-id-domain-level: &lt;domain level&gt;</code></pre></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><code>token-handler</code></p></td>
<td class="tableblock halign-left valign-top"><p>The tenant name information is expected to be provided through the configured custom header value.</p></td>
<td class="tableblock halign-left valign-top"><pre class="highlight"><code>tenant-id-handler:
  header: &quot;my-custom-header&quot;</code></pre></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><code>none</code></p></td>
<td class="tableblock halign-left valign-top"><p>No tenant name finding is used. Default tenant name <code>@default</code> is used instead.</p></td>
<td class="tableblock halign-left valign-top"></td>
</tr>
</tbody>
</table>

You can also implement a custom way of discovering the tenant name and tenant configuration. The custom tenant name discovery from request can be done by implementing SPI:

`io.helidon.security.providers.oidc.common.spi.TenantIdProvider`

and the custom tenant configuration discovery can be provided by implementing SPI:

`io.helidon.security.providers.oidc.common.spi.TenantConfigProvider`

#### Available tenant config options

##### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a2e4f7-audience"></span> `audience` | `VALUE` | `String` |   | Audience of issued tokens |
| <span id="a37c39-authorization-endpoint-uri"></span> `authorization-endpoint-uri` | `VALUE` | `URI` |   | URI of an authorization endpoint used to redirect users to for logging-in |
| <span id="ad0521-base-scopes"></span> `base-scopes` | `VALUE` | `String` | `openid` | Configure base scopes |
| <span id="a7bcb9-check-audience"></span> `check-audience` | `VALUE` | `Boolean` | `true` | Configure audience claim check |
| <span id="a67ded-client-id"></span> `client-id` | `VALUE` | `String` |   | Client ID as generated by OIDC server |
| <span id="abd29e-client-secret"></span> `client-secret` | `VALUE` | `String` |   | Client secret as generated by OIDC server |
| <span id="a3942e-client-timeout-millis"></span> `client-timeout-millis` | `VALUE` | `Duration` | `30000` | Timeout of calls using web client |
| <span id="a989a6-decryption-keys-resource"></span> [`decryption-keys.resource`](../../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | A resource pointing to JWK with private keys used for JWE content key decryption |
| <span id="aea75b-identity-uri"></span> `identity-uri` | `VALUE` | `URI` |   | URI of the identity server, base used to retrieve OIDC metadata |
| <span id="a0f21f-introspect-endpoint-uri"></span> `introspect-endpoint-uri` | `VALUE` | `URI` |   | Endpoint to use to validate JWT |
| <span id="aa6493-issuer"></span> `issuer` | `VALUE` | `String` |   | Issuer of issued tokens |
| <span id="aaf0a0-name"></span> `name` | `VALUE` | `String` |   | Name of the tenant |
| <span id="a14def-oidc-metadata-well-known"></span> `oidc-metadata-well-known` | `VALUE` | `Boolean` | `true` | If set to true, metadata will be loaded from default (well known) location, unless it is explicitly defined using oidc-metadata-resource |
| <span id="a23e2c-oidc-metadata-resource"></span> [`oidc-metadata.resource`](../../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Resource configuration for OIDC Metadata containing endpoints to various identity services, as well as information about the identity server |
| <span id="ac2900-optional-audience"></span> `optional-audience` | `VALUE` | `Boolean` | `false` | Allow audience claim to be optional |
| <span id="aa8075-scope-audience"></span> `scope-audience` | `VALUE` | `String` |   | Audience of the scope required by this application |
| <span id="af12f3-server-type"></span> `server-type` | `VALUE` | `String` | `@default` | Configure one of the supported types of identity servers |
| <span id="a8cb9d-sign-jwk-resource"></span> [`sign-jwk.resource`](../../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | A resource pointing to JWK with public keys of signing certificates used to validate JWT |
| <span id="aa5a6b-token-endpoint-auth"></span> [`token-endpoint-auth`](../../config/io_helidon_security_providers_oidc_common_OidcConfig_ClientAuthentication.md) | `VALUE` | `i.h.s.p.o.c.O.ClientAuthentication` | `CLIENT_SECRET_BASIC` | Type of authentication to use when invoking the token endpoint |
| <span id="a3ab59-token-endpoint-uri"></span> `token-endpoint-uri` | `VALUE` | `URI` |   | URI of a token endpoint used to obtain a JWT based on the authentication code |
| <span id="aa43d0-validate-jwt-with-jwk"></span> `validate-jwt-with-jwk` | `VALUE` | `Boolean` | `true` | Use JWK (a set of keys to validate signatures of JWT) to validate tokens |

#### How does that work?

Multi-tenant support requires to obtain tenant name from the incoming request. OIDC configuration is selected based on the received tenant name. The way this tenant name has to be provided is configured via `tenant-id-style` configuration. See [How to enable tenants](#multiple-tenants) for more information. After matching tenant configuration with the received name, the rest of the OIDC flow if exactly the same as in [How does OIDC work](#how-does-it-work).

Base OIDC configuration is treated as a default tenant, which is used, if no tenant name is provided. This default tenant is having `@default` name specified.

It is also important to note, that each tenant configuration is based on the default tenant configuration (base OIDC configuration), and therefore its configuration do not need to change all the properties, if they do not differ from the base OIDC configuration.

## CORS Settings

CORS is (now) a single component configured either through config (key `cors`), or programmatically via `io.helidon.webserver.cors.CorsFeature`. To add proper CORS setup for the OIDC endpoint, use one of these. Component specific CORS setup will be removed from Helidon.

### HTTP Basic Authentication Provider

HTTP Basic authentication support

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a57c45-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="aade93-outbound"></span> [`outbound`](../../config/io_helidon_security_providers_common_OutboundTarget.md) | `LIST` | `i.h.s.p.c.OutboundTarget` |   | Add a new outbound target to configure identity propagation or explicit username/password |
| <span id="aa4dbd-principal-type"></span> [`principal-type`](../../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a9be1e-realm"></span> `realm` | `VALUE` | `String` | `helidon` | Set the realm to use when challenging users |
| <span id="a18d67-users"></span> [`users`](../../config/io_helidon_security_providers_httpauth_ConfigUserStore_ConfigUser.md) | `LIST` | `i.h.s.p.h.C.ConfigUser` |   | Set user store to validate users |

#### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/outbound-override) on GitHub.

Configuration example

``` yaml
security:
  providers:
  - http-basic-auth:
      realm: "helidon"
      users:
      - login: "john"
        password: "${CLEAR=changeit}"
        roles: ["admin"]
      - login: "jack"
        password: "changeit"
        roles: ["user", "admin"]
      outbound:
        - name: "internal-services"
          hosts: ["*.example.org"]
          # Propagates current user's identity or identity from request property
          outbound-token:
            header: "X-Internal-Auth"
        - name: "partner-service"
          hosts: ["*.partner.org"]
          # Uses this username and password
          username: "partner-user-1"
          password: "${CLEAR=changeit}"
```

#### How does it work?

See <a href="https://tools.ietf.org/html/rfc7617" class="bare">https://tools.ietf.org/html/rfc7617</a>.

**Authentication of request**

When a request is received without the `Authorization: basic …​.` header, a challenge is returned to provide such authentication.

When a request is received with the `Authorization: basic …​.` header, the username and password is validated against configured users (and users obtained from custom service if any provided).

Subject is created based on the username and roles provided by the user store.

**Identity propagation**

When identity propagation is configured, there are several options for identifying username and password to propagate:

1.  We propagate the current username and password (inbound request must be authenticated using basic authentication).
2.  We use username and password from an explicitly configured property (See `EndpointConfig.PROPERTY_OUTBOUND_ID` and `EndpointConfig.PROPERTY_OUTBOUND_SECRET`)
3.  We use username and password associated with an outbound target (see example configuration above)

Identity is propagated only if:

1.  There is an outbound target configured for the endpoint
2.  Or there is an explicitly configured username/password for the current request (through request property)

**Custom user store**

Java service loader service `io.helidon.security.providers.httpauth.spi.UserStoreService` can be implemented to provide users to the provider, such as when validated against an internal database or LDAP server. The user store is defined so you never need the clear text password of the user.

*Warning on security of HTTP Basic Authentication (or lack thereof)*

Basic authentication uses base64 encoded username and password and passes it over the network. Base64 is only encoding, not encryption - so anybody that gets hold of the header value can learn the actual username and password of the user. This is a security risk and an attack vector that everybody should be aware of before using HTTP Basic Authentication. We recommend using this approach only for testing and demo purposes.

### HTTP Digest Authentication Provider

HTTP Digest authentication support

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a090f1-algorithm"></span> [`algorithm`](../../config/io_helidon_security_providers_httpauth_HttpDigest_Algorithm.md) | `VALUE` | `i.h.s.p.h.H.Algorithm` | `MD5` | Digest algorithm to use |
| <span id="a0ad17-nonce-timeout-millis"></span> `nonce-timeout-millis` | `VALUE` | `Long` | `86400000` | How long will the nonce value be valid. When timed-out, browser will re-request username/password |
| <span id="a45a23-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="a5336f-principal-type"></span> [`principal-type`](../../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="af980a-qop"></span> [`qop`](../../config/io_helidon_security_providers_httpauth_HttpDigest_Qop.md) | `VALUE` | `i.h.s.p.h.H.Qop` | `NONE` | Only \`AUTH\` supported. If left empty, uses the legacy approach (older RFC version). \`AUTH-INT\` is not supported |
| <span id="a5d808-realm"></span> `realm` | `VALUE` | `String` | `Helidon` | Set the realm to use when challenging users |
| <span id="abd869-server-secret"></span> `server-secret` | `LIST` | `String` |   | The nonce is encrypted using this secret - to make sure the nonce we get back was generated by us and to make sure we can safely time-out nonce values |
| <span id="a97822-users"></span> [`users`](../../config/io_helidon_security_providers_httpauth_ConfigUserStore_ConfigUser.md) | `LIST` | `i.h.s.p.h.C.ConfigUser` |   | Set user store to obtain passwords and roles based on logins |

#### Example code

Configuration example

``` yaml
security:
  providers:
  - http-digest-auth:
      realm: "helidon"
      server-secret: "${CLEAR=service-wide-secret-not-known-outside}"
      users:
      - login: "john"
        password: "${CLEAR=changeit}"
        roles: ["admin"]
      - login: "jack"
        password: "changeit"
        roles: ["user", "admin"]
```

#### How does it work?

See <a href="https://tools.ietf.org/html/rfc7616" class="bare">https://tools.ietf.org/html/rfc7616</a>.

**Authentication of request**

When a request is received without the `Authorization: digest …​.` header, a challenge is returned to provide such authentication using `WWW-Authenticate` header.

When a request is received with the `Authorization: digest …​.` header, the request is validated against configured users (and users obtained from custom service if any provided).

Subject is created based on the username and roles provided by the user store.

**Custom user store**

Java service loader service `io.helidon.security.providers.httpauth.spi.UserStoreService` can be implemented to provide users to the provider, such as when validated against an internal database or LDAP server. The user store is defined so you never need the clear text password of the user.

*Note on security of HTTP Digest Authentication*

These authentication schemes should be *obsolete*, though they provide a very easy way to test a protected resource.

### Header Authentication Provider

Asserts user or service identity based on a value of a header.

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-header</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9672f-atn-token"></span> [`atn-token`](../../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to extract username from request |
| <span id="a25377-authenticate"></span> `authenticate` | `VALUE` | `Boolean` | `true` | Whether to authenticate requests |
| <span id="adbdf3-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="aa4f36-outbound"></span> [`outbound`](../../config/io_helidon_security_providers_common_OutboundTarget.md) | `LIST` | `i.h.s.p.c.OutboundTarget` |   | Configure outbound target for identity propagation |
| <span id="ad5021-outbound-token"></span> [`outbound-token`](../../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to create outbound headers to propagate identity |
| <span id="aa8e94-principal-type"></span> [`principal-type`](../../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a0309f-propagate"></span> `propagate` | `VALUE` | `Boolean` | `false` | Whether to propagate identity |

#### Example code

Configuration example

``` yaml
security:
  providers:
    header-atn:
      atn-token:
        header: "X-AUTH-USER"
      outbound:
        - name: "internal-services"
          hosts: ["*.example.org"]
          # propagates the current user or service id using the same header as authentication
        - name: "partner-service"
          hosts: ["*.partner.org"]
          # propagates an explicit username in a custom header
          username: "service-27"
          outbound-token:
            header: "X-Service-Auth"
```

#### How does it work?

This provider inspects a specified request header and extracts the username/service name from it and asserts it as current subject’s principal.

This can be used when we use perimeter authentication (e.g. there is a gateway that takes care of authentication and propagates the user in a header).

**Identity propagation**

Identity is propagated only if an outbound target matches the target service.

The following options exist when propagating identity: 1. We propagate the current username using the configured header 2. We use username associated with an outbound target (see example configuration above)

**Caution**

When using this provider, you must be sure the header cannot be explicitly configured by a user or another service. All requests should go through a gateway that removes this header from inbound traffic, and only configures it for authenticated users/services. Another option is to use this with fully trusted parties (such as services within a single company, on a single protected network not accessible to any users), and of course for testing and demo purposes.

### HTTP Signatures Provider

Support for HTTP Signatures.

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-http-sign</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac1d34-backward-compatible-eol"></span> `backward-compatible-eol` | `VALUE` | `Boolean` | `false` | Enable support for Helidon versions before 3.0.0 (exclusive) |
| <span id="acc108-headers"></span> [`headers`](../../config/io_helidon_security_providers_httpsign_HttpSignHeader.md) | `LIST` | `i.h.s.p.h.HttpSignHeader` |   | Add a header that is validated on inbound requests |
| <span id="abbc62-inbound-keys"></span> [`inbound.keys`](../../config/io_helidon_security_providers_httpsign_InboundClientDefinition.md) | `LIST` | `i.h.s.p.h.InboundClientDefinition` |   | Add inbound configuration |
| <span id="a9cb96-optional"></span> `optional` | `VALUE` | `Boolean` | `true` | Set whether the signature is optional |
| <span id="af2400-outbound"></span> [`outbound`](../../config/io_helidon_security_providers_common_OutboundConfig.md) | `VALUE` | `i.h.s.p.c.OutboundConfig` |   | Add outbound targets to this builder |
| <span id="a4938a-realm"></span> `realm` | `VALUE` | `String` | `helidon` | Realm to use for challenging inbound requests that do not have "Authorization" header in case header is `HttpSignHeader#AUTHORIZATION` and singatures are not optional |
| <span id="a4ba7d-sign-headers"></span> [`sign-headers`](../../config/io_helidon_security_providers_httpsign_SignedHeadersConfig_HeadersConfig.md) | `LIST` | `i.h.s.p.h.S.HeadersConfig` |   | Override the default inbound required headers (e.g |

#### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/webserver-signatures) on GitHub.

Configuration example

``` yaml
security:
  providers:
    - http-signatures:
        inbound:
          keys:
            - key-id: "service1-hmac"
              principal-name: "Service1 - HMAC signature"
              hmac.secret: "${CLEAR=changeit}"
            - key-id: "service1-rsa"
              principal-name: "Service1 - RSA signature"
              public-key:
                keystore:
                  resource.path: "src/main/resources/keystore.p12"
                  passphrase: "changeit"
                  cert.alias: "service_cert"
        outbound:
          - name: "service2-hmac"
            hosts: ["localhost"]
            paths: ["/service2"]
            signature:
              key-id: "service1-hmac"
              hmac.secret: "${CLEAR=changeit}"
          - name: "service2-rsa"
            hosts: ["localhost"]
            paths: ["/service2-rsa.*"]
            signature:
              key-id: "service1-rsa"
              private-key:
                keystore:
                  resource.path: "src/main/resources/keystore.p12"
                  passphrase: "changeit"
                  key.alias: "myPrivateKey"
```

#### Signature basics

- standard: based on <a href="https://tools.ietf.org/html/draft-cavage-http-signatures-03" class="bare">https://tools.ietf.org/html/draft-cavage-http-signatures-03</a>
- key-id: an arbitrary string used to locate signature configuration - when a request is received the provider locates validation configuration based on this id (e.g. HMAC shared secret or RSA public key). Commonly used meanings are: key fingerprint (RSA); API Key

#### How does it work?

**Inbound Signatures** We act as a server and another party is calling us with a signed HTTP request. We validate the signature and assume identity of the caller.

**Outbound Signatures** We act as a client and we sign our outgoing requests. If there is a matching `outbound` target specified in configuration, its configuration will be applied for signing the outgoing request, otherwise there is no signature added

### IDCS Role Mapper

A role mapper to retrieve roles from Oracle IDCS.

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-idcs-mapper</artifactId>
</dependency>
```

#### Single-tenant IDCS Role Mapper

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aed3ab-cache-config"></span> [`cache-config`](../../config/io_helidon_security_providers_common_EvictableCache.md) | `VALUE` | `i.h.s.p.c.EvictableCache` |   | Use explicit `io.helidon.security.providers.common.EvictableCache` for role caching |
| <span id="aa2e00-default-idcs-subject-type"></span> `default-idcs-subject-type` | `VALUE` | `String` | `user` | Configure subject type to use when requesting roles from IDCS |
| <span id="a630af-oidc-config"></span> [`oidc-config`](../../config/io_helidon_security_providers_oidc_common_OidcConfig.md) | `VALUE` | `i.h.s.p.o.c.OidcConfig` |   | Use explicit `io.helidon.security.providers.oidc.common.OidcConfig` instance, e.g |
| <span id="a477d4-subject-types"></span> [`subject-types`](../../config/io_helidon_security_SubjectType.md) | `LIST` | `i.h.s.SubjectType` | `USER` | Add a supported subject type |

#### Multi-tenant IDCS Role Mapper

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6bc4c-cache-config"></span> [`cache-config`](../../config/io_helidon_security_providers_common_EvictableCache.md) | `VALUE` | `i.h.s.p.c.EvictableCache` |   | Use explicit `io.helidon.security.providers.common.EvictableCache` for role caching |
| <span id="a75027-default-idcs-subject-type"></span> `default-idcs-subject-type` | `VALUE` | `String` | `user` | Configure subject type to use when requesting roles from IDCS |
| <span id="a89a70-idcs-app-name-handler"></span> [`idcs-app-name-handler`](../../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Configure token handler for IDCS Application name |
| <span id="af8920-idcs-tenant-handler"></span> [`idcs-tenant-handler`](../../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Configure token handler for IDCS Tenant ID |
| <span id="a2275a-oidc-config"></span> [`oidc-config`](../../config/io_helidon_security_providers_oidc_common_OidcConfig.md) | `VALUE` | `i.h.s.p.o.c.OidcConfig` |   | Use explicit `io.helidon.security.providers.oidc.common.OidcConfig` instance, e.g |
| <span id="ab2c38-subject-types"></span> [`subject-types`](../../config/io_helidon_security_SubjectType.md) | `LIST` | `i.h.s.SubjectType` | `USER` | Add a supported subject type |

#### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/idcs-login/) on GitHub.

Configuration example

``` yaml
security:
  providers:
    - idcs-role-mapper:
        multitenant: false
        oidc-config:
            client-id: "client-id"
            client-secret: "changeit"
            identity-uri: "IDCS identity server address"
```

#### How does it work?

The provider asks the IDCS server to provide list of roles for the currently authenticated user. The result is cached for a certain period of time (see `cache-config` above).

### ABAC Provider

Attribute based access control authorization provider.

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-abac</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a4f520-fail-if-none-validated"></span> `fail-if-none-validated` | `VALUE` | `Boolean` | `true` | Whether to fail if NONE of the attributes is validated |
| <span id="a52725-fail-on-unvalidated"></span> `fail-on-unvalidated` | `VALUE` | `Boolean` | `true` | Whether to fail if any attribute is left unvalidated |

#### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/attribute-based-access-control) on GitHub.

Configuration example

``` yaml
security:
  providers:
    - abac:
```

#### Configuration options

The following table shows all configuration options of the provider and their default values

| key | default value | description |
|----|----|----|
| `fail-on-unvalidated` | `true` | "Unvalidated" means: an attribute is defined, but there is no validator available for it |
| `fail-if-none-validated` | `true` | "None validated" means: there was not a single attribute that was validated |

#### How does it work?

ABAC uses available validators and validates them against attributes of the authenticated user.

Combinations of `fail-on-unvalidated` and `fail-if-none-validated`:

1.  `true` & `true`: Will fail if any attribute is not validated and if any has failed validation
2.  `false` & `true`: Will fail if there is one or more attributes present and NONE of them is validated or if any has failed validation, Will NOT fail if there is at least one validated attribute and any number of not validated attributes (and NONE failed)
3.  `false` & `false`: Will fail if there is any attribute that failed validation, Will NOT fail if there are no failed validation or if there are NONE validated

Any attribute of the following objects can be used:

- environment (such as time of request) - e.g. env.time.year
- subject (user) - e.g. subject.principal.id
- subject (service) - e.g. service.principal.id
- object (must be explicitly invoked by developer in code, as object cannot be automatically added to security context) - e.g. object.owner

This provider checks that all defined ABAC validators are validated. If there is a definition for a validator that is not checked, the request is denied (depending on configuration as mentioned above).

ABAC provider also allows an object to be used in authorization process, such as when evaluating if an object’s owner is the current user. The following example uses the Expression language validator to demonstrate the point in a JAX-RS resource:

Example of using an object

``` java
@Authenticated
@Path("/abac")
public class AbacResource {
    @GET
    @Authorized(explicit = true)
    @PolicyStatement("${env.time.year >= 2017 && object.owner == subject.principal.id}")
    public Response process(@Context SecurityContext context) {
        // probably looked up from a database
        SomeResource res = new SomeResource("user");
        AuthorizationResponse atzResponse = context.authorize(res);

        if (atzResponse.isPermitted()) {
            //do the update
            return Response.ok().entity("fine, sir").build();
        } else {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(atzResponse.description().orElse("Access not granted"))
                    .build();
        }
    }
}
```

**The following validators are implemented:**

- [Roles](#role-validator)
- [Scopes](#scope-validator)
- [EL Policy](#expression-language-policy-validator)

#### Role Validator

Checks whether user/service is in either of the required role(s).

Configuration Key: `role-validator`

Annotations: `@RolesAllowed`, `@RoleValidator.Roles`

Configuration example for `WebServer`

``` yaml
security:
  web-server.paths:
    - path: "/user/*"
      roles-allowed: ["user"]
```

JAX-RS example

``` java
@RolesAllowed("user")
@RoleValidator.Roles(value = "service_role", subjectType = SubjectType.SERVICE)
@Authenticated
@Path("/abac")
public class AbacResource {
}
```

##### Interaction with JAX-RS sub-resource locators

When using sub-resource locators in JAX-RS, the roles allowed are collected from each "level" of execution: - Application class annotations - Resource class annotations + resource method annotations - Sub-resource class annotations + sub-resource method annotations - Sub-resource class annotations + sub-resource method annotations (for every sub-resource on the path)

The `RolesAllowed` or `Roles` annotation to be used is the last one in the path as defined above.

*Example 1:* There is a `RolesAllowed("admin")` defined on a sub-resource locator resource class. In this case the required role is `admin`.

*Example 2:* There is a `RolesAllowed("admin")` defined on a sub-resource locator resource class and a `RolesAllowed("user")` defined on the method of the sub-resource that provides the response. In this case the required role is `user`.

#### Scope Validator

Checks whether user has all the required scopes.

Configuration Key: `scope-validator`

Annotations: `@Scope`

Configuration example for `WebServer`

``` yaml
security:
  web-server.paths:
    - path: "/user/*"
      abac.scopes:
        ["calendar_read", "calendar_edit"]
```

JAX-RS example

``` java
@Scope("calendar_read")
@Scope("calendar_edit")
@Authenticated
@Path("/abac")
public class AbacResource {
}
```

#### Expression Language Policy Validator

Policy executor using Java EE policy expression language (EL)

Configuration Key: `policy-javax-el`

Annotations: `@PolicyStatement`

Example of a policy statement: `${env.time.year >= 2017}`

Configuration example for `WebServer`

``` yaml
security:
  web-server.paths:
    - path: "/user/*"
      policy:
        statement: "hasScopes('calendar_read','calendar_edit') AND timeOfDayBetween('8:15', '17:30')"
```

JAX-RS example

``` java
@PolicyStatement("${env.time.year >= 2017}")
@Authenticated
@Path("/abac")
public class AbacResource {
}
```

Configuration example for `JAX-RS` over the configuration

``` yaml
server:
  features:
    security:
      endpoints:
        - path: "/somePath"
          config:
            abac.policy-validator.statement: "\\${env.time.year >= 2017}"
```

### Google Login Provider

Authenticates a token from request against Google identity provider

This provider is deprecated and will be removed in a future version of Helidon. Please use our OpenID Connect security provider instead.

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-google-login</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a8327f-client-id"></span> `client-id` | `VALUE` | `String` |   | Google application client id, to validate that the token was generated by Google for us |
| <span id="a5eb30-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | If set to true, this provider will return `io.helidon.security.SecurityResponse.SecurityStatus#ABSTAIN` instead of failing in case of invalid request |
| <span id="a6fd85-outbound"></span> [`outbound`](../../config/io_helidon_security_providers_common_OutboundConfig.md) | `VALUE` | `i.h.s.p.c.OutboundConfig` |   | Outbound configuration - a set of outbound targets that will have the token propagated |
| <span id="a836e0-proxy-host"></span> `proxy-host` | `VALUE` | `String` |   | Set proxy host when talking to Google |
| <span id="a72cab-proxy-port"></span> `proxy-port` | `VALUE` | `Integer` | `80` | Set proxy port when talking to Google |
| <span id="a7871a-realm"></span> `realm` | `VALUE` | `String` | `helidon` | Set the authentication realm to build challenge, defaults to "helidon" |
| <span id="af185f-token"></span> [`token`](../../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` | `` `Authorization` header with `bearer` prefix `` | Token provider to extract Google access token from request, defaults to "Authorization" header with a "bearer " prefix |

#### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/google-login) on GitHub.

Configuration example

``` yaml
security:
  providers:
    - provider:
        client-id: "Google client id"
```

#### How does it work?

We expect to receive a token (with sufficient scopes) from the inbound request, such as when using the Google login button on a page. The page has access to the token in javascript and can send it to backend with every request in a header field (`Authorization` with \`bearer \` prefix is assumed by default).

Once we receive the token in Helidon, we parse it and:

1.  Validate if it timed out locally
2.  Return a cached response (see `EvictableCache` with default values)
3.  Otherwise verify using Google API - `GoogleIdTokenVerifier`

We build a subject from the Google token with the following attributes filled (if in token):

- userId
- email
- name
- emailVerified
- locale
- family_name
- given_name
- picture (URL)

**Outbound security** The token will be propagated to outbound calls if an outbound target exists that matches the invoked endpoint (see `outbound` configuration above).

### JWT Provider

JWT token authentication and outbound security provider.

#### Setup

Maven dependency

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-jwt</artifactId>
</dependency>
```

#### Overview

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a8e9cb-allow-impersonation"></span> `allow-impersonation` | `VALUE` | `Boolean` | `false` | Whether to allow impersonation by explicitly overriding username from outbound requests using `io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID` property |
| <span id="ac7a36-allow-unsigned"></span> `allow-unsigned` | `VALUE` | `Boolean` | `false` | Configure support for unsigned JWT |
| <span id="a31c89-atn-token-handler"></span> [`atn-token.handler`](../../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to extract username from request |
| <span id="ab9ed4-atn-token-jwk-resource"></span> [`atn-token.jwk.resource`](../../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | JWK resource used to verify JWTs created by other parties |
| <span id="a7fd00-atn-token-jwt-audience"></span> `atn-token.jwt-audience` | `VALUE` | `String` |   | Audience expected in inbound JWTs |
| <span id="a1483b-atn-token-verify-signature"></span> `atn-token.verify-signature` | `VALUE` | `Boolean` | `true` | Configure whether to verify signatures |
| <span id="a2bd0c-authenticate"></span> `authenticate` | `VALUE` | `Boolean` | `true` | Whether to authenticate requests |
| <span id="ac625d-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="af07ea-principal-type"></span> [`principal-type`](../../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a5a95f-propagate"></span> `propagate` | `VALUE` | `Boolean` | `true` | Whether to propagate identity |
| <span id="a9294b-sign-token"></span> [`sign-token`](../../config/io_helidon_security_providers_common_OutboundConfig.md) | `VALUE` | `i.h.s.p.c.OutboundConfig` |   | Configuration of outbound rules |
| <span id="adc22c-sign-token-jwk-resource"></span> [`sign-token.jwk.resource`](../../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | JWK resource used to sign JWTs created by us |
| <span id="ab60c1-sign-token-jwt-issuer"></span> `sign-token.jwt-issuer` | `VALUE` | `String` |   | Issuer used to create new JWTs |
| <span id="a8cde7-use-jwt-groups"></span> `use-jwt-groups` | `VALUE` | `Boolean` | `true` | Claim `groups` from JWT will be used to automatically add groups to current subject (may be used with `jakarta.annotation.security.RolesAllowed` annotation) |

#### Example code

See the [example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/outbound-override) on GitHub.

Configuration example

``` yaml
security:
  providers:
    - provider:
        atn-token:
          jwk.resource.resource-path: "verifying-jwk.json"
          jwt-audience: "http://my.service"
        sign-token:
          jwk.resource.resource-path: "signing-jwk.json"
          jwt-issuer: "http://my.server/identity"
          outbound:
          - name: "propagate-token"
            hosts: ["*.internal.org"]
          - name: "generate-token"
            hosts: ["1.partner-service"]
            jwk-kid: "partner-1"
            jwt-kid: "helidon"
            jwt-audience: "http://1.partner-service"
```

#### How does it work?

JSON Web Token (JWT) provider has support for authentication and outbound security.

Authentication is based on validating the token (signature, valid before etc.) and on asserting the subject of the JWT subject claim.

For outbound, we support either token propagation (e.g. the token from request is propagated further) or support for generating a brand new token based on configuration of this provider.

## Reference

- [Helidon Security Examples](https://github.com/oracle/helidon/tree/mainexamples/security)
- [Helidon OIDC JavaDoc](/apidocs/io.helidon.security.providers.oidc/module-summary.html)
- [Helidon HTTP Authentication JavaDoc](/apidocs/io.helidon.security.providers.httpauth/module-summary.html)
- [Helidon Header Authentication JavaDoc](/apidocs/io.helidon.security.providers.header/module-summary.html)
- [Helidon HTTP Signature JavaDoc](/apidocs/io.helidon.security.providers.httpsign/module-summary.html)
- [Helidon IDCS Role Mapper JavaDoc](/apidocs/io.helidon.security.providers.idcs.mapper/module-summary.html)
- [Helidon ABAC JavaDoc](/apidocs/io.helidon.security.providers.abac/module-summary.html)
- [Helidon Google Login JavaDoc](/apidocs/io.helidon.security.providers.google.login/module-summary.html)
- [Helidon JWT JavaDoc](/apidocs/io.helidon.security.providers.jwt/module-summary.html)
