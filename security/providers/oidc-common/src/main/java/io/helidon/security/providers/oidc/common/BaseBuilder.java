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
import java.util.Locale;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;

import io.helidon.common.Builder;
import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.common.http.SetCookie;
import io.helidon.config.Config;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.security.Security;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.util.TokenHandler;

import static io.helidon.security.providers.oidc.common.TenantConfig.DEFAULT_COOKIE_NAME;
import static io.helidon.security.providers.oidc.common.TenantConfig.DEFAULT_COOKIE_USE;

/**
 * Base builder of the OIDC config components.
 */
abstract class BaseBuilder<B extends BaseBuilder<B, T>, T extends TenantConfig> implements Builder<T> {

    static final String DEFAULT_SERVER_TYPE = "@default";
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    @SuppressWarnings("unchecked")
    private final B me = (B) this;

    private final OidcCookieHandler.Builder tokenCookieBuilder = OidcCookieHandler.builder()
            .cookieName(DEFAULT_COOKIE_NAME);
    private final OidcCookieHandler.Builder idTokenCookieBuilder = OidcCookieHandler.builder()
            .cookieName(DEFAULT_COOKIE_NAME + "_2");
    private final OidcMetadata.Builder oidcMetadata = OidcMetadata.builder().remoteEnabled(true);
    private OidcConfig.ClientAuthentication tokenEndpointAuthentication = OidcConfig.ClientAuthentication.CLIENT_SECRET_BASIC;
    private String clientId;
    private String clientSecret;
    private String baseScopes = TenantConfig.DEFAULT_BASE_SCOPES;
    private String realm = TenantConfig.DEFAULT_REALM;
    private String issuer;
    private String audience;
    private String serverType;
    private String paramName = TenantConfig.DEFAULT_PARAM_NAME;
    private boolean useHeader = TenantConfig.DEFAULT_HEADER_USE;
    private URI authorizationEndpointUri;
    private URI logoutEndpointUri;
    private URI identityUri;
    private URI tokenEndpointUri;
    private Duration clientTimeout = Duration.ofSeconds(TenantConfig.DEFAULT_TIMEOUT_SECONDS);
    private JwkKeys signJwk;
    private boolean validateJwtWithJwk = TenantConfig.DEFAULT_JWT_VALIDATE_JWK;
    private boolean useParam = TenantConfig.DEFAULT_PARAM_USE;
    private URI introspectUri;
    private TokenHandler headerHandler = TokenHandler.builder()
            .tokenHeader("Authorization")
            .tokenPrefix("bearer ")
            .build();
    private boolean useCookie = DEFAULT_COOKIE_USE;
    private boolean cookieSameSiteDefault = true;
    private String scopeAudience;

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

