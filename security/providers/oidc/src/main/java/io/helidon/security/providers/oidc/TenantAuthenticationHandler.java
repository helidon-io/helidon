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

package io.helidon.security.providers.oidc;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.Errors;
import io.helidon.common.LazyValue;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.SetCookie;
import io.helidon.http.Status;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityException;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;
import io.helidon.security.jwt.JwtValidator;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.Tenant;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import jakarta.json.JsonObject;

import static io.helidon.security.providers.oidc.OidcFeature.JSON_BUILDER_FACTORY;
import static io.helidon.security.providers.oidc.OidcFeature.JSON_READER_FACTORY;
import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;

/**
 * Authentication handler.
 */
class TenantAuthenticationHandler {
    private static final System.Logger LOGGER = System.getLogger(TenantAuthenticationHandler.class.getName());
    private static final TokenHandler PARAM_HEADER_HANDLER = TokenHandler.forHeader(OidcConfig.PARAM_HEADER_NAME);
    private static final TokenHandler PARAM_ID_HEADER_HANDLER = TokenHandler.forHeader(OidcConfig.PARAM_ID_HEADER_NAME);
    private static final LazyValue<SecureRandom> RANDOM = LazyValue.create(SecureRandom::new);
    private static final JwtValidator TIME_VALIDATORS = JwtValidator.builder()
            .addDefaultTimeValidators()
            .build();

    private final boolean optional;
    private final OidcConfig oidcConfig;
    private final TenantConfig tenantConfig;
    private final Tenant tenant;
    private final boolean useJwtGroups;
    private final BiFunction<SignedJwt, Errors.Collector, Errors.Collector> jwtValidator;
    private final BiConsumer<StringBuilder, String> scopeAppender;
    private final Pattern attemptPattern;

