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
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.Builder;
import io.helidon.common.Errors;
import io.helidon.common.LazyValue;
import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceConfig;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.Timeout;
import io.helidon.faulttolerance.TimeoutConfig;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;

/**
 * Base builder of the OIDC config components.
 *
 * @param <B> type of the builder
 * @param <T> type of the object built by this builder
 */
@Configured // as this class is configured, we reference it in the config metadata documentation, so it must be public
public abstract class BaseBuilder<B extends BaseBuilder<B, T>, T> implements Builder<B, T> {

    static final String DEFAULT_SERVER_TYPE = "@default";
    static final String DEFAULT_BASE_SCOPES = "openid";
    static final String DEFAULT_REALM = "helidon";
    static final boolean DEFAULT_JWT_VALIDATE_JWK = true;
    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final Duration DEFAULT_JWK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_JWK_RETRY_OVERALL_TIMEOUT = Duration.ofSeconds(11);
    private static final RetryConfig DEFAULT_JWK_RETRY_CONFIG = RetryConfig.builder()
            .name("oidc-jwk-retry")
            .calls(2)
            .overallTimeout(DEFAULT_JWK_RETRY_OVERALL_TIMEOUT)
            .addApplyOn(ResilientValue.UnavailableException.class)
            .buildPrototype();
    private static final CircuitBreakerConfig DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG = CircuitBreakerConfig.builder()
            .name("oidc-jwk-circuit-breaker")
            .volume(1)
            .errorRatio(100)
            .addApplyOn(ResilientValue.UnavailableException.class)
            .buildPrototype();
    private static final TimeoutConfig DEFAULT_JWK_TIMEOUT_CONFIG = TimeoutConfig.builder()
            .name("oidc-jwk-timeout")
            .timeout(DEFAULT_JWK_TIMEOUT)
            .currentThread(true)
            .buildPrototype();

    private JsonObject oidcMetadata;
    private ResourceConfig oidcMetadataResource;
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
    private ResourceConfig signJwkResource;
    private RetryConfig jwkRetryConfig = DEFAULT_JWK_RETRY_CONFIG;
    private Retry jwkRetry;
    private Supplier<? extends Retry> jwkRetrySupplier;
    private CircuitBreakerConfig jwkCircuitBreakerConfig = DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG;
    private CircuitBreaker jwkCircuitBreaker;
    private Supplier<? extends CircuitBreaker> jwkCircuitBreakerSupplier;
    private TimeoutConfig jwkTimeoutConfig = DEFAULT_JWK_TIMEOUT_CONFIG;
    private Timeout jwkTimeout;
    private Supplier<? extends Timeout> jwkTimeoutSupplier;
    private JwkKeys contentKeyDecryptionKeys;
    private boolean validateJwtWithJwk = DEFAULT_JWT_VALIDATE_JWK;
    private URI introspectUri;
    private String scopeAudience;
    private boolean useWellKnown = true;
    // Whether audience claim is optional (turned off by default)
    private boolean optionalAudience = false;
    // Whether to check audience claim (turned on by default)
    private boolean checkAudience = true;

    BaseBuilder() {
    }

    static Retry defaultJwkRetry() {
        return RetryConfig.builder(DEFAULT_JWK_RETRY_CONFIG).build();
    }

    static CircuitBreaker defaultJwkCircuitBreaker() {
        return CircuitBreakerConfig.builder(DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG).build();
    }

    static Timeout defaultJwkTimeout() {
        return TimeoutConfig.builder(DEFAULT_JWK_TIMEOUT_CONFIG).build();
    }

