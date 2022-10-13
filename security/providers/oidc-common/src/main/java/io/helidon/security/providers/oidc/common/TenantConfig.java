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
import java.util.logging.Logger;

import io.helidon.common.http.FormParams;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.WebClientRequestBuilder;

/**
 * Tenant configuration.
 */
public class TenantConfig {

    private static final Logger LOGGER = Logger.getLogger(TenantConfig.class.getName());

    static final String DEFAULT_BASE_SCOPES = "openid";
    static final String DEFAULT_REALM = "helidon";
    static final boolean DEFAULT_JWT_VALIDATE_JWK = true;
    static final String DEFAULT_PARAM_NAME = "accessToken";
    static final boolean DEFAULT_PARAM_USE = false;
    static final boolean DEFAULT_HEADER_USE = false;
    static final boolean DEFAULT_COOKIE_USE = true;
    static final String DEFAULT_COOKIE_NAME = "JSESSIONID";
    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private final URI authorizationEndpointUri;
    private final String clientId;
    private final boolean useParam;
    private final String paramName;
    private final URI identityUri;
    private final URI tokenEndpointUri;
    private final boolean useHeader;
    private final TokenHandler headerHandler;
    private final boolean useCookie;
    private final OidcCookieHandler tokenCookieHandler;
    private final OidcCookieHandler idTokenCookieHandler;
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
    private final boolean oidcMetadataWellKnown;
    private final String serverType;

