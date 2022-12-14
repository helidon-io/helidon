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
import java.time.Duration;
import java.util.Collections;

import io.helidon.common.Builder;
import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;

/**
 * Base builder of the OIDC config components.
 */
@Configured
abstract class BaseBuilder<B extends BaseBuilder<B, T>, T> implements Builder<B, T> {

    static final String DEFAULT_SERVER_TYPE = "@default";
    static final String DEFAULT_BASE_SCOPES = "openid";
    static final String DEFAULT_REALM = "helidon";
    static final boolean DEFAULT_JWT_VALIDATE_JWK = true;
    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    private JsonObject oidcMetadata;
    private OidcConfig.ClientAuthentication tokenEndpointAuthentication = OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC;
    private String clientId;
    private String clientSecret;
    private String baseScopes = DEFAULT_BASE_SCOPES;
    private String realm = DEFAULT_REALM;
    private String issuer;
    private String audience;
    private String serverType;
    private URI authorizationEndpointUri;
    private URI logoutEndpointUri;
    private URI identityUri;
    private URI tokenEndpointUri;
    private Duration clientTimeout = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
    private JwkKeys signJwk;
    private boolean validateJwtWithJwk = DEFAULT_JWT_VALIDATE_JWK;
    private URI introspectUri;
    private String scopeAudience;
    private boolean useWellKnown = true;

    BaseBuilder() {
    }

    void buildConfiguration() {
        this.serverType = OidcUtil.fixServerType(serverType);

        Errors.Collector collector = Errors.collector();

        OidcUtil.validateExists(collector, clientId, "Client Id", "client-id");
        OidcUtil.validateExists(collector, clientSecret, "Client Secret", "client-secret");
        OidcUtil.validateExists(collector, identityUri, "Identity URI", "identity-uri");

        if ((audience == null) && (identityUri != null)) {
            this.audience = identityUri.toString();
        }
        // first set of validations
        collector.collect().checkValid();
    }

    /**
     * Update this builder with values from configuration.
     *
     * @param config provided config
     * @return updated builder instance
     */
    public B config(Config config) {
        config.get("client-id").asString().ifPresent(this::clientId);
        config.get("client-secret").asString().ifPresent(this::clientSecret);
        config.get("identity-uri").as(URI.class).ifPresent(this::identityUri);

        // OIDC server configuration
        config.get("oidc-metadata.resource").as(Resource::create).ifPresent(this::oidcMetadata);
        config.get("base-scopes").asString().ifPresent(this::baseScopes);
        // backward compatibility
        config.get("oidc-metadata.resource").as(Resource::create).ifPresent(this::oidcMetadata);
        config.get("oidc-metadata-well-known").asBoolean().ifPresent(this::oidcMetadataWellKnown);

        config.get("scope-audience").asString().ifPresent(this::scopeAudience);
        config.get("token-endpoint-auth").asString()
                .map(String::toUpperCase)
                .map(OidcConfig.ClientAuthentication::valueOf)
                .ifPresent(this::tokenEndpointAuthentication);
        config.get("authorization-endpoint-uri").as(URI.class).ifPresent(this::authorizationEndpointUri);
        config.get("token-endpoint-uri").as(URI.class).ifPresent(this::tokenEndpointUri);
        config.get("logout-endpoint-uri").as(URI.class).ifPresent(this::logoutEndpointUri);

        config.get("sign-jwk.resource").as(Resource::create).ifPresent(this::signJwk);

        config.get("introspect-endpoint-uri").as(URI.class).ifPresent(this::introspectEndpointUri);
        config.get("validate-with-jwk").asBoolean().ifPresent(this::validateJwtWithJwk);
        config.get("issuer").asString().ifPresent(this::issuer);
        config.get("audience").asString().ifPresent(this::audience);

        // type of the identity server
        // now uses hardcoded switch - should change to service loader eventually
        config.get("server-type").asString().ifPresent(this::serverType);

        config.get("client-timeout-millis").asLong().ifPresent(this::clientTimeoutMillis);
        return identity();
    }

    /**
     * Client ID as generated by OIDC server.
     *
     * @param clientId the client id of this application.
     * @return updated builder instance
     */
    @ConfiguredOption
    public B clientId(String clientId) {
        this.clientId = clientId;
        return identity();
    }