    void buildConfiguration() {
        this.serverType = OidcUtil.fixServerType(serverType);

        Errors.Collector collector = Errors.collector();

        OidcUtil.validateExists(collector, clientId, "Client Id", "client-id");
        if (tokenEndpointAuthentication != OidcConfig.ClientAuthentication.CLIENT_CERTIFICATE || serverType.equals("idcs")) {
            //This client secret validation should happen in case when token endpoint authentication is not client certificate
            // OR the server is not IDCS. It will be needed for IDCS later even with client certificate. IDCS does not support
            //client certificate authentication when exchanging code to token.
            OidcUtil.validateExists(collector, clientSecret, "Client Secret", "client-secret");
        }
        OidcUtil.validateExists(collector, identityUri, "Identity URI", "identity-uri");
        validateAbsoluteUri(collector, identityUri, "identity-uri");
        validateAbsoluteUri(collector, authorizationEndpointUri, "authorization-endpoint-uri");
        validateAbsoluteUri(collector, logoutEndpointUri, "logout-endpoint-uri");
        validateAbsoluteUri(collector, tokenEndpointUri, "token-endpoint-uri");
        validateAbsoluteUri(collector, introspectUri, "introspect-endpoint-uri");
        if (jwkLoadingLazy()) {
            validateJwkFaultTolerance(collector);
        }

        if (audience == null && !optionalAudience && identityUri != null) {
            this.audience = identityUri.toString();
        }
        validateMetadata(collector);
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
        config.get("oidc-metadata.resource").as(ResourceConfig::create).ifPresent(this::oidcMetadata);
        config.get("base-scopes").asString().ifPresent(this::baseScopes);
        config.get("oidc-metadata-well-known").asBoolean().ifPresent(this::oidcMetadataWellKnown);

        config.get("scope-audience").asString().ifPresent(this::scopeAudience);
        config.get("token-endpoint-auth").asString()
                .map(String::toUpperCase)
                .map(OidcConfig.ClientAuthentication::valueOf)
                .ifPresent(this::tokenEndpointAuthentication);
        config.get("authorization-endpoint-uri").as(URI.class).ifPresent(this::authorizationEndpointUri);
        config.get("token-endpoint-uri").as(URI.class).ifPresent(this::tokenEndpointUri);
        config.get("logout-endpoint-uri").as(URI.class).ifPresent(this::logoutEndpointUri);

        config.get("sign-jwk.resource").as(ResourceConfig::create).ifPresent(this::signJwk);
        RetryConfig inheritedRetryConfig = jwkRetryConfig == null ? DEFAULT_JWK_RETRY_CONFIG : jwkRetryConfig;
        TimeoutConfig inheritedTimeoutConfig = jwkTimeoutConfig == null ? DEFAULT_JWK_TIMEOUT_CONFIG : jwkTimeoutConfig;
        CircuitBreakerConfig inheritedCircuitBreakerConfig = jwkCircuitBreakerConfig == null
                ? DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG
                : jwkCircuitBreakerConfig;
        config.get("jwk-loader.retry")
                .as(it -> mergeRetryConfig(inheritedRetryConfig, it))
                .ifPresent(this::jwkRetry);
        config.get("jwk-loader.timeout")
                .as(it -> TimeoutConfig.builder(inheritedTimeoutConfig).config(it).buildPrototype())
                .ifPresent(this::jwkTimeout);
        config.get("jwk-loader.circuit-breaker")
                .as(it -> CircuitBreakerConfig.builder(inheritedCircuitBreakerConfig).config(it).buildPrototype())
                .ifPresent(this::jwkCircuitBreaker);
        config.get("decryption-keys.resource").as(Resource::create).ifPresent(this::decryptionKeys);

        config.get("introspect-endpoint-uri").as(URI.class).ifPresent(this::introspectEndpointUri);
        DeprecatedConfig.get(config, "validate-jwt-with-jwk", "validate-with-jwk")
                .asBoolean().ifPresent(this::validateJwtWithJwk);
        config.get("issuer").asString().ifPresent(this::issuer);
        config.get("audience").asString().ifPresent(this::audience);

        // type of the identity server
        // now uses hardcoded switch - should change to service loader eventually
        config.get("server-type").asString().ifPresent(this::serverType);

        config.get("client-timeout-millis").asLong().ifPresent(this::clientTimeoutMillis);
        config.get("optional-audience").asBoolean().ifPresent(this::optionalAudience);
        config.get("check-audience").asBoolean().ifPresent(this::checkAudience);
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
        this.signJwkResource = null;
        try {
            this.signJwk = requireSigningKeys(JwkKeys.builder()
                                                          .resource(Objects.requireNonNull(resource))
                                                          .build());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Configured OIDC signing JWK is invalid", e);
        }
        return identity();
    }