    TenantAuthenticationHandler(OidcConfig oidcConfig, Tenant tenant, boolean useJwtGroups, boolean optional) {
        this.oidcConfig = oidcConfig;
        this.tenant = tenant;
        this.tenantConfig = tenant.tenantConfig();
        this.useJwtGroups = useJwtGroups;
        this.optional = optional;

        attemptPattern = Pattern.compile(".*?" + oidcConfig.redirectAttemptParam() + "=(\\d+).*");
        if (tenantConfig.validateJwtWithJwk()) {
            this.jwtValidator = (signedJwt, collector) -> {
                JwkKeys jwk = tenant.signJwk();
                Errors errors = signedJwt.verifySignature(jwk);
                errors.forEach(errorMessage -> {
                    switch (errorMessage.getSeverity()) {
                    case FATAL:
                        collector.fatal(errorMessage.getSource(), errorMessage.getMessage());
                        break;
                    case WARN:
                        collector.warn(errorMessage.getSource(), errorMessage.getMessage());
                        break;
                    case HINT:
                    default:
                        collector.hint(errorMessage.getSource(), errorMessage.getMessage());
                        break;
                    }
                });
                return collector;
            };
        } else {
            this.jwtValidator = (signedJwt, collector) -> {
                Parameters.Builder form = Parameters.builder("oidc-form-params")
                        .add("token", signedJwt.tokenContent());

                HttpClientRequest post = tenant.appWebClient()
                        .post()
                        .uri(tenant.introspectUri())
                        .header(HeaderValues.ACCEPT_JSON)
                        .headers(it -> it.add(HeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate"));

                OidcUtil.updateRequest(OidcConfig.RequestType.INTROSPECT_JWT, tenantConfig, form, post);

                try (HttpClientResponse response = post.submit(form.build())) {
                    if (response.status().family() == Status.Family.SUCCESSFUL) {
                        try {
                            JsonObject jsonObject = response.as(JsonObject.class);
                            if (!jsonObject.getBoolean("active")) {
                                collector.fatal(jsonObject, "Token is not active");
                            }
                        } catch (Exception e) {
                            collector.fatal(e, "Failed to validate token, request failed: "
                                    + "Failed to read JSON from response");
                        }
                    } else {
                        String message;
                        try {
                            message = response.as(String.class);
                            collector.fatal(response.status(),
                                            "Failed to validate token, response " + "status: " + response.status() + ", "
                                                    + "entity: " + message);
                        } catch (Exception e) {
                            collector.fatal(e, "Failed to validate token, request failed: Failed to process error entity");
                        }
                    }
                } catch (Exception e) {
                    collector.fatal(e, "Failed to validate token, request failed: Failed to invoke request");
                }
                return collector;
            };
        }
        // clean the scope audience - must end with / if exists
        String configuredScopeAudience = tenantConfig.scopeAudience();
        if (configuredScopeAudience == null || configuredScopeAudience.isEmpty()) {
            this.scopeAppender = StringBuilder::append;
        } else {
            if (configuredScopeAudience.endsWith("/")) {
                this.scopeAppender = (stringBuilder, scope) -> stringBuilder.append(configuredScopeAudience).append(scope);
            } else {
                this.scopeAppender = (stringBuilder, scope) -> stringBuilder.append(configuredScopeAudience)
                        .append("/")
                        .append(scope);
            }
        }
    }

    AuthenticationResponse authenticate(String tenantId, ProviderRequest providerRequest) {
        /*
        1. Get id token from request - if available, validate it and process access token
        2. If not - skip to access token validation directly
         */
        Optional<String> idToken = Optional.empty();
        try {
            if (oidcConfig.useParam()) {
                idToken = idToken.or(() -> PARAM_ID_HEADER_HANDLER.extractToken(providerRequest.env().headers()));
                if (idToken.isEmpty()) {
                    idToken = idToken.or(() -> providerRequest.env()
                            .queryParams()
                            .first(oidcConfig.idTokenParamName()).asOptional());
                }
            }
            if (oidcConfig.useCookie() && idToken.isEmpty()) {
                // only do this for cookies
                Optional<String> cookie = oidcConfig.idTokenCookieHandler()
                        .findCookie(providerRequest.env().headers());
                if (cookie.isPresent()) {
                    try {
                        String idTokenValue = cookie.get();
                        return validateIdToken(tenantId, providerRequest, idTokenValue);
                    } catch (Exception e) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            LOGGER.log(System.Logger.Level.DEBUG, "Invalid id token in cookie", e);
                        }
                        return errorResponse(providerRequest,
                                             Status.UNAUTHORIZED_401,
                                             null,
                                             "Invalid id token",
                                             tenantId);
                    }
                }
            }
        } catch (SecurityException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Failed to extract token from one of the configured locations", e);
            return failOrAbstain("Failed to extract one of the configured tokens" + e);
        }
        if (idToken.isPresent()) {
            return validateIdToken(tenantId, providerRequest, idToken.get());
        } else {
            return processAccessToken(tenantId, providerRequest, null);
        }

    }