    public B config(Config config) {
        config.get("client-id").asString().ifPresent(this::clientId);
        config.get("client-secret").asString().ifPresent(this::clientSecret);
        config.get("identity-uri").as(URI.class).ifPresent(this::identityUri);

        // token handling
        config.get("query-param-use").asBoolean().ifPresent(this::useParam);
        config.get("query-param-name").asString().ifPresent(this::paramName);
        config.get("header-use").asBoolean().ifPresent(this::useHeader);
        config.get("header-token").as(TokenHandler.class).ifPresent(this::headerTokenHandler);
        config.get("cookie-use").asBoolean().ifPresent(this::useCookie);
        config.get("cookie-name").asString().ifPresent(this::cookieName);
        config.get("cookie-name-id-token").asString().ifPresent(this::cookieNameIdToken);
        config.get("cookie-domain").asString().ifPresent(this::cookieDomain);
        config.get("cookie-path").asString().ifPresent(this::cookiePath);
        config.get("cookie-max-age-seconds").asLong().ifPresent(this::cookieMaxAgeSeconds);
        config.get("cookie-http-only").asBoolean().ifPresent(this::cookieHttpOnly);
        config.get("cookie-secure").asBoolean().ifPresent(this::cookieSecure);
        config.get("cookie-same-site").asString().ifPresent(this::cookieSameSite);
        // encryption of cookies
        config.get("cookie-encryption-enabled").asBoolean().ifPresent(this::cookieEncryptionEnabled);
        config.get("cookie-encryption-password").as(String.class)
                .map(String::toCharArray)
                .ifPresent(this::cookieEncryptionPassword);
        config.get("cookie-encryption-name").asString().ifPresent(this::cookieEncryptionName);

        // OIDC server configuration
        config.get("oidc-metadata.resource").as(Resource::create).ifPresent(this::oidcMetadata);
        config.get("base-scopes").asString().ifPresent(this::baseScopes);
        // backward compatibility
        Resource.create(config, "oidc-metadata").ifPresent(this::oidcMetadata);
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
        Resource.create(config, "sign-jwk").ifPresent(this::signJwk);

        config.get("introspect-endpoint-uri").as(URI.class).ifPresent(this::introspectEndpointUri);
        config.get("validate-with-jwk").asBoolean().ifPresent(this::validateJwtWithJwk);
        config.get("issuer").asString().ifPresent(this::issuer);
        config.get("audience").asString().ifPresent(this::audience);

        // type of the identity server
        // now uses hardcoded switch - should change to service loader eventually
        config.get("server-type").asString().ifPresent(this::serverType);

        config.get("client-timeout-millis").asLong().ifPresent(this::clientTimeoutMillis);
        return me;
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
        return me;
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
        return me;
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
        return me;
    }

    /**
     * Realm to return when not redirecting and an error occurs that sends back WWW-Authenticate header.
     *
     * @param realm realm name
     * @return updated builder instance
     */
    public B realm(String realm) {
        this.realm = realm;
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        return me;
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
        this.oidcMetadata.json(JSON.createReader(resource.stream()).readObject());
        return me;
    }

    /**
     * JsonObject with the OIDC Metadata.
     *
     * @param metadata metadata JSON
     * @return updated builder instance
     * @see #oidcMetadata(Resource)
     */
    public B oidcMetadata(JsonObject metadata) {
        this.oidcMetadata.json(metadata);
        return me;
    }