    /**
     * Resource configuration pointing to JWK with public keys of signing certificates used to validate JWT.
     * Classpath and inline resources are loaded immediately. File system paths and URIs are loaded lazily and may
     * recover if they become available later.
     *
     * @param resourceConfig resource configuration pointing to the JWK
     * @return updated builder instance
     */
    public B signJwk(ResourceConfig resourceConfig) {
        validateJwtWithJwk(true);
        validateResourceConfig(Objects.requireNonNull(resourceConfig), "OIDC signing JWK");
        if (isDynamic(resourceConfig)) {
            this.signJwk = null;
            this.signJwkResource = resourceConfig;
        } else {
            this.signJwkResource = null;
            try {
                this.signJwk = requireSigningKeys(JwkKeys.builder()
                                                              .resource(Resource.create(resourceConfig))
                                                              .build());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Configured OIDC signing JWK is invalid", e);
            }
        }
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
        this.signJwkResource = null;
        this.signJwk = requireSigningKeys(Objects.requireNonNull(jwk));
        return identity();
    }

    /**
     * Retry used while loading OIDC metadata and signing JWKs; by default, it wraps two timeout-guarded attempts
     * within an 11-second overall timeout.
     *
     * @param jwkRetry retry to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "jwk-loader.retry", type = Retry.class)
    public B jwkRetry(Retry jwkRetry) {
        this.jwkRetry = Objects.requireNonNull(jwkRetry);
        this.jwkRetryConfig = jwkRetry.prototype();
        this.jwkRetrySupplier = null;
        return identity();
    }

    /**
     * Retry used while loading OIDC metadata and signing JWK.
     * The supplier is invoked only when a reloadable tenant source requires the retry.
     *
     * @param jwkRetry prototype of retry to use
     * @return updated builder instance
     */
    public B jwkRetry(RetryConfig jwkRetry) {
        this.jwkRetryConfig = Objects.requireNonNull(jwkRetry);
        this.jwkRetry = null;
        this.jwkRetrySupplier = null;
        return identity();
    }

    /**
     * Retry used while loading OIDC metadata and signing JWK.
     *
     * @param consumer consumer of builder of retry to use
     * @return updated builder instance
     */
    public B jwkRetry(Consumer<RetryConfig.Builder> consumer) {
        Objects.requireNonNull(consumer);
        var builder = RetryConfig.builder(DEFAULT_JWK_RETRY_CONFIG);
        consumer.accept(builder);
        return jwkRetry(builder.buildPrototype());
    }

    /**
     * Retry used while loading OIDC metadata and signing JWK.
     *
     * @param supplier supplier of retry to use
     * @return updated builder instance
     */
    public B jwkRetry(Supplier<? extends Retry> supplier) {
        this.jwkRetrySupplier = Objects.requireNonNull(supplier);
        this.jwkRetryConfig = null;
        this.jwkRetry = null;
        return identity();
    }

    /**
     * Timeout applied to each attempt to load OIDC metadata and signing JWKs; it defaults to 5 seconds, must be
     * positive, must execute on the current thread, and must not exceed the retry overall timeout. Current-thread
     * execution ensures that a retry cannot overlap an attempt that is still unwinding after an interrupt. The
     * deadline interrupts the loader; prompt termination also depends on the underlying I/O honoring interruption or
     * enforcing its own timeout.
     *
     * @param jwkTimeout timeout to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "jwk-loader.timeout", type = Timeout.class)
    public B jwkTimeout(Timeout jwkTimeout) {
        this.jwkTimeout = Objects.requireNonNull(jwkTimeout);
        this.jwkTimeoutConfig = jwkTimeout.prototype();
        this.jwkTimeoutSupplier = null;
        return identity();
    }

    /**
     * Timeout applied to each attempt to load OIDC metadata and signing JWK.
     * The supplier is invoked only when a reloadable tenant source requires the timeout.
     *
     * @param jwkTimeout prototype of timeout to use
     * @return updated builder instance
     */
    public B jwkTimeout(TimeoutConfig jwkTimeout) {
        this.jwkTimeoutConfig = Objects.requireNonNull(jwkTimeout);
        this.jwkTimeout = null;
        this.jwkTimeoutSupplier = null;
        return identity();
    }

