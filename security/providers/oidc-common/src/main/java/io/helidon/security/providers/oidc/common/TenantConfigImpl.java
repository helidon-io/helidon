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
import java.util.logging.Logger;

import io.helidon.security.jwt.jwk.JwkKeys;

import jakarta.json.JsonObject;

/**
 * Tenant configuration.
 */
class TenantConfigImpl implements TenantConfig {

    private static final Logger LOGGER = Logger.getLogger(TenantConfigImpl.class.getName());

    private final URI authorizationEndpointUri;
    private final String clientId;
    private final URI identityUri;
    private final URI tokenEndpointUri;
    private final String baseScopes;
    private final boolean validateJwtWithJwk;
    private final String issuer;
    private final String audience;
    private final String realm;
    private final OidcConfig.ClientAuthentication tokenEndpointAuthentication;
    private final Duration clientTimeout;
    private final JwkKeys signJwk;
    private final String clientSecret;
    private final URI introspectUri;
    private final URI logoutEndpointUri;
    private final String scopeAudience;
    private final String serverType;
    private final JsonObject oidcMetadata;
    private final boolean useWellKnown;
    private final String name;

    TenantConfigImpl(BaseBuilder<?, ?> builder) {
        this.name = builder.name();
        this.clientId = builder.clientId();
        this.baseScopes = builder.baseScopes();
        this.validateJwtWithJwk = builder.validateJwtWithJwk();
        this.issuer = builder.issuer();
        this.audience = builder.audience();
        this.identityUri = builder.identityUri();
        this.realm = builder.realm();
        this.tokenEndpointUri = builder.tokenEndpointUri();
        this.tokenEndpointAuthentication = builder.tokenEndpointAuthentication();
        this.clientTimeout = builder.clientTimeout();
        this.authorizationEndpointUri = builder.authorizationEndpointUri();
        this.logoutEndpointUri = builder.logoutEndpointUri();
        this.serverType = builder.serverType();

        this.clientSecret = builder.clientSecret();
        this.signJwk = builder.signJwk();
        this.oidcMetadata = builder.oidcMetadata();
        this.useWellKnown = builder.useWellKnown();

        if (validateJwtWithJwk) {
            this.introspectUri = null;
        } else {
            this.introspectUri = builder.introspectUri();
        }

        if ((builder.scopeAudience() == null) || builder.scopeAudience().trim().isEmpty()) {
            this.scopeAudience = "";
        } else {
            String tmp = builder.scopeAudience().trim();
            if (tmp.endsWith("/")) {
                this.scopeAudience = tmp;
            } else {
                this.scopeAudience = tmp + "/";
            }
        }

        LOGGER.finest(() -> "OIDC Scope audience: " + scopeAudience);
    }

    @Override
    public Optional<JwkKeys> tenantSignJwk() {
        return Optional.ofNullable(signJwk);
    }

    @Override
    public Optional<URI> tenantLogoutEndpointUri() {
        return Optional.ofNullable(logoutEndpointUri);
    }

    @Override
    public Optional<URI> tenantTokenEndpointUri() {
        return Optional.ofNullable(tokenEndpointUri);
    }

    @Override
    public String clientId() {
        return clientId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String baseScopes() {
        return baseScopes;
    }

    @Override
    public boolean validateJwtWithJwk() {
        return validateJwtWithJwk;
    }

    @Override
    public Optional<URI> tenantIntrospectUri() {
        return Optional.ofNullable(introspectUri);
    }

    @Override
    public Optional<String> tenantIssuer() {
        return Optional.ofNullable(issuer);
    }

    @Override
    public String audience() {
        return audience;
    }

    @Override
    public String scopeAudience() {
        return scopeAudience;
    }

    @Override
    public URI identityUri() {
        return identityUri;
    }

    @Override
    public String realm() {
        return realm;
    }

    @Override
    public OidcConfig.ClientAuthentication tokenEndpointAuthentication() {
        return tokenEndpointAuthentication;
    }

    @Override
    public Duration clientTimeout() {
        return clientTimeout;
    }

    @Override
    public Optional<URI> authorizationEndpoint() {
        return Optional.ofNullable(authorizationEndpointUri);
    }

    @Override
    public String clientSecret() {
        return clientSecret;
    }

    @Override
    public String serverType() {
        return serverType;
    }

    @Override
    public JsonObject oidcMetadata() {
        return oidcMetadata;
    }

    @Override
    public boolean useWellKnown() {
        return useWellKnown;
    }

}