    /**
     * Configure base scopes.
     * By default, this is {@value TenantConfig#DEFAULT_BASE_SCOPES}.
     * If scope has a qualifier, it must be used here.
     *
     * @param scopes Space separated scopes to be required by default from OIDC server
     * @return updated builder instance
     */
    @ConfiguredOption(value = TenantConfig.DEFAULT_BASE_SCOPES)
    public B baseScopes(String scopes) {
        this.baseScopes = scopes;
        return me;
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
    public B oidcMetadataWellKnown(Boolean useWellKnown) {
        oidcMetadata.remoteEnabled(useWellKnown);
        return me;
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
        return me;
    }

    /**
     * A {@link TokenHandler} to
     * process header containing a JWT.
     * Default is "Authorization" header with a prefix "bearer ".
     *
     * @param tokenHandler token handler to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "header-token")
    public B headerTokenHandler(TokenHandler tokenHandler) {
        this.headerHandler = tokenHandler;
        return me;
    }

    /**
     * Whether to expect JWT in a header field.
     *
     * @param useHeader set to true to use a header extracted with {@link #headerTokenHandler(TokenHandler)}
     * @return updated builder instance
     */
    @ConfiguredOption(key = "header-use", value = "false")
    public B useHeader(Boolean useHeader) {
        this.useHeader = useHeader;
        return me;
    }

    /**
     * Name of a query parameter that contains the JWT token when parameter is used.
     *
     * @param paramName name of the query parameter to expect
     * @return updated builder instance
     */
    @ConfiguredOption(key = "query-param-name", value = TenantConfig.DEFAULT_PARAM_NAME)
    public B paramName(String paramName) {
        this.paramName = paramName;
        return me;
    }

    /**
     * Whether to use a query parameter to send JWT token from application to this
     * server.
     *
     * @param useParam whether to use a query parameter (true) or not (false)
     * @return updated builder instance
     * @see #paramName(String)
     */
    @ConfiguredOption(key = "query-param-use", value = "false")
    public B useParam(Boolean useParam) {
        this.useParam = useParam;
        return me;
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
        return me;
    }

    /**
     * Name of the encryption configuration available through {@link Security#encrypt(String, byte[])} and
     * {@link Security#decrypt(String, String)}.
     * If configured and encryption is enabled for any cookie,
     * Security MUST be configured in global or current {@code io.helidon.common.context.Context} (this
     * is done automatically in Helidon MP).
     *
     * @param cookieEncryptionName name of the encryption configuration in security used to encrypt/decrypt cookies
     * @return updated builder
     */
    public B cookieEncryptionName(String cookieEncryptionName) {
        this.tokenCookieBuilder.encryptionName(cookieEncryptionName);
        this.idTokenCookieBuilder.encryptionName(cookieEncryptionName);
        return me;
    }

    /**
     * Master password for encryption/decryption of cookies. This must be configured to the same value on each microservice
     * using the cookie.
     *
     * @param cookieEncryptionPassword encryption password
     * @return updated builder
     */
    public B cookieEncryptionPassword(char[] cookieEncryptionPassword) {
        this.tokenCookieBuilder.encryptionPassword(cookieEncryptionPassword);
        this.idTokenCookieBuilder.encryptionPassword(cookieEncryptionPassword);

        return me;
    }

    /**
     * Whether to encrypt token cookie created by this microservice.
     * Defaults to {@code false}.
     *
     * @param cookieEncryptionEnabled whether cookie should be encrypted {@code true}, or as obtained from
     *                               OIDC server {@code false}
     * @return updated builder instance
     */
    public B cookieEncryptionEnabled(boolean cookieEncryptionEnabled) {
        this.tokenCookieBuilder.encryptionEnabled(cookieEncryptionEnabled);
        return me;
    }

    /**
     * Whether to encrypt id token cookie created by this microservice.
     * Defaults to {@code true}.
     *
     * @param cookieEncryptionEnabled whether cookie should be encrypted {@code true}, or as obtained from
     *                               OIDC server {@code false}
     * @return updated builder instance
     */
    public B cookieEncryptionEnabledIdToken(boolean cookieEncryptionEnabled) {
        this.idTokenCookieBuilder.encryptionEnabled(cookieEncryptionEnabled);
        return me;
    }

    /**
     * When using cookie, used to set the SameSite cookie value. Can be
     * "Strict" or "Lax"
     *
     * @param sameSite SameSite cookie attribute value
     * @return updated builder instance
     */
    public B cookieSameSite(String sameSite) {
        return cookieSameSite(SetCookie.SameSite.valueOf(sameSite.toUpperCase(Locale.ROOT)));
    }

    /**
     * When using cookie, used to set the SameSite cookie value. Can be
     * "Strict" or "Lax".
     *
     * @param sameSite SameSite cookie attribute
     * @return updated builder instance
     */
    @ConfiguredOption(value = "LAX")
    public B cookieSameSite(SetCookie.SameSite sameSite) {
        this.tokenCookieBuilder.sameSite(sameSite);
        this.idTokenCookieBuilder.sameSite(sameSite);
        this.cookieSameSiteDefault = false;
        return me;
    }

    /**
     * When using cookie, if set to true, the Secure attribute will be configured.
     * Defaults to false.
     *
     * @param secure whether the cookie should be secure (true) or not (false)
     * @return updated builder instance
     */
    @ConfiguredOption("false")
    public B cookieSecure(Boolean secure) {
        this.tokenCookieBuilder.secure(secure);
        this.idTokenCookieBuilder.secure(secure);
        return me;
    }

    /**
     * When using cookie, if set to true, the HttpOnly attribute will be configured.
     * Defaults to {@value OidcCookieHandler.Builder#DEFAULT_HTTP_ONLY}.
     *
     * @param httpOnly whether the cookie should be HttpOnly (true) or not (false)
     * @return updated builder instance
     */
    @ConfiguredOption("true")
    public B cookieHttpOnly(Boolean httpOnly) {
        this.tokenCookieBuilder.httpOnly(httpOnly);
        this.idTokenCookieBuilder.httpOnly(httpOnly);
        return me;
    }

    /**
     * When using cookie, used to set MaxAge attribute of the cookie, defining how long
     * the cookie is valid.
     * Not used by default.
     *
     * @param age age in seconds
     * @return updated builder instance
     */
    @ConfiguredOption
    public B cookieMaxAgeSeconds(long age) {
        this.tokenCookieBuilder.maxAge(age);
        this.idTokenCookieBuilder.maxAge(age);
        return me;
    }

    /**
     * Path the cookie is valid for.
     * Defaults to "/".
     *
     * @param path the path to use as value of cookie "Path" attribute
     * @return updated builder instance
     */
    @ConfiguredOption(value = OidcCookieHandler.Builder.DEFAULT_PATH)
    public B cookiePath(String path) {
        this.tokenCookieBuilder.path(path);
        this.idTokenCookieBuilder.path(path);
        return me;
    }

    /**
     * Domain the cookie is valid for.
     * Not used by default.
     *
     * @param domain domain to use as value of cookie "Domain" attribute
     * @return updated builder instance
     */
    @ConfiguredOption
    public B cookieDomain(String domain) {
        this.tokenCookieBuilder.domain(domain);
        this.idTokenCookieBuilder.domain(domain);
        return me;
    }

    /**
     * Name of the cookie to use.
     * Defaults to {@value TenantConfig#DEFAULT_COOKIE_NAME}.
     *
     * @param cookieName name of a cookie
     * @return updated builder instance
     */
    @ConfiguredOption(value = DEFAULT_COOKIE_NAME)
    public B cookieName(String cookieName) {
        this.tokenCookieBuilder.cookieName(cookieName);
        return me;
    }

    /**
     * Name of the cookie to use for id token.
     * Defaults to {@value TenantConfig#DEFAULT_COOKIE_NAME}_2.
     *
     * This cookie is only used when logout is enabled, as otherwise it is not needed.
     * Content of this cookie is encrypted.
     *
     * @param cookieName name of a cookie
     * @return updated builder instance
     */
    public B cookieNameIdToken(String cookieName) {
        this.idTokenCookieBuilder.cookieName(cookieName);
        return me;
    }

    /**
     * Whether to use cookie to store JWT between requests.
     * Defaults to {@value TenantConfig#DEFAULT_COOKIE_USE}.
     *
     * @param useCookie whether to use cookie to store JWT (true) or not (false))
     * @return updated builder instance
     */
    @ConfiguredOption(key = "cookie-use", value = "true")
    public B useCookie(Boolean useCookie) {
        this.useCookie = useCookie;
        return me;
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
        return me;
    }

    private void clientTimeoutMillis(long millis) {
        this.clientTimeout(Duration.ofMillis(millis));
    }

    OidcCookieHandler.Builder tokenCookieBuilder() {
        return tokenCookieBuilder;
    }

    OidcCookieHandler.Builder idTokenCookieBuilder() {
        return idTokenCookieBuilder;
    }

    OidcConfig.ClientAuthentication tokenEndpointAuthentication() {
        return tokenEndpointAuthentication;
    }

    OidcMetadata.Builder oidcMetadata() {
        return oidcMetadata;
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

    String paramName() {
        return paramName;
    }

    boolean useHeader() {
        return useHeader;
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

    boolean useParam() {
        return useParam;
    }

    URI introspectUri() {
        return introspectUri;
    }

    TokenHandler headerHandler() {
        return headerHandler;
    }

    boolean useCookie() {
        return useCookie;
    }

    boolean cookieSameSiteDefault() {
        return cookieSameSiteDefault;
    }

    String scopeAudience() {
        return scopeAudience;
    }
}