    /**
     * Timeout applied to each attempt to load OIDC metadata and signing JWK.
     *
     * @param consumer consumer of builder of timeout to use
     * @return updated builder instance
     */
    public B jwkTimeout(Consumer<TimeoutConfig.Builder> consumer) {
        Objects.requireNonNull(consumer);
        var builder = TimeoutConfig.builder(DEFAULT_JWK_TIMEOUT_CONFIG);
        consumer.accept(builder);
        return jwkTimeout(builder.buildPrototype());
    }

    /**
     * Timeout applied to each attempt to load OIDC metadata and signing JWK.
     *
     * @param supplier supplier of timeout to use
     * @return updated builder instance
     */
    public B jwkTimeout(Supplier<? extends Timeout> supplier) {
        this.jwkTimeoutSupplier = Objects.requireNonNull(supplier);
        this.jwkTimeoutConfig = null;
        this.jwkTimeout = null;
        return identity();
    }

    /**
     * Circuit breaker around each complete retry batch used to load OIDC metadata and signing JWKs; by default, the
     * circuit opens after one exhausted batch and permits a recovery probe after 5 seconds.
     *
     * @param jwkCircuitBreaker circuit breaker to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "jwk-loader.circuit-breaker", type = CircuitBreaker.class)
    public B jwkCircuitBreaker(CircuitBreaker jwkCircuitBreaker) {
        this.jwkCircuitBreaker = Objects.requireNonNull(jwkCircuitBreaker);
        this.jwkCircuitBreakerConfig = jwkCircuitBreaker.prototype();
        this.jwkCircuitBreakerSupplier = null;
        return identity();
    }

    /**
     * Circuit breaker used while loading OIDC metadata and signing JWK.
     * The supplier is invoked only when a reloadable tenant source requires the circuit breaker.
     *
     * @param jwkCircuitBreaker prototype of circuit breaker to use
     * @return updated builder instance
     */
    public B jwkCircuitBreaker(CircuitBreakerConfig jwkCircuitBreaker) {
        this.jwkCircuitBreakerConfig = Objects.requireNonNull(jwkCircuitBreaker);
        this.jwkCircuitBreaker = null;
        this.jwkCircuitBreakerSupplier = null;
        return identity();
    }

    /**
     * Circuit breaker used while loading OIDC metadata and signing JWK.
     *
     * @param consumer consumer of builder of circuit breaker to use
     * @return updated builder instance
     */
    public B jwkCircuitBreaker(Consumer<CircuitBreakerConfig.Builder> consumer) {
        Objects.requireNonNull(consumer);
        var builder = CircuitBreakerConfig.builder(DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG);
        consumer.accept(builder);
        return jwkCircuitBreaker(builder.buildPrototype());
    }

    /**
     * Circuit breaker used while loading OIDC metadata and signing JWK.
     *
     * @param supplier supplier of circuit breaker to use
     * @return updated builder instance
     */
    public B jwkCircuitBreaker(Supplier<? extends CircuitBreaker> supplier) {
        this.jwkCircuitBreakerSupplier = Objects.requireNonNull(supplier);
        this.jwkCircuitBreakerConfig = null;
        this.jwkCircuitBreaker = null;
        return identity();
    }