    TenantConfig(BaseBuilder<?, ?> builder) {
        this.clientId = builder.clientId();
        this.useParam = builder.useParam();
        this.paramName = builder.paramName();
        this.useHeader = builder.useHeader();
        this.headerHandler = builder.headerHandler();
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
        this.useCookie = builder.useCookie();
        this.oidcMetadataWellKnown = builder.oidcMetadataWellKnown();
        this.serverType = builder.serverType();

        this.tokenCookieHandler = builder.tokenCookieBuilder().build();
        this.idTokenCookieHandler = builder.idTokenCookieBuilder().build();
        this.clientSecret = builder.clientSecret();
        this.signJwk = builder.signJwk();

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

    /**
     * Create new {@link TenantConfig.Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder tenantBuilder() {
        return new Builder();
    }

    /**
     * JWK used for signature validation.
     *
     * @return set of keys used use to verify tokens
     * @see BaseBuilder#signJwk(JwkKeys)
     */
    public JwkKeys signJwk() {
        return signJwk;
    }

    /**
     * Authorization endpoint.
     *
     * @return authorization endpoint uri as a string
     * @see BaseBuilder#authorizationEndpointUri(URI)
     */
    public String authorizationEndpointUri() {
        if (authorizationEndpointUri == null) {
            return null;
        }
        return authorizationEndpointUri.toString();
    }

    /**
     * Logout endpoint on OIDC server.
     *
     * @return URI of the logout endpoint
     * @see OidcConfig.Builder#logoutEndpointUri(java.net.URI)
     */
    public URI logoutEndpointUri() {
        return logoutEndpointUri;
    }

    /**
     * Token endpoint URI.
     *
     * @return endpoint URI
     * @see BaseBuilder#tokenEndpointUri(java.net.URI)
     */
    public URI tokenEndpointUri() {
        return tokenEndpointUri;
    }

    /**
     * Whether to use query parameter to get the information from request.
     *
     * @return if query parameter should be used
     * @see BaseBuilder#useParam(Boolean)
     */
    public boolean useParam() {
        return useParam;
    }

    /**
     * Query parameter name.
     *
     * @return name of the query parameter to use
     * @see BaseBuilder#paramName(String)
     */
    public String paramName() {
        return paramName;
    }

    /**
     * Whether to use HTTP header to get the information from request.
     *
     * @return if header should be used
     * @see BaseBuilder#useHeader(Boolean)
     */
    public boolean useHeader() {
        return useHeader;
    }

    /**
     * {@link TokenHandler} to extract header information from request.
     *
     * @return handler to extract header
     * @see BaseBuilder#headerTokenHandler(TokenHandler)
     */
    public TokenHandler headerHandler() {
        return headerHandler;
    }

    /**
     * Whether to use cooke to get the information from request.
     *
     * @return if cookie should be used
     * @see OidcConfig.Builder#useCookie(Boolean)
     */
    public boolean useCookie() {
        return useCookie;
    }

    /**
     * Cookie name.
     *
     * @return name of the cookie to use
     * @see OidcConfig.Builder#cookieName(String)
     * @deprecated use {@link #tokenCookieHandler()} instead
     */
    @Deprecated(forRemoval = true, since = "2.4.0")
    public String cookieName() {
        return tokenCookieHandler.cookieName();
    }

    /**
     * Additional options of the cookie to use.
     *
     * @return cookie options to use in cookie string
     * @see OidcConfig.Builder#cookieHttpOnly(Boolean)
     * @see OidcConfig.Builder#cookieDomain(String)
     * @deprecated please use {@link #tokenCookieHandler()} instead
     */
    @Deprecated(forRemoval = true, since = "2.4.0")
    public String cookieOptions() {
        return tokenCookieHandler.createCookieOptions();
    }

    /**
     * Cookie handler to create cookies or unset cookies for token.
     *
     * @return a new cookie handler
     */
    public OidcCookieHandler tokenCookieHandler() {
        return tokenCookieHandler;
    }

    /**
     * Cookie handler to create cookies or unset cookies for id token.
     *
     * @return a new cookie handler
     */
    public OidcCookieHandler idTokenCookieHandler() {
        return idTokenCookieHandler;
    }

    /**
     * Prefix of a cookie header formed by name and "=".
     *
     * @return prefix of cookie value
     * @see OidcConfig.Builder#cookieName(String)
     * @deprecated use {@link io.helidon.security.providers.oidc.common.OidcCookieHandler} instead, this method
     *      will no longer be avilable
     */
    @Deprecated(forRemoval = true, since = "2.4.0")
    public String cookieValuePrefix() {
        return tokenCookieHandler.cookieValuePrefix();
    }

    /**
     * Client id of this client.
     *
     * @return client id
     * @see BaseBuilder#clientId(String)
     */
    public String clientId() {
        return clientId;
    }


    /**
     * Base scopes to require from OIDC server.
     *
     * @return base scopes
     * @see OidcConfig.Builder#baseScopes(String)
     */
    public String baseScopes() {
        return baseScopes;
    }

    /**
     * Whether to validate JWT with JWK information (e.g. verify signatures locally).
     *
     * @return if we should validate JWT with JWK
     * @see OidcConfig.Builder#validateJwtWithJwk(Boolean)
     */
    public boolean validateJwtWithJwk() {
        return validateJwtWithJwk;
    }

    /**
     * Introspection endpoint URI.
     *
     * @return introspection endpoint URI
     * @see OidcConfig.Builder#introspectEndpointUri(java.net.URI)
     */
    public URI introspectUri() {
        return introspectUri;
    }

    /**
     * Token issuer.
     *
     * @return token issuer
     * @see OidcConfig.Builder#issuer(String)
     */
    public String issuer() {
        return issuer;
    }

    /**
     * Expected token audience.
     *
     * @return audience
     * @see OidcConfig.Builder#audience(String)
     */
    public String audience() {
        return audience;
    }

    /**
     * Audience URI of custom scopes.
     *
     * @return scope audience
     * @see OidcConfig.Builder#scopeAudience(String)
     */
    public String scopeAudience() {
        return scopeAudience;
    }

    /**
     * Identity server URI.
     *
     * @return identity server URI
     * @see OidcConfig.Builder#identityUri(URI)
     */
    public URI identityUri() {
        return identityUri;
    }

    /**
     * Realm to use for WWW-Authenticate response (if needed).
     *
     * @return realm name
     */
    public String realm() {
        return realm;
    }

    /**
     * Type of authentication mechanism used for token endpoint.
     *
     * @return client authentication type
     */
    public OidcConfig.ClientAuthentication tokenEndpointAuthentication() {
        return tokenEndpointAuthentication;
    }

    boolean oidcMetadataWellKnown() {
        return oidcMetadataWellKnown;
    }

    /**
     * Update request that uses form params with authentication.
     *
     * @param type type of the request
     * @param request request builder
     * @param form form params builder
     */
    public void updateRequest(OidcConfig.RequestType type, WebClientRequestBuilder request, FormParams.Builder form) {
        if (type == OidcConfig.RequestType.CODE_TO_TOKEN
                && tokenEndpointAuthentication == OidcConfig.ClientAuthentication.CLIENT_SECRET_POST) {
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
        }
    }

    /**
     * Expected timeout of HTTP client operations.
     *
     * @return client timeout
     */
    public Duration clientTimeout() {
        return clientTimeout;
    }

    /**
     * Authorization endpoint.
     *
     * @return authorization endpoint uri as a string
     * @see BaseBuilder#authorizationEndpointUri(URI)
     */
    URI authorizationEndpoint() {
        return authorizationEndpointUri;
    }

    String clientSecret() {
        return clientSecret;
    }

    String serverType() {
        return serverType;
    }

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link TenantConfig}.
     */
    public static final class Builder extends BaseBuilder<Builder, TenantConfig> {

        private Builder() {
        }

        @Override
        public TenantConfig build() {
            buildConfiguration();
            return new TenantConfig(this);
        }
    }
}