    /**
     * Client secret as generated by OIDC server.
     * Used to authenticate this application with the server when requesting
     * JWT based on a code.
     *
     * @param clientSecret secret to use
     * @return updated builder instance
     */
    @ConfiguredOption
    public B clientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return identity();
    }

    /**
     * URI of the identity server, base used to retrieve OIDC metadata.
     *
     * @param uri full URI of an identity server (such as "http://tenantid.identity.oraclecloud.com")
     * @return updated builder instance
     */
    @ConfiguredOption
    public B identityUri(URI uri) {
        this.identityUri = uri;
        return identity();
    }

    /**
     * Realm to return when not redirecting and an error occurs that sends back WWW-Authenticate header.
     *
     * @param realm realm name
     * @return updated builder instance
     */
    public B realm(String realm) {
        this.realm = realm;
        return identity();
    }

    /**
     * Audience of issued tokens.
     *
     * @param audience audience to validate
     * @return updated builder instance
     */
    @ConfiguredOption
    public B audience(String audience) {
        this.audience = audience;
        return identity();
    }

    /**
     * Issuer of issued tokens.
     *
     * @param issuer expected issuer to validate
     * @return updated builder instance
     */
    @ConfiguredOption
    public B issuer(String issuer) {
        this.issuer = issuer;
        return identity();
    }

    /**
     * Use JWK (a set of keys to validate signatures of JWT) to validate tokens.
     * Use this method when you want to use default values for JWK or introspection endpoint URI.
     *
     * @param useJwk when set to true, jwk is used, when set to false, introspect endpoint is used
     * @return updated builder instance
     */
    @ConfiguredOption("true")
    public B validateJwtWithJwk(Boolean useJwk) {
        this.validateJwtWithJwk = useJwk;
        return identity();
    }

    /**
     * Endpoint to use to validate JWT.
     * Either use this or set {@link #signJwk(JwkKeys)} or {@link #signJwk(Resource)}.
     *
     * @param uri URI of introspection endpoint
     * @return updated builder instance
     */
    @ConfiguredOption
    public B introspectEndpointUri(URI uri) {
        validateJwtWithJwk(false);
        this.introspectUri = uri;
        return identity();
    }

    /**
     * A resource pointing to JWK with public keys of signing certificates used
     * to validate JWT.
     *
     * @param resource Resource pointing to the JWK
     * @return updated builder instance
     */
    @ConfiguredOption(key = "sign-jwk.resource")
    public B signJwk(Resource resource) {
        validateJwtWithJwk(true);
        this.signJwk = JwkKeys.builder().resource(resource).build();
        return identity();
    }

    /**
     * Set {@link JwkKeys} to use for JWT validation.
     *
     * @param jwk JwkKeys instance to get public keys used to sign JWT
     * @return updated builder instance
     */
    public B signJwk(JwkKeys jwk) {
        validateJwtWithJwk(true);
        this.signJwk = jwk;
        return identity();
    }

    /**
     * Type of authentication to use when invoking the token endpoint.
     * Current supported options:
     * <ul>
     *     <li>{@link io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication#CLIENT_SECRET_BASIC}</li>
     *     <li>{@link io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication#CLIENT_SECRET_POST}</li>
     *     <li>{@link io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication#NONE}</li>
     * </ul>
     *
     * @param tokenEndpointAuthentication authentication type
     * @return updated builder
     */
    @ConfiguredOption(key = "token-endpoint-auth", value = "CLIENT_SECRET_BASIC")
    public B tokenEndpointAuthentication(OidcConfig.ClientAuthentication tokenEndpointAuthentication) {

        switch (tokenEndpointAuthentication) {
        case CLIENT_SECRET_BASIC:
        case CLIENT_SECRET_POST:
        case NONE:
            break;
        default:
            throw new IllegalArgumentException("Token endpoint authentication type " + tokenEndpointAuthentication
                                                       + " is not supported.");
        }
        this.tokenEndpointAuthentication = tokenEndpointAuthentication;
        return identity();
    }

    /**
     * URI of an authorization endpoint used to redirect users to for logging-in.
     *
     * If not defined, it is obtained from {@link #oidcMetadata(Resource)}, if that is not defined
     * an attempt is made to use {@link #identityUri(URI)}/oauth2/v1/authorize.
     *
     * @param uri URI to use for token endpoint
     * @return updated builder instance
     */
    @ConfiguredOption
    public B authorizationEndpointUri(URI uri) {
        this.authorizationEndpointUri = uri;
        return identity();
    }

    /**
     * URI of a logout endpoint used to redirect users to for logging-out.
     * If not defined, it is obtained from {@link #oidcMetadata(Resource)}, if that is not defined
     * an attempt is made to use {@link #identityUri(URI)}/oauth2/v1/userlogout.
     *
     * @param logoutEndpointUri URI to use to log out
     * @return updated builder instance
     */
    public B logoutEndpointUri(URI logoutEndpointUri) {
        this.logoutEndpointUri = logoutEndpointUri;
        return identity();
    }

    /**
     * URI of a token endpoint used to obtain a JWT based on the authentication
     * code.
     * If not defined, it is obtained from {@link #oidcMetadata(Resource)}, if that is not defined
     * an attempt is made to use {@link #identityUri(URI)}/oauth2/v1/token.
     *
     * @param uri URI to use for token endpoint
     * @return updated builder instance
     */
    @ConfiguredOption
    public B tokenEndpointUri(URI uri) {
        this.tokenEndpointUri = uri;
        return identity();
    }

    /**
     * Resource configuration for OIDC Metadata
     * containing endpoints to various identity services, as well as information about the identity server.
     *
     * @param resource resource pointing to the JSON structure
     * @return updated builder instance
     */
    @ConfiguredOption(key = "oidc-metadata.resource")
    public B oidcMetadata(Resource resource) {
        return oidcMetadata(JSON.createReader(resource.stream()).readObject());
    }

    /**
     * JsonObject with the OIDC Metadata.
     *
     * @param metadata metadata JSON
     * @return updated builder instance
     * @see #oidcMetadata(Resource)
     */
    public B oidcMetadata(JsonObject metadata) {
        this.oidcMetadata = metadata;
        return identity();
    }

    /**
     * Configure base scopes.
     * By default, this is {@value #DEFAULT_BASE_SCOPES}.
     * If scope has a qualifier, it must be used here.
     *
     * @param scopes Space separated scopes to be required by default from OIDC server
     * @return updated builder instance
     */
    @ConfiguredOption(value = DEFAULT_BASE_SCOPES)
    public B baseScopes(String scopes) {
        this.baseScopes = scopes;
        return identity();
    }

    /**
     * If set to true, metadata will be loaded from default (well known)
     * location, unless it is explicitly defined using oidc-metadata-resource. If set to false, it would not be loaded
     * even if oidc-metadata-resource is not defined. In such a case all URIs must be explicitly defined (e.g.
     * token-endpoint-uri).
     *
     * @param useWellKnown whether to use well known location for OIDC metadata
     * @return updated builder instance
     */
    @ConfiguredOption("true")
    public B oidcMetadataWellKnown(boolean useWellKnown) {
        this.useWellKnown = useWellKnown;
        return identity();
    }


    /**
     * Configure one of the supported types of identity servers.
     *
     * If the type does not have an explicit mapping, a warning is logged and the default implementation is used.
     *
     * @param type Type of identity server. Currently supported is {@code idcs} or not configured (for default).
     * @return updated builder instance
     */
    @ConfiguredOption(value = DEFAULT_SERVER_TYPE)
    public B serverType(String type) {
        this.serverType = type;
        return identity();
    }

    /**
     * Timeout of calls using web client.
     *
     * @param duration timeout
     * @return updated builder
     */
    @ConfiguredOption(key = "client-timeout-millis", value = "30000")
    public B clientTimeout(Duration duration) {
        this.clientTimeout = duration;
        return identity();
    }

    /**
     * Audience of the scope required by this application. This is prefixed to
     * the scope name when requesting scopes from the identity server.
     * Defaults to empty string.
     *
     * @param audience audience, if provided, end with "/" to append the scope correctly
     * @return updated builder instance
     */
    @ConfiguredOption
    public B scopeAudience(String audience) {
        this.scopeAudience = audience;
        return identity();
    }

    private void clientTimeoutMillis(long millis) {
        this.clientTimeout(Duration.ofMillis(millis));
    }

    OidcConfig.ClientAuthentication tokenEndpointAuthentication() {
        return tokenEndpointAuthentication;
    }

    JsonObject oidcMetadata() {
        return oidcMetadata;
    }

    public boolean useWellKnown() {
        return useWellKnown;
    }

    String clientId() {
        return clientId;
    }

    String clientSecret() {
        return clientSecret;
    }

    String baseScopes() {
        return baseScopes;
    }

    String realm() {
        return realm;
    }

    String issuer() {
        return issuer;
    }

    String audience() {
        return audience;
    }

    String serverType() {
        return serverType;
    }

    URI authorizationEndpointUri() {
        return authorizationEndpointUri;
    }

    URI logoutEndpointUri() {
        return logoutEndpointUri;
    }

    URI identityUri() {
        return identityUri;
    }

    URI tokenEndpointUri() {
        return tokenEndpointUri;
    }

    Duration clientTimeout() {
        return clientTimeout;
    }

    JwkKeys signJwk() {
        return signJwk;
    }

    boolean validateJwtWithJwk() {
        return validateJwtWithJwk;
    }

    URI introspectUri() {
        return introspectUri;
    }

    String scopeAudience() {
        return scopeAudience;
    }

    String name() {
        return TenantConfigFinder.DEFAULT_TENANT_ID;
    }
}