    /**
     * Type of authentication to use when invoking the token endpoint.
     * With {@link io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication#CLIENT_SECRET_BASIC},
     * credentials are sent only to POST requests on the resolved token endpoint scheme, host, and path and, when
     * JWT introspection is used, to POST requests on the resolved introspection endpoint scheme, host, and path.
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
    @ConfiguredOption(key = "token-endpoint-auth",
                      value = "CLIENT_SECRET_BASIC",
                      description = "Type of authentication to use when invoking the token endpoint. With "
                              + "CLIENT_SECRET_BASIC, credentials are sent only to POST requests on the resolved token "
                              + "endpoint scheme, host, and path and, when JWT introspection is used, to POST requests "
                              + "on the resolved introspection endpoint scheme, host, and path.")
    public B tokenEndpointAuthentication(OidcConfig.ClientAuthentication tokenEndpointAuthentication) {

        switch (tokenEndpointAuthentication) {
        case CLIENT_SECRET_BASIC:
        case CLIENT_SECRET_POST:
        case CLIENT_CERTIFICATE:
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
        this.oidcMetadataResource = null;
        try (var resourceStream = resource.stream()) {
            return oidcMetadataJsonObject(JsonParser.create(resourceStream).readJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close input stream on resource: " + resource, e);
        }
    }

    /**
     * Resource configuration for OIDC metadata. Classpath and inline resources are loaded immediately. File system
     * paths and URIs are loaded lazily and may recover if they become available later.
     *
     * @param resourceConfig resource configuration pointing to the JSON structure
     * @return updated builder instance
     */
    public B oidcMetadata(ResourceConfig resourceConfig) {
        validateResourceConfig(Objects.requireNonNull(resourceConfig), "OIDC metadata");
        if (isDynamic(resourceConfig)) {
            this.oidcMetadata = null;
            this.oidcMetadataResource = resourceConfig;
            return identity();
        }
        this.oidcMetadataResource = null;
        try {
            return oidcMetadata(Resource.create(resourceConfig));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Configured OIDC metadata is invalid", e);
        }
    }