    private AuthenticationResponse processAccessToken(String tenantId, ProviderRequest providerRequest, Jwt idToken) {
        /*
        Access token is mandatory!
        1. Get access token from request - if available, validate it and continue
        2. If not - Redirect to login page
         */
        List<String> missingLocations = new LinkedList<>();

        Optional<String> token = Optional.empty();
        try {
            if (oidcConfig.useHeader()) {
                token = token.or(() -> oidcConfig.headerHandler().extractToken(providerRequest.env().headers()));

                if (token.isEmpty()) {
                    missingLocations.add("header");
                }
            }

            if (oidcConfig.useParam()) {
                token = token.or(() -> PARAM_HEADER_HANDLER.extractToken(providerRequest.env().headers()));

                if (token.isEmpty()) {
                    token = token.or(() -> providerRequest.env().queryParams().first(oidcConfig.paramName()).asOptional());
                }

                if (token.isEmpty()) {
                    missingLocations.add("query-param");
                }
            }

            if (oidcConfig.useCookie()) {
                if (token.isEmpty()) {
                    // only do this for cookies
                    Optional<String> cookie = oidcConfig.tokenCookieHandler()
                            .findCookie(providerRequest.env().headers());
                    if (cookie.isEmpty()) {
                        missingLocations.add("cookie");
                    } else {
                        try {
                            String tokenValue = cookie.get();
                            String decodedJson = new String(Base64.getDecoder().decode(tokenValue), StandardCharsets.UTF_8);
                            JsonObject jsonObject = JSON_READER_FACTORY.createReader(new StringReader(decodedJson)).readObject();
                            if (oidcConfig.accessTokenIpCheck()) {
                                Object userIp = providerRequest.env().abacAttribute("userIp").orElseThrow();
                                if (!jsonObject.getString("remotePeer").equals(userIp)) {
                                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                        LOGGER.log(System.Logger.Level.DEBUG,
                                                   "Current peer IP does not match the one this access token was issued for");
                                    }
                                    return errorResponse(providerRequest,
                                                         Status.UNAUTHORIZED_401,
                                                         "peer_host_mismatch",
                                                         "Peer host access token mismatch",
                                                         tenantId);
                                }
                            }
                            return validateAccessToken(tenantId, providerRequest, jsonObject.getString("accessToken"), idToken);
                        } catch (Exception e) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                LOGGER.log(System.Logger.Level.DEBUG, "Invalid access token in cookie", e);
                            }
                            return errorResponse(providerRequest,
                                                 Status.UNAUTHORIZED_401,
                                                 null,
                                                 "Invalid access token",
                                                 tenantId);
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Failed to extract access token from one of the configured locations", e);
            return failOrAbstain("Failed to extract one of the configured tokens" + e);
        }

        if (token.isPresent()) {
            return validateAccessToken(tenantId, providerRequest, token.get(), idToken);
        } else {
            LOGGER.log(System.Logger.Level.DEBUG, () -> "Missing access token, could not find in either of: " + missingLocations);
            return errorResponse(providerRequest,
                                 Status.UNAUTHORIZED_401,
                                 null,
                                 "Missing access token, could not find in either of: " + missingLocations,
                                 tenantId);
        }
    }

    private Set<String> expectedScopes(ProviderRequest request) {

        Set<String> result = new HashSet<>();

        for (SecurityLevel securityLevel : request.endpointConfig().securityLevels()) {
            List<ScopeValidator.Scopes> expectedScopes = securityLevel.combineAnnotations(ScopeValidator.Scopes.class,
                                                                                          EndpointConfig.AnnotationScope
                                                                                                  .values());
            expectedScopes.stream()
                    .map(ScopeValidator.Scopes::value)
                    .map(Arrays::asList)
                    .map(List::stream)
                    .forEach(stream -> stream.map(ScopeValidator.Scope::value)
                            .forEach(result::add));

            List<ScopeValidator.Scope> expectedScopeAnnotations = securityLevel.combineAnnotations(ScopeValidator.Scope.class,
                                                                                                   EndpointConfig.AnnotationScope
                                                                                                           .values());

            expectedScopeAnnotations.stream()
                    .map(ScopeValidator.Scope::value)
                    .forEach(result::add);
        }

        return result;
    }

    private AuthenticationResponse errorResponse(ProviderRequest providerRequest,
                                                 Status status,
                                                 String code,
                                                 String description,
                                                 String tenantId) {
        if (oidcConfig.shouldRedirect()) {
            // make sure we do not exceed redirect limit
            String origUri = origUri(providerRequest);
            int redirectAttempt = redirectAttempt(origUri);
            if (redirectAttempt >= oidcConfig.maxRedirects()) {
                return errorResponseNoRedirect(code, description, status);
            }
            String state = generateRandomString();

            Set<String> expectedScopes = expectedScopes(providerRequest);

            StringBuilder scopes = new StringBuilder(tenantConfig.baseScopes());

            for (String expectedScope : expectedScopes) {
                if (!scopes.isEmpty()) {
                    // space after base scopes
                    scopes.append(' ');
                }
                String scope = expectedScope;
                if (scope.startsWith("/")) {
                    scope = scope.substring(1);
                }
                scopeAppender.accept(scopes, scope);
            }

            String scopeString;
            scopeString = URLEncoder.encode(scopes.toString(), StandardCharsets.UTF_8);

            String authorizationEndpoint = tenant.authorizationEndpointUri();
            String nonce = UUID.randomUUID().toString();
            String redirectUri;
            if (DEFAULT_TENANT_ID.equals(tenantId)) {
                redirectUri = encode(redirectUri(providerRequest.env()));
            } else {
                redirectUri = encode(redirectUri(providerRequest.env()) + "?"
                                             + encode(oidcConfig.tenantParamName()) + "=" + encode(tenantId));
            }

            String queryString = "?" + "client_id=" + tenantConfig.clientId() + "&"
                    + "response_type=code&"
                    + "redirect_uri=" + redirectUri + "&"
                    + "scope=" + scopeString + "&"
                    + "nonce=" + nonce + "&"
                    + "state=" + state;

            JsonObject stateJson = JSON_BUILDER_FACTORY.createObjectBuilder()
                    .add("originalUri", origUri)
                    .add("state", state)
                    .add("nonce", nonce)
                    .build();

            String stateBase64 = Base64.getEncoder().encodeToString(stateJson.toString().getBytes(StandardCharsets.UTF_8));
            SetCookie cookie = oidcConfig.stateCookieHandler().createCookie(stateBase64).build();

            // must redirect
            return AuthenticationResponse
                    .builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                    .statusCode(Status.TEMPORARY_REDIRECT_307.code())
                    .responseHeader(HeaderNames.SET_COOKIE.defaultCase(), cookie.toString())
                    .description("Redirecting to identity server: " + description)
                    .responseHeader("Location", authorizationEndpoint + queryString)
                    .build();
        } else {
            return errorResponseNoRedirect(code, description, status);
        }
    }

    private String redirectUri(SecurityEnvironment env) {
        for (Map.Entry<String, List<String>> entry : env.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("host") && !entry.getValue().isEmpty()) {
                String firstHost = entry.getValue().getFirst();
                String schema = oidcConfig.forceHttpsRedirects() ? "https" : env.transport();
                return oidcConfig.redirectUriWithHost(schema + "://" + firstHost);
            }
        }

        return oidcConfig.redirectUriWithHost();
    }

