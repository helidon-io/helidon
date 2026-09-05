/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

import io.helidon.common.Errors;
import io.helidon.common.configurable.ResourceConfig;
import io.helidon.common.configurable.ResourceException;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.json.JsonException;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.security.Security;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.ResilientResource;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.HttpBasicOutboundConfig;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.security.WebClientSecurity;

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
        ResourceConfig metadataResource = tenantConfig.oidcMetadataResource().orElse(null);
        JsonObject metadataJson = resolveMetadata(tenantConfig.oidcMetadataJsonObject(),
                                                  metadataResource,
                                                  tenantConfig.jwkRetryConfig().overallTimeout());
        OidcMetadata oidcMetadata = OidcMetadata.builder()
                .remoteEnabled(tenantConfig.useWellKnown())
                .json(metadataJson)
                .reloadable(metadataResource != null)
                .webClient(webClient)
                .identityUri(identityUri)
                .build();

        String serverType = tenantConfig.serverType();
        String metaKey = OidcUtil.resolveMetaKey("token_endpoint", serverType, identityUri);
        URI tokenEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                            tenantConfig.tenantTokenEndpointUri().orElse(null),
                                                            metaKey,
                                                            "/oauth2/v1/token");

        URI authorizationEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                                    tenantConfig.authorizationEndpoint().orElse(null),
                                                                    "authorization_endpoint",
                                                                    "/oauth2/v1/authorize");

        metaKey = OidcUtil.resolveMetaKey("end_session_endpoint", serverType, identityUri);
        URI logoutEndpointUri = oidcMetadata.getOidcEndpoint(collector,
                                                             tenantConfig.tenantLogoutEndpointUri().orElse(null),
                                                             metaKey,
                                                             "oauth2/v1/userlogout");

        String issuer = tenantConfig.tenantIssuer()
                .or(() -> oidcMetadata.getString("issuer"))
                .orElse(null);

        URI introspectUri = tenantConfig.tenantIntrospectUri().orElse(null);
        if (!tenantConfig.validateJwtWithJwk()) {
            metaKey = OidcUtil.resolveMetaKey("introspection_endpoint", serverType, identityUri);
            introspectUri = oidcMetadata.getOidcEndpoint(collector,
                                                         introspectUri,
                                                         metaKey,
                                                         "/oauth2/v1/introspect");
        }

        collector.collect().checkValid();
        WebClientConfig.Builder webClientBuilder = oidcConfig.webClientBuilderSupplier().get();

        if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC) {
            HttpBasicAuthProvider.Builder httpBasicAuthBuilder = HttpBasicAuthProvider.builder()
                    .addOutboundTarget(outboundTarget("oidc-token", tokenEndpointUri, tenantConfig));

            if (introspectUri != null) {
                httpBasicAuthBuilder.addOutboundTarget(outboundTarget("oidc-introspect", introspectUri, tenantConfig));
            }

            HttpBasicAuthProvider httpBasicAuth = httpBasicAuthBuilder.build();
            Security tokenOutboundSecurity = Security.builder()
                    .addOutboundSecurityProvider(httpBasicAuth)
                    .build();

            webClientBuilder.addService(WebClientSecurity.create(tokenOutboundSecurity));
        }

        WebClient appWebClient = webClientBuilder.build();

        JwkKeys signJwk = resolveSigningJwk(tenantConfig,
                                            oidcMetadata,
                                            collector,
                                            appWebClient,
                                            webClient,
                                            tokenEndpointUri);
        return new Tenant(tenantConfig,
                          tokenEndpointUri,
                          authorizationEndpointUri,
                          logoutEndpointUri,
                          issuer,
                          appWebClient,
                          signJwk,
                          introspectUri);
    }

    private static JwkKeys resolveSigningJwk(TenantConfig tenantConfig,
                                             OidcMetadata oidcMetadata,
                                             Errors.Collector collector,
                                             WebClient appWebClient,
                                             WebClient webClient,
                                             URI tokenEndpointUri) {
        if (!tenantConfig.validateJwtWithJwk()) {
            return JwkKeys.builder().build();
        }

        JwkKeys configuredKeys = tenantConfig.tenantSignJwk().orElse(null);
        if (configuredKeys != null) {
            return requireSigningKeys(configuredKeys, false);
        }

        ResourceConfig configuredResource = tenantConfig.tenantSignJwkResource().orElse(null);
        if (configuredResource != null) {
            String description = resourceDescription("OIDC signing JWK", configuredResource);
            try {
                JwkKeys keys = JwkKeys.builder()
                        .resource(ResilientResource.create(description,
                                                           configuredResource,
                                                           tenantConfig.jwkRetryConfig().overallTimeout()))
                        .build();
                return requireSigningKeys(keys, true);
            } catch (ResilientValue.UnavailableException e) {
                throw e;
            } catch (ResourceException e) {
                throw ResilientValue.unavailable(description + " could not be read", e);
            } catch (JsonException e) {
                String detail = hasCause(e, IOException.class)
                        ? " could not be read"
                        : " does not contain valid JSON";
                throw ResilientValue.unavailable(description + detail, e);
            } catch (JwtException e) {
                throw ResilientValue.unavailable(description + " does not contain usable verification keys", e);
            }
        }

        String serverType = tenantConfig.serverType();
        String jwksMetaKey = OidcUtil.resolveMetaKey("jwks_uri", serverType, tenantConfig.identityUri());
        URI jwkUri = oidcMetadata.getOidcEndpoint(collector, null, jwksMetaKey, null);
        if (collector.hasFatal()) {
            if (oidcMetadata.reloadable()) {
                collector.clear();
                throw ResilientValue.unavailable("OIDC metadata does not contain a usable JWK endpoint");
            }
            collector.clear();
            return JwkKeys.builder().build();
        }

        try {
            JwkKeys keys;
            if ("idcs".equals(serverType)) {
                keys = IdcsSupport.signJwk(appWebClient,
                                           webClient,
                                           tokenEndpointUri,
                                           jwkUri,
                                           tenantConfig.clientTimeout(),
                                           tenantConfig);
            } else {
                keys = JwkKeys.builder()
                        .json(webClient.get()
                                      .uri(jwkUri)
                                      .requestEntity(JsonObject.class))
                        .build();
            }
            return requireSigningKeys(keys, true);
        } catch (ResilientValue.UnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ResilientValue.unavailable("OIDC signing JWK is unavailable", e);
        }
    }

    private static JwkKeys requireSigningKeys(JwkKeys keys, boolean mayBecomeAvailable) {
        if (!keys.keys().isEmpty()) {
            return keys;
        }
        if (mayBecomeAvailable) {
            throw ResilientValue.unavailable("OIDC signing JWK contains no usable keys");
        }
        throw new IllegalArgumentException("Configured OIDC signing JWK must contain at least one usable key");
    }

    private static JsonObject resolveMetadata(JsonObject configuredMetadata,
                                              ResourceConfig resourceConfig,
                                              Duration ioTimeout) {
        if (resourceConfig == null) {
            return configuredMetadata;
        }
        String description = resourceDescription("OIDC metadata", resourceConfig);
        try (var stream = ResilientResource.create(description, resourceConfig, ioTimeout).stream()) {
            return JsonParser.create(stream).readJsonObject();
        } catch (ResourceException e) {
            throw ResilientValue.unavailable(description + " could not be read", e);
        } catch (JsonException e) {
            String detail = hasCause(e, IOException.class)
                    ? " could not be read"
                    : " does not contain valid JSON";
            throw ResilientValue.unavailable(description + detail, e);
        } catch (IOException e) {
            throw ResilientValue.unavailable(description + " could not be closed", e);
        }
    }

    private static String resourceDescription(String valueDescription, ResourceConfig resourceConfig) {
        if (resourceConfig.path().isPresent()) {
            return valueDescription + " filesystem source";
        }
        if (resourceConfig.uri().isPresent()) {
            return valueDescription + " URI source";
        }
        return valueDescription + " resource";
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static OutboundTarget outboundTarget(String name, URI endpointUri, TenantConfig tenantConfig) {
        String scheme = endpointUri.getScheme();
        String host = endpointUri.getHost();
        if (scheme == null || host == null) {
            throw new SecurityException("OIDC endpoint URI must be absolute with scheme and host when using "
                                                + OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC);
        }
        String path = endpointUri.getPath();
        return OutboundTarget.builder(name)
                .addTransport(scheme)
                .addHost(host)
                .addPath(Pattern.quote(path == null || path.isEmpty() ? "/" : path))
                .addMethod("POST")
                .customObject(HttpBasicOutboundConfig.class,
                              HttpBasicOutboundConfig.create(tenantConfig.clientId(), tenantConfig.clientSecret()))
                .build();
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
     * client credentials are scoped to POST requests on the token endpoint scheme, host, and path and, when JWT
     * introspection is used, to POST requests on the introspection endpoint scheme, host, and path.
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