    /**
     * Helidon JSON with the OIDC metadata.
     *
     * @param metadata metadata JSON
     * @return updated builder instance
     * @see #oidcMetadata(Resource)
     */
    public B oidcMetadataJsonObject(JsonObject metadata) {
        this.oidcMetadataResource = null;
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

    /**
     * Allow audience claim to be optional.
     *
     * @param optional whether the audience claim is optional ({@code true}) or not ({@code false})
     * @return updated builder instance
     */
    @ConfiguredOption("false")
    public B optionalAudience(boolean optional) {
        this.optionalAudience = optional;
        return identity();
    }

    /**
     * Configure audience claim check.
     *
     * @param checkAudience whether the audience claim will be checked ({@code true}) or not ({@code false})
     * @return updated builder instance
     */
    @ConfiguredOption("true")
    public B checkAudience(boolean checkAudience) {
        this.checkAudience = checkAudience;
        return identity();
    }

    /**
     * A resource pointing to JWK with private keys used for JWE content key decryption.
     *
     * @param resource Resource pointing to the JWK
     * @return updated builder instance
     */
    @ConfiguredOption(key = "decryption-keys.resource")
    public B decryptionKeys(Resource resource) {
        this.contentKeyDecryptionKeys = JwkKeys.builder().resource(resource).build();
        return identity();
    }

    /**
     * Set {@link JwkKeys} used for JWE content key decryption.
     *
     * @param contentKeyDecryptionKeys JwkKeys instance to get private key for JWE content key decryption
     * @return updated builder instance
     */
    public B decryptionKeys(JwkKeys contentKeyDecryptionKeys) {
        this.contentKeyDecryptionKeys = contentKeyDecryptionKeys;
        return identity();
    }

    OidcConfig.ClientAuthentication tokenEndpointAuthentication() {
        return tokenEndpointAuthentication;
    }

    JsonObject oidcMetadataJsonObject() {
        return oidcMetadata;
    }

    ResourceConfig oidcMetadataResource() {
        return oidcMetadataResource;
    }

    boolean useWellKnown() {
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

    boolean checkAudience() {
        return checkAudience;
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

    ResourceConfig signJwkResource() {
        return signJwkResource;
    }

    LazyValue<Retry> jwkRetry() {
        Retry instance = jwkRetry;
        Supplier<? extends Retry> supplier = jwkRetrySupplier;
        RetryConfig prototype = jwkRetryConfig;
        return LazyValue.create(() -> {
            if (instance != null) {
                return instance;
            }
            if (supplier != null) {
                return Objects.requireNonNull(supplier.get());
            }
            return RetryConfig.builder(prototype).build();
        });
    }

    LazyValue<Timeout> jwkTimeout() {
        Timeout instance = jwkTimeout;
        Supplier<? extends Timeout> supplier = jwkTimeoutSupplier;
        TimeoutConfig prototype = jwkTimeoutConfig;
        return LazyValue.create(() -> {
            if (instance != null) {
                return instance;
            }
            if (supplier != null) {
                return Objects.requireNonNull(supplier.get());
            }
            return TimeoutConfig.builder(prototype).build();
        });
    }

    LazyValue<CircuitBreaker> jwkCircuitBreaker() {
        CircuitBreaker instance = jwkCircuitBreaker;
        Supplier<? extends CircuitBreaker> supplier = jwkCircuitBreakerSupplier;
        CircuitBreakerConfig prototype = jwkCircuitBreakerConfig;
        return LazyValue.create(() -> {
            if (instance != null) {
                return instance;
            }
            if (supplier != null) {
                return Objects.requireNonNull(supplier.get());
            }
            return CircuitBreakerConfig.builder(prototype).build();
        });
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

    JwkKeys contentKeyDecryptionKeys() {
        return contentKeyDecryptionKeys;
    }

    private static RetryConfig mergeRetryConfig(RetryConfig inheritedConfig, Config config) {
        var builder = RetryConfig.builder(inheritedConfig);
        boolean delayFactorConfigured = config.get("delay-factor").exists();
        boolean jitterConfigured = config.get("jitter").exists();
        boolean jitterFactorConfigured = config.get("jitter-factor").exists();
        if (delayFactorConfigured || jitterConfigured || jitterFactorConfigured) {
            if (!delayFactorConfigured) {
                builder.delayFactor(-1);
            }
            if (!jitterConfigured) {
                builder.jitter(Duration.ofSeconds(-1));
            }
            if (!jitterFactorConfigured) {
                builder.jitterFactor(-1);
            }
        }
        return builder.config(config).buildPrototype();
    }

    private static JwkKeys requireSigningKeys(JwkKeys keys) {
        if (keys.keys().isEmpty()) {
            throw new IllegalArgumentException("Configured OIDC signing JWK must contain at least one usable key");
        }
        return keys;
    }

    private static boolean isDynamic(ResourceConfig resourceConfig) {
        return resourceConfig.path().isPresent() || resourceConfig.uri().isPresent();
    }

    private static void validateResourceConfig(ResourceConfig resourceConfig, String description) {
        int selectors = 0;
        selectors += resourceConfig.path().isPresent() ? 1 : 0;
        selectors += resourceConfig.resourcePath().isPresent() ? 1 : 0;
        selectors += resourceConfig.uri().isPresent() ? 1 : 0;
        selectors += resourceConfig.contentPlain().isPresent() ? 1 : 0;
        selectors += resourceConfig.content().isPresent() ? 1 : 0;
        if (selectors != 1) {
            throw new IllegalArgumentException(description + " resource must configure exactly one source");
        }

        if (resourceConfig.proxyHost().isPresent()) {
            if (resourceConfig.uri().isEmpty()) {
                throw new IllegalArgumentException(description + " proxy can only be configured for a URI resource");
            }
            if (resourceConfig.proxyHost().orElseThrow().isBlank()) {
                throw new IllegalArgumentException(description + " proxy host must not be blank");
            }
            int proxyPort = resourceConfig.proxyPort();
            if (proxyPort < 1 || proxyPort > 65_535) {
                throw new IllegalArgumentException(description + " proxy port must be between 1 and 65535");
            }
        }
        if (resourceConfig.proxy().isPresent() && resourceConfig.uri().isEmpty()) {
            throw new IllegalArgumentException(description + " proxy can only be configured for a URI resource");
        }
        resourceConfig.uri().ifPresent(uri -> validateResourceUri(uri, description));
    }

    private static void validateAbsoluteUri(Errors.Collector collector, URI uri, String key) {
        if (uri != null) {
            if (!uri.isAbsolute()) {
                collector.fatal(key + " must be an absolute URI");
            } else if (!isHttpUri(uri)) {
                collector.fatal(key + " must use HTTP or HTTPS");
            } else if (uri.getHost() == null) {
                collector.fatal(key + " HTTP URI must include a host");
            }
        }
    }

    private static void validateResourceUri(URI uri, String description) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(description + " resource URI must be absolute");
        }
        try {
            uri.toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException(description + " resource URI uses an unsupported scheme", e);
        }
        if (httpUriWithoutHost(uri)) {
            throw new IllegalArgumentException(description + " resource HTTP URI must include a host");
        }
    }

    private static boolean httpUriWithoutHost(URI uri) {
        return isHttpUri(uri) && uri.getHost() == null;
    }

    private static boolean isHttpUri(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private void clientTimeoutMillis(long millis) {
        this.clientTimeout(Duration.ofMillis(millis));
    }

    private boolean jwkLoadingLazy() {
        boolean metadataLoadingLazy = oidcMetadataResource != null
                || (oidcMetadata == null && useWellKnown);
        if (metadataLoadingLazy || !validateJwtWithJwk) {
            return metadataLoadingLazy;
        }
        if (signJwkResource != null) {
            return true;
        }
        if (signJwk != null || oidcMetadata == null) {
            return false;
        }
        String key = OidcUtil.resolveMetaKey("jwks_uri", serverType, identityUri);
        return oidcMetadata.stringValue(key).isPresent();
    }

    private void validateJwkFaultTolerance(Errors.Collector collector) {
        RetryConfig retryConfig = jwkRetryConfig;
        TimeoutConfig timeoutConfig = jwkTimeoutConfig;
        if (retryConfig == null || timeoutConfig == null) {
            return;
        }
        Duration timeout = timeoutConfig.timeout();
        if (timeout.isNegative() || timeout.isZero()) {
            collector.fatal("jwk-loader.timeout.timeout must be positive");
        }
        if (!timeoutConfig.currentThread()) {
            collector.fatal("jwk-loader.timeout.current-thread must be true");
        }
        if (timeout.compareTo(retryConfig.overallTimeout()) > 0) {
            collector.fatal("jwk-loader.timeout.timeout must not exceed jwk-loader.retry.overall-timeout");
        }
    }

    private void validateMetadata(Errors.Collector collector) {
        if (oidcMetadata == null) {
            return;
        }
        validateMetadataUri(collector, "authorization_endpoint");
        validateMetadataUri(collector, OidcUtil.resolveMetaKey("token_endpoint", serverType, identityUri));
        validateMetadataUri(collector, OidcUtil.resolveMetaKey("end_session_endpoint", serverType, identityUri));
        validateMetadataUri(collector, OidcUtil.resolveMetaKey("introspection_endpoint", serverType, identityUri));
        validateMetadataUri(collector, OidcUtil.resolveMetaKey("jwks_uri", serverType, identityUri));
    }

    private void validateMetadataUri(Errors.Collector collector, String key) {
        oidcMetadata.stringValue(key).ifPresent(value -> {
            try {
                URI uri = URI.create(value);
                if (!uri.isAbsolute()) {
                    collector.fatal("OIDC metadata field \"" + key + "\" must be an absolute URI");
                } else if (!isHttpUri(uri)) {
                    collector.fatal("OIDC metadata field \"" + key + "\" must use HTTP or HTTPS");
                } else if (uri.getHost() == null) {
                    collector.fatal("OIDC metadata field \"" + key + "\" HTTP URI must include a host");
                }
            } catch (IllegalArgumentException _) {
                collector.fatal("OIDC metadata field \"" + key + "\" must be a valid URI");
            }
        });
    }

}