    private AuthenticationResponse failOrAbstain(String message) {
        if (optional) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .description(message)
                    .build();
        } else {
            return AuthenticationResponse.builder()
                    .status(AuthenticationResponse.SecurityStatus.FAILURE)
                    .description(message)
                    .build();
        }
    }

    private AuthenticationResponse errorResponseNoRedirect(String code, String description, Status status) {
        if (optional) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .description(description)
                    .build();
        }
        if (null == code) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .statusCode(Status.UNAUTHORIZED_401.code())
                    .responseHeader(HeaderNames.WWW_AUTHENTICATE.defaultCase(),
                                    "Bearer realm=\"" + tenantConfig.realm() + "\"")
                    .description(description)
                    .build();
        } else {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .statusCode(status.code())
                    .responseHeader(HeaderNames.WWW_AUTHENTICATE.defaultCase(), errorHeader(code, description))
                    .description(description)
                    .build();
        }
    }

    private int redirectAttempt(String state) {
        if (state.contains("?")) {
            // there are parameters
            Matcher matcher = attemptPattern.matcher(state);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
        }

        return 1;
    }

    private String errorHeader(String code, String description) {
        return "Bearer realm=\"" + tenantConfig.realm() + "\", error=\"" + code + "\", error_description=\"" + description + "\"";
    }

    String origUri(ProviderRequest providerRequest) {
        List<String> origUri = providerRequest.env().headers()
                .getOrDefault(Security.HEADER_ORIG_URI, List.of());

        if (origUri.isEmpty()) {
            URI targetUri = providerRequest.env().targetUri();
            String query = targetUri.getQuery();
            String path = targetUri.getPath();
            if (query == null || query.isEmpty()) {
                return path;
            } else {
                return path + "?" + query;
            }
        }

        return origUri.getFirst();
    }

    private String encode(String state) {
        return URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    private AuthenticationResponse validateIdToken(String tenantId, ProviderRequest providerRequest, String idToken) {
        SignedJwt signedJwt;
        try {
            signedJwt = SignedJwt.parseToken(idToken);
        } catch (Exception e) {
            //invalid token
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Could not parse inbound id token", e);
            }
            return AuthenticationResponse.failed("Invalid id token", e);
        }

        try {
            Errors errors;
            if (oidcConfig.idTokenSignatureValidation()) {
                errors = jwtValidator.apply(signedJwt, Errors.collector()).collect();
            } else {
                errors = Errors.collector().collect();
            }
            Jwt jwt = signedJwt.getJwt();

            JwtValidator.Builder jwtValidatorBuilder = JwtValidator.builder()
                    .addDefaultTimeValidators()
                    .addCriticalValidator()
                    .addUserPrincipalValidator()
                    .addAudienceValidator(tenantConfig.clientId());

            if (tenant.issuer() != null) {
                jwtValidatorBuilder.addIssuerValidator(tenant.issuer());
            }

            JwtValidator jwtValidation = jwtValidatorBuilder.build();
            Errors validationErrors = jwtValidation.validate(jwt);

            if (errors.isValid() && validationErrors.isValid()) {
                return processAccessToken(tenantId, providerRequest, jwt);
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    errors.log(LOGGER);
                    validationErrors.log(LOGGER);
                }
                return errorResponse(providerRequest,
                                     Status.UNAUTHORIZED_401,
                                     "invalid_id_token",
                                     "Id token not valid",
                                     tenantId);
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to validate request", e);
            }
            return AuthenticationResponse.failed("Failed to validate JWT", e);
        }
    }

    private AuthenticationResponse validateAccessToken(String tenantId,
                                                       ProviderRequest providerRequest,
                                                       String token,
                                                       Jwt idToken) {
        SignedJwt signedJwt;
        try {
            signedJwt = SignedJwt.parseToken(token);
        } catch (Exception e) {
            //invalid token
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Could not parse inbound token", e);
            }
            return AuthenticationResponse.failed("Invalid token", e);
        }

        try {
            Errors.Collector collector;
            if (oidcConfig.tokenSignatureValidation()) {
                collector = jwtValidator.apply(signedJwt, Errors.collector());
            } else {
                collector = Errors.collector();
            }
            Errors timeErrors = TIME_VALIDATORS.validate(signedJwt.getJwt());
            if (timeErrors.isValid()) {
                return processValidationResult(providerRequest, signedJwt, idToken, tenantId, collector);
            } else {
                //Access token expired, we should attempt to refresh it
                Optional<String> refreshToken = oidcConfig.refreshTokenCookieHandler()
                        .findCookie(providerRequest.env().headers());
                //If we have no refresh token to use. Continue with evaluation and reuse failure mechanism.
                return refreshToken.map(refreshTokenValue -> refreshAccessToken(providerRequest,
                                                                                refreshTokenValue,
                                                                                idToken,
                                                                                tenantId))
                        .orElseGet(() -> processValidationResult(providerRequest, signedJwt, idToken, tenantId, collector));
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to validate request", e);
            }
            return AuthenticationResponse.failed("Failed to validate JWT", e);
        }
    }

    private AuthenticationResponse refreshAccessToken(ProviderRequest providerRequest,
                                                      String refreshTokenString,
                                                      Jwt idToken,
                                                      String tenantId) {
        try {
            WebClient webClient = tenant.appWebClient();
            Parameters.Builder form = Parameters.builder("oidc-form-params")
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshTokenString)
                    .add("client_id", tenantConfig.clientId());

            HttpClientRequest post = webClient.post()
                    .uri(tenant.tokenEndpointUri())
                    .header(HeaderValues.ACCEPT_JSON);

            try (HttpClientResponse response = post.submit(form.build())) {
                if (response.status().family() == Status.Family.SUCCESSFUL) {
                    try {
                        JsonObject jsonObject = response.as(JsonObject.class);
                        String accessToken = jsonObject.getString("access_token");
                        String refreshToken = jsonObject.getString("refresh_token", null);

                        SignedJwt signedAccessToken;
                        try {
                            signedAccessToken = SignedJwt.parseToken(accessToken);
                        } catch (Exception e) {
                            //invalid token
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                LOGGER.log(System.Logger.Level.DEBUG, "Could not parse refreshed access token", e);
                            }
                            return AuthenticationResponse.failed("Invalid access token", e);
                        }
                        Errors.Collector newAccessTokenCollector = jwtValidator.apply(signedAccessToken, Errors.collector());
                        Object remotePeer = providerRequest.env().abacAttribute("userIp").orElseThrow();

                        JsonObject accessTokenCookie = JSON_BUILDER_FACTORY.createObjectBuilder()
                                .add("accessToken", signedAccessToken.tokenContent())
                                .add("remotePeer", remotePeer.toString())
                                .build();
                        String base64 = Base64.getEncoder()
                                .encodeToString(accessTokenCookie.toString().getBytes(StandardCharsets.UTF_8));

                        List<String> setCookieParts = new ArrayList<>();
                        setCookieParts.add(oidcConfig.tokenCookieHandler()
                                                   .createCookie(base64)
                                                   .build()
                                                   .toString());
                        if (refreshToken != null) {
                            setCookieParts.add(oidcConfig.refreshTokenCookieHandler()
                                                       .createCookie(refreshToken)
                                                       .build()
                                                       .toString());
                        }
                        return processValidationResult(providerRequest,
                                                       signedAccessToken,
                                                       idToken,
                                                       tenantId,
                                                       newAccessTokenCollector,
                                                       setCookieParts);
                    } catch (Exception e) {
                        return errorResponse(providerRequest,
                                             Status.UNAUTHORIZED_401,
                                             "refresh_access_token_failure",
                                             "Failed to refresh access token",
                                             tenantId);
                    }
                } else {
                    String message;
                    try {
                        message = response.as(String.class);
                        return errorResponse(providerRequest,
                                             Status.UNAUTHORIZED_401,
                                             "access_token_refresh_failed",
                                             "Failed to refresh access token. Response status was: "
                                                     + response.status() + " "
                                                     + "with message: " + message,
                                             tenantId);
                    } catch (Exception e) {
                        return AuthenticationResponse.failed(
                                "Failed to refresh access token, request failed: Failed to process error entity",
                                e);
                    }
                }
            } catch (Exception e) {
                return AuthenticationResponse.failed(
                        "Failed to refresh access token, request failed: Failed to invoke request",
                        e);
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to validate refresh token", e);
            }
            return AuthenticationResponse.failed("Failed to validate refresh token", e);
        }
    }

    private AuthenticationResponse processValidationResult(ProviderRequest providerRequest,
                                                           SignedJwt signedJwt,
                                                           Jwt idToken,
                                                           String tenantId,
                                                           Errors.Collector collector) {
        return processValidationResult(providerRequest, signedJwt, idToken, tenantId, collector, List.of());
    }

    private AuthenticationResponse processValidationResult(ProviderRequest providerRequest,
                                                           SignedJwt signedJwt,
                                                           Jwt idToken,
                                                           String tenantId,
                                                           Errors.Collector collector,
                                                           List<String> cookies) {
        Jwt jwt = signedJwt.getJwt();
        Errors errors = collector.collect();
        JwtValidator.Builder jwtValidatorBuilder = JwtValidator.builder()
                .addDefaultTimeValidators()
                .addCriticalValidator()
                .addUserPrincipalValidator();
        if (tenant.issuer() != null) {
            jwtValidatorBuilder.addIssuerValidator(tenant.issuer());
        }
        if (tenantConfig.checkAudience()) {
            jwtValidatorBuilder.addAudienceValidator(tenantConfig.audience());
        }
        JwtValidator jwtValidation = jwtValidatorBuilder.build();
        Errors validationErrors = jwtValidation.validate(jwt);

        if (errors.isValid() && validationErrors.isValid()) {

            errors.log(LOGGER);
            Subject subject = buildSubject(jwt, signedJwt, idToken);

            Set<String> scopes = subject.grantsByType("scope")
                    .stream()
                    .map(Grant::getName)
                    .collect(Collectors.toSet());

            // make sure we have the correct scopes
            Set<String> expectedScopes = expectedScopes(providerRequest);
            List<String> missingScopes = new LinkedList<>();
            for (String expectedScope : expectedScopes) {
                if (!scopes.contains(expectedScope)) {
                    missingScopes.add(expectedScope);
                }
            }

            if (missingScopes.isEmpty()) {
                AuthenticationResponse.Builder response = AuthenticationResponse.builder()
                        .status(SecurityResponse.SecurityStatus.SUCCESS)
                        .user(subject);

                if (cookies.isEmpty()) {
                    return response.build();
                } else {
                    return response
                            .responseHeader(HeaderNames.SET_COOKIE.defaultCase(), cookies)
                            .build();
                }
            } else {
                return errorResponse(providerRequest,
                                     Status.FORBIDDEN_403,
                                     "insufficient_scope",
                                     "Scopes " + missingScopes + " are missing",
                                     tenantId);
            }
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                // only log errors when details requested
                errors.log(LOGGER);
                validationErrors.log(LOGGER);
            }
            return errorResponse(providerRequest,
                                 Status.UNAUTHORIZED_401,
                                 "invalid_token",
                                 "Token not valid",
                                 tenantId);
        }
    }

    private Subject buildSubject(Jwt jwt, SignedJwt signedJwt, Jwt idToken) {
        Principal principal = buildPrincipal(jwt, idToken);

        TokenCredential.Builder builder = TokenCredential.builder();
        jwt.issueTime().ifPresent(builder::issueTime);
        jwt.expirationTime().ifPresent(builder::expTime);
        jwt.issuer().ifPresent(builder::issuer);
        builder.token(signedJwt.tokenContent());
        builder.addToken(Jwt.class, jwt);
        builder.addToken(SignedJwt.class, signedJwt);

        Subject.Builder subjectBuilder = Subject.builder()
                .principal(principal)
                .addPublicCredential(TokenCredential.class, builder.build());

        if (useJwtGroups) {
            Optional<List<String>> userGroups = jwt.userGroups();
            userGroups.ifPresent(groups -> groups.forEach(group -> subjectBuilder.addGrant(Role.create(group))));
        }

        Optional<List<String>> scopes = jwt.scopes();
        scopes.ifPresent(scopeList -> scopeList.forEach(scope -> subjectBuilder.addGrant(Grant.builder()
                                                                                                 .name(scope)
                                                                                                 .type("scope")
                                                                                                 .build())));

        return subjectBuilder.build();

    }

    private Principal buildPrincipal(Jwt accessToken, Jwt idToken) {
        Jwt tokenToUse = idToken;
        if (idToken == null) {
            tokenToUse = accessToken;
        }

        String subject = tokenToUse.subject()
                .orElseThrow(() -> new JwtException("JWT does not contain subject claim, cannot create principal."));

        String name = tokenToUse.preferredUsername()
                .orElse(subject);

        Principal.Builder builder = Principal.builder();

        builder.name(name)
                .id(subject);

        tokenToUse.payloadClaims()
                .forEach((key, jsonValue) -> builder.addAttribute(key, JwtUtil.toObject(jsonValue)));

        tokenToUse.email().ifPresent(value -> builder.addAttribute("email", value));
        tokenToUse.emailVerified().ifPresent(value -> builder.addAttribute("email_verified", value));
        tokenToUse.locale().ifPresent(value -> builder.addAttribute("locale", value));
        tokenToUse.familyName().ifPresent(value -> builder.addAttribute("family_name", value));
        tokenToUse.givenName().ifPresent(value -> builder.addAttribute("given_name", value));
        tokenToUse.fullName().ifPresent(value -> builder.addAttribute("full_name", value));

        return builder.build();
    }

    //Obtained from https://www.baeldung.com/java-random-string
    private static String generateRandomString() {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;

        return RANDOM.get().ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
