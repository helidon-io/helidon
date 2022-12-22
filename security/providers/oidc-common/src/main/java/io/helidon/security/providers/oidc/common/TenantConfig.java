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
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.security.jwt.jwk.JwkKeys;

import jakarta.json.JsonObject;

/**
 * Tenant configuration.
 */
public interface TenantConfig {

    /**
     * Create new {@link TenantConfig.Builder} instance.
     *
     * @return new builder instance
     */
    static Builder tenantBuilder() {
        return new Builder();
    }

    /**
     * Client id of this client.
     *
     * @return client id
     */
    String clientId();

    /**
     * Name of the tenant.
     *
     * @return tenant name
     */
    String name();

    /**
     * Base scopes to require from OIDC server.
     *
     * @return base scopes
     */
    String baseScopes();

    /**
     * Whether to validate JWT with JWK information (e.g. verify signatures locally).
     *
     * @return if we should validate JWT with JWK
     */
    boolean validateJwtWithJwk();

    /**
     * Introspection endpoint URI.
     * Empty if no introspection endpoint has been provided via configuration.
     *
     * @return introspection endpoint URI
     */
    Optional<URI> tenantIntrospectUri();

    /**
     * Return provided token issuer.
     * Empty if no issuer has been provided via configuration.
     *
     * @return token issuer
     */
    Optional<String> tenantIssuer();

    /**
     * JWK used for signature validation.
     * Empty if no jwk has been provided via configuration.
     *
     * @return set of keys used to verify tokens
     */
    Optional<JwkKeys> tenantSignJwk();

    /**
     * Logout endpoint on OIDC server.
     * Empty if no logout endpoint uri has been provided via configuration.
     *
     * @return URI of the logout endpoint
     */
    Optional<URI> tenantLogoutEndpointUri();

    /**
     * Token endpoint URI.
     * Empty if no token endpoint uri has been provided via configuration.
     *
     * @return endpoint URI
     */
    Optional<URI> tenantTokenEndpointUri();

    /**
     * Expected token audience.
     *
     * @return audience
     */
    String audience();

    /**
     * Audience URI of custom scopes.
     *
     * @return scope audience
     */
    String scopeAudience();

    /**
     * Identity server URI.
     *
     * @return identity server URI
     */
    URI identityUri();

    /**
     * Realm to use for WWW-Authenticate response (if needed).
     *
     * @return realm name
     */
    String realm();

    /**
     * Type of authentication mechanism used for token endpoint.
     *
     * @return client authentication type
     */
    OidcConfig.ClientAuthentication tokenEndpointAuthentication();

    /**
     * Expected timeout of HTTP client operations.
     *
     * @return client timeout
     */
    Duration clientTimeout();

    /**
     * Authorization endpoint.
     *
     * @return authorization endpoint uri as a string
     */
    Optional<URI> authorizationEndpoint();

    /**
     * Client secret.
     *
     * @return configured client secret
     */
    String clientSecret();

    /**
     * Server type.
     *
     * @return configured server type
     */
    String serverType();

    /**
     * OIDC metadata.
     *
     * @return configured oidc metadata
     */
    JsonObject oidcMetadata();

    /**
     * Whether to use OIDC well known metadata.
     *
     * @return configured oidc metadata
     */
    boolean useWellKnown();

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link TenantConfig}.
     */
    @Configured(description = "Open ID Connect tenant configuration")
    final class Builder extends BaseBuilder<Builder, TenantConfig> {
        private static final String TENANT_IDENT = "name";

        private String name;

        private Builder() {
        }

        /**
         * Name of the tenant.
         *
         * @param name tenant name
         * @return updated builder instance
         */
        @ConfiguredOption(required = true)
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder config(Config config) {
            super.config(config);
            config.get(TENANT_IDENT).asString().ifPresent(this::name);
            return this;
        }

        @Override
        public TenantConfig build() {
            buildConfiguration();
            if (name == null) {
                throw new IllegalStateException("Every tenant need to have \"" + TENANT_IDENT + "\" specified");
            }
            return new TenantConfigImpl(this);
        }

        String name() {
            return name;
        }
    }

}
