/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import io.helidon.common.Errors;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.webclient.WebClient;

import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.WebTarget;

/**
 * Holder of the tenant configuration resolved at runtime. Used for OIDC lazy loading.
 */
public class Tenant {
    private static final String SKIP_CLIENT_CREDENTIALS_PROPERTY =
            "io.helidon.security.providers.oidc.common.skip-client-secret-basic";

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

        OidcMetadata oidcMetadata = OidcMetadata.builder()
                .remoteEnabled(tenantConfig.useWellKnown())
                .json(tenantConfig.oidcMetadata())
                .webClient(webClient)
                .identityUri(tenantConfig.identityUri())
                .collector(collector)
                .build();

        URI tokenEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                            tenantConfig.tenantTokenEndpointUri().orElse(null),
                                                            "token_endpoint",
                                                            "/oauth2/v1/token");

        URI authorizationEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                                    tenantConfig.authorizationEndpoint().orElse(null),
                                                                    "authorization_endpoint",
                                                                    "/oauth2/v1/authorize");

        URI logoutEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                             tenantConfig.tenantLogoutEndpointUri().orElse(null),
                                                             "end_session_endpoint",
                                                             "oauth2/v1/userlogout");

        String issuer = tenantConfig.tenantIssuer()
                .or(() -> oidcMetadata.getString("issuer"))
                .orElse(null);

        URI introspectUri = tenantConfig.tenantIntrospectUri().orElse(null);
        if (!tenantConfig.validateJwtWithJwk()) {
            introspectUri = oidcMetadata.getOidcEndpoint(collector,
                                                         introspectUri,
                                                         "introspection_endpoint",
                                                         "/oauth2/v1/introspect");
        }

        collector.collect().checkValid();
        WebClient.Builder webClientBuilder = oidcConfig.webClientBuilderSupplier().get();
        ClientBuilder clientBuilder = oidcConfig.jaxrsClientBuilderSupplier().get();

        if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC) {
            URI finalIntrospectUri = introspectUri;
            String basicAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString((tenantConfig.clientId() + ":" + tenantConfig.clientSecret())
                                            .getBytes(StandardCharsets.UTF_8));

            clientBuilder.register((ClientRequestFilter) requestContext -> {
                if (!Boolean.TRUE.equals(requestContext.getProperty(SKIP_CLIENT_CREDENTIALS_PROPERTY))
                        && matchesEndpoint(requestContext.getMethod(),
                                           requestContext.getUri(),
                                           tokenEndpointUri,
                                           finalIntrospectUri)) {
                    requestContext.getHeaders().putSingle(Http.Header.AUTHORIZATION, basicAuthorization);
                }
            });

            webClientBuilder.addService(request -> {
                if (matchesEndpoint(request.method().name(), request.uri(), tokenEndpointUri, finalIntrospectUri)) {
                    request.headers().unsetHeader(Http.Header.AUTHORIZATION);
                    request.headers().add(Http.Header.AUTHORIZATION, basicAuthorization);
                }
                return Single.just(request);
            });
        }

        Client appClient = clientBuilder.build();
        WebClient appWebClient = webClientBuilder.build();
        WebTarget tokenEndpoint = appClient.target(tokenEndpointUri);

        JwkKeys signJwk = tenantConfig.tenantSignJwk().orElseGet(() -> {
            if (tenantConfig.validateJwtWithJwk()) {
                // not configured - use default location
                URI jwkUri = oidcMetadata.getOidcEndpoint(collector,
                                                          null,
                                                          "jwks_uri",
                                                          null);
                if (jwkUri != null) {
                    if ("idcs".equals(tenantConfig.serverType())) {
                        return IdcsSupport.signJwk(appWebClient,
                                                   webClient,
                                                   tokenEndpointUri,
                                                   jwkUri,
                                                   tenantConfig.clientTimeout());
                    } else {
                        return JwkKeys.builder()
                                .json(webClient.get()
                                              .uri(jwkUri)
                                              .request(JsonObject.class)
                                              .await())
                                .build();
                    }
                }
            }
            return JwkKeys.builder().build();
        });
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

    private static boolean matchesEndpoint(String method, URI requestUri, URI tokenEndpointUri, URI introspectUri) {
        return "POST".equalsIgnoreCase(method)
                && (matchesEndpoint(tokenEndpointUri, requestUri)
                || (introspectUri != null && matchesEndpoint(introspectUri, requestUri)));
    }

    private static boolean matchesEndpoint(URI endpointUri, URI requestUri) {
        String endpointScheme = endpointUri.getScheme();
        String endpointHost = endpointUri.getHost();
        String requestScheme = requestUri.getScheme();
        String requestHost = requestUri.getHost();

        if (endpointScheme == null || endpointHost == null || requestScheme == null || requestHost == null) {
            return false;
        }

        String endpointPath = endpointUri.getRawPath();
        String requestPath = requestUri.getRawPath();
        return endpointScheme.equalsIgnoreCase(requestScheme)
                && endpointHost.equalsIgnoreCase(requestHost)
                && port(endpointUri) == port(requestUri)
                && (endpointPath == null || endpointPath.isEmpty() ? "/" : endpointPath)
                        .equals(requestPath == null || requestPath.isEmpty() ? "/" : requestPath)
                && Objects.equals(endpointUri.getRawQuery(), requestUri.getRawQuery());
    }

    private static int port(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
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
     * When token endpoint authentication is {@link OidcConfig.ClientAuthentication#CLIENT_SECRET_BASIC},
     * client credentials are scoped to POST requests on the token endpoint scheme, host, port, path, and query and, when
     * JWT introspection is used, to POST requests on the introspection endpoint scheme, host, port, path, and query.
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
