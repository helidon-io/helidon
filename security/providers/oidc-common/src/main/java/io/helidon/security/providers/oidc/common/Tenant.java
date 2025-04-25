/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.security.providers.oidc.common;

import java.net.URI;

import io.helidon.common.Errors;
import io.helidon.security.Security;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.HttpBasicOutboundConfig;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.security.WebClientSecurity;

import jakarta.json.JsonObject;

/**
 * Holder of the tenant configuration resolved at runtime. Used for OIDC lazy loading.
 */
public class Tenant {

    private final TenantConfig tenantConfig;
    private final URI tokenEndpointUri;
    private final String authorizationEndpointUri;
    private final URI logoutEndpointUri;
    private final String issuer;
    private final WebClient appWebClient;
    private final JwkKeys signJwk;
    private final URI introspectUri;

    private Tenant(TenantConfig tenantConfig,
                   URI tokenEndpointUri,
                   URI authorizationEndpointUri,
                   URI logoutEndpointUri,
                   String issuer,
                   WebClient appWebClient,
                   JwkKeys signJwk,
                   URI introspectUri) {
        this.tenantConfig = tenantConfig;
        this.tokenEndpointUri = tokenEndpointUri;
        this.authorizationEndpointUri = authorizationEndpointUri.toString();
        this.logoutEndpointUri = logoutEndpointUri;
        this.issuer = issuer;
        this.appWebClient = appWebClient;
        this.signJwk = signJwk;
        this.introspectUri = introspectUri;
    }

    /**
     * Create new instance and resolve all the metadata related values.
     *
     * @param oidcConfig overall OIDC config
     * @param tenantConfig tenant config
     * @return new instance with resolved OIDC metadata
     */
    public static Tenant create(OidcConfig oidcConfig, TenantConfig tenantConfig) {
        WebClient webClient = oidcConfig.generalWebClient();

        Errors.Collector collector = Errors.collector();

        URI identityUri = tenantConfig.identityUri();
        OidcMetadata oidcMetadata = OidcMetadata.builder()
                .remoteEnabled(tenantConfig.useWellKnown())
                .json(tenantConfig.oidcMetadata())
                .webClient(webClient)
                .identityUri(identityUri)
                .collector(collector)
                .build();

        String serverType = tenantConfig.serverType();
        String metaKey = resolveMetaKey("token_endpoint", serverType, identityUri);
        URI tokenEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                            tenantConfig.tenantTokenEndpointUri().orElse(null),
                                                            metaKey,
                                                            "/oauth2/v1/token");

        URI authorizationEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                                    tenantConfig.authorizationEndpoint().orElse(null),
                                                                    "authorization_endpoint",
                                                                    "/oauth2/v1/authorize");

        metaKey = resolveMetaKey("end_session_endpoint", serverType, identityUri);
        URI logoutEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                             tenantConfig.tenantLogoutEndpointUri().orElse(null),
                                                             metaKey,
                                                             "oauth2/v1/userlogout");

        String issuer = tenantConfig.tenantIssuer()
                .or(() -> oidcMetadata.getString("issuer"))
                .orElse(null);

        collector.collect().checkValid();
        WebClientConfig.Builder webClientBuilder = oidcConfig.webClientBuilderSupplier().get();

        if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC) {

            HttpBasicAuthProvider httpBasicAuth = HttpBasicAuthProvider.builder()
                    .addOutboundTarget(OutboundTarget.builder("oidc")
                                               .addHost("*")
                                               .customObject(HttpBasicOutboundConfig.class,
                                                             HttpBasicOutboundConfig.create(tenantConfig.clientId(),
                                                                                            tenantConfig.clientSecret()))
                                               .build())
                    .build();
            Security tokenOutboundSecurity = Security.builder()
                    .addOutboundSecurityProvider(httpBasicAuth)
                    .build();

            webClientBuilder.addService(WebClientSecurity.create(tokenOutboundSecurity));
        }

        WebClient appWebClient = webClientBuilder.build();

        JwkKeys signJwk = tenantConfig.tenantSignJwk().orElseGet(() -> {
            if (tenantConfig.validateJwtWithJwk()) {
                // not configured - use default location
                String jwksMetaKey = resolveMetaKey("jwks_uri", serverType, identityUri);
                URI jwkUri = oidcMetadata.getOidcEndpoint(collector,
                                                          null,
                                                          jwksMetaKey,
                                                          null);
                if (jwkUri != null) {
                    if ("idcs".equals(serverType)) {
                        return IdcsSupport.signJwk(appWebClient,
                                                   webClient,
                                                   tokenEndpointUri,
                                                   jwkUri,
                                                   tenantConfig.clientTimeout(),
                                                   tenantConfig);
                    } else {
                        return JwkKeys.builder()
                                .json(webClient.get()
                                              .uri(jwkUri)
                                              .requestEntity(JsonObject.class))
                                .build();
                    }
                }
            }
            return JwkKeys.builder().build();
        });
        URI introspectUri = tenantConfig.tenantIntrospectUri().orElse(null);
        if (!tenantConfig.validateJwtWithJwk()) {
            metaKey = resolveMetaKey("introspection_endpoint", serverType, identityUri);
            introspectUri = oidcMetadata.getOidcEndpoint(collector,
                                                         introspectUri,
                                                         metaKey,
                                                         "/oauth2/v1/introspect");
        }
        return new Tenant(tenantConfig,
                          tokenEndpointUri,
                          authorizationEndpointUri,
                          logoutEndpointUri,
                          issuer,
                          appWebClient,
                          signJwk,
                          introspectUri);
    }

    private static String resolveMetaKey(String metaKey, String serverType, URI identityUri) {
        if ("idcs".equals(serverType) && identityUri.toString().contains(".secure.")) {
            //when server is IDCS and URI has ".secure." defined, we know we are using MTLS and need to obtain
            //secured endpoint also.
            return "secure_" + metaKey;
        }
        return metaKey;
    }

    /**
     * Provided tenant configuration.
     *
     * @return tenant configuration
     */
    public TenantConfig tenantConfig() {
        return tenantConfig;
    }

    /**
     * Token endpoint URI.
     *
     * @return endpoint URI
     */
    public URI tokenEndpointUri() {
        return tokenEndpointUri;
    }

    /**
     * Authorization endpoint.
     *
     * @return authorization endpoint uri as a string
     */
    public String authorizationEndpointUri() {
        return authorizationEndpointUri;
    }

    /**
     * Logout endpoint on OIDC server.
     *
     * @return URI of the logout endpoint
     */
    public URI logoutEndpointUri() {
        return logoutEndpointUri;
    }

    /**
     * Token issuer.
     *
     * @return token issuer
     */
    public String issuer() {
        return issuer;
    }

    /**
     * Client with configured proxy and security.
     *
     * @return client for communicating with OIDC identity server
     */
    public WebClient appWebClient() {
        return appWebClient;
    }

    /**
     * JWK used for signature validation.
     *
     * @return set of keys used to verify tokens
     */
    public JwkKeys signJwk() {
        return signJwk;
    }

    /**
     * Introspection endpoint URI.
     *
     * @return introspection endpoint URI
     */
    public URI introspectUri() {
        if (introspectUri == null) {
            throw new SecurityException("Introspect URI is not configured when using validate with JWK.");
        }
        return introspectUri;
    }

}
