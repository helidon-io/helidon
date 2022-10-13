/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Optional;

import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.common.Errors;
import io.helidon.security.Security;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.HttpBasicOutboundConfig;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.security.WebClientSecurity;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

/**
 * Holder of the tenant configuration resolved in runtime. Used for OIDC lazy loading.
 */
public final class Tenant {

    private final TenantConfig tenantConfig;
    private final URI tokenEndpointUri;
    private final String authorizationEndpointUri;
    private final URI logoutEndpointUri;
    private final String issuer;
    private final Client appClient;
    private final WebClient appWebClient;
    private final WebTarget tokenEndpoint;
    private final JwkKeys signJwk;
    private final URI introspectUri;

    private Tenant(TenantConfig tenantConfig,
           URI tokenEndpointUri,
           URI authorizationEndpointUri,
           URI logoutEndpointUri,
           String issuer,
           Client appClient,
           WebClient appWebClient,
           WebTarget tokenEndpoint,
           JwkKeys signJwk,
           URI introspectUri) {
        this.tenantConfig = tenantConfig;
        this.tokenEndpointUri = tokenEndpointUri;
        this.authorizationEndpointUri = authorizationEndpointUri.toString();
        this.logoutEndpointUri = logoutEndpointUri;
        this.issuer = issuer;
        this.appClient = appClient;
        this.appWebClient = appWebClient;
        this.tokenEndpoint = tokenEndpoint;
        this.signJwk = signJwk;
        this.introspectUri = introspectUri;
    }

    public static Tenant create(OidcConfig defaultOidcConfig, TenantConfig tenantConfig) {
        WebClient webClient = defaultOidcConfig.generalWebClient();

        Errors.Collector collector = Errors.collector();
        OidcMetadata oidcMetadata = OidcMetadata.builder().webClient(webClient)
                .remoteEnabled(tenantConfig.oidcMetadataWellKnown())
                .identityUri(tenantConfig.identityUri())
                .collector(collector)
                .build();

        URI tokenEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                            tenantConfig.tokenEndpointUri(),
                                                            "token_endpoint",
                                                            "/oauth2/v1/token");

        URI authorizationEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                                    tenantConfig.authorizationEndpoint(),
                                                                    "authorization_endpoint",
                                                                    "/oauth2/v1/authorize");

        URI logoutEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                             tenantConfig.logoutEndpointUri(),
                                                             "end_session_endpoint",
                                                             "oauth2/v1/userlogout");

        String issuer = tenantConfig.issuer();
        if (issuer == null) {
            Optional<String> maybeIssuer = oidcMetadata.getString("issuer");
            issuer = maybeIssuer.orElse(null);
        }

        collector.collect().checkValid();
        WebClient.Builder webClientBuilder = defaultOidcConfig.webClientBuilderSupplier().get();
        ClientBuilder clientBuilder = defaultOidcConfig.jaxrsClientBuilderSupplier().get();

        if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC) {
            HttpAuthenticationFeature basicAuth = HttpAuthenticationFeature.basicBuilder()
                    .credentials(tenantConfig.clientId(), tenantConfig.clientSecret())
                    .build();
            clientBuilder.register(basicAuth);

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

        Client appClient = clientBuilder.build();
        WebClient appWebClient = webClientBuilder.build();
        WebTarget tokenEndpoint = appClient.target(tokenEndpointUri);

        JwkKeys signJwk = tenantConfig.signJwk();
        URI introspectUri = tenantConfig.introspectUri();
        if (tenantConfig.validateJwtWithJwk()) {
            if (signJwk == null) {
                // not configured - use default location
                URI jwkUri = oidcMetadata.getOidcEndpoint(collector,
                                                          null,
                                                          "jwks_uri",
                                                          null);
                if (jwkUri != null) {
                    if ("idcs".equals(tenantConfig.serverType())) {
                        signJwk = IdcsSupport.signJwk(appWebClient,
                                                      webClient,
                                                      tokenEndpointUri,
                                                      jwkUri,
                                                      tenantConfig.clientTimeout());
                    } else {
                        signJwk = JwkKeys.builder()
                                .json(webClient.get()
                                              .uri(jwkUri)
                                              .request(JsonObject.class)
                                              .await())
                                .build();
                    }
                }
            }
        } else {
            introspectUri = oidcMetadata.getOidcEndpoint(collector,
                                                         introspectUri,
                                                         "introspection_endpoint",
                                                         "/oauth2/v1/introspect");
        }
        return new Tenant(tenantConfig,
                          tokenEndpointUri,
                          authorizationEndpointUri,
                          logoutEndpointUri,
                          issuer,
                          appClient,
                          appWebClient,
                          tokenEndpoint,
                          signJwk,
                          introspectUri);
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

    /**
     * Token endpoint of the OIDC server.
     *
     * @return target the endpoint is on
     */
    WebTarget tokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Client with configured proxy and security of this OIDC client.
     *
     * @return client for communication with OIDC server
     */
    Client appClient() {
        return appClient;
    }

}
