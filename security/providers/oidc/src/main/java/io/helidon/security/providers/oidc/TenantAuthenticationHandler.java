/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import io.helidon.common.parameters.Parameters;
import io.helidon.http.Http;
import io.helidon.http.Http.HeaderNames;
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
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.Tenant;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;

import jakarta.json.JsonObject;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;

/**
 * Authentication handler.
 */
class TenantAuthenticationHandler {
    private static final System.Logger LOGGER = System.getLogger(TenantAuthenticationHandler.class.getName());
    private static final TokenHandler PARAM_HEADER_HANDLER = TokenHandler.forHeader(OidcConfig.PARAM_HEADER_NAME);

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
                        .header(Http.Headers.ACCEPT_JSON)
                        .headers(it -> it.add(HeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate"));

                OidcUtil.updateRequest(OidcConfig.RequestType.INTROSPECT_JWT, tenantConfig, form);

                try (HttpClientResponse response = post.submit(form.build())) {
                    if (response.status().family() == Http.Status.Family.SUCCESSFUL) {
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
        1. Get token from request - if available, validate it and continue
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
                    token = token.or(() -> providerRequest.env().queryParams().first(oidcConfig.paramName()));
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
                            return validateToken(tenantId, providerRequest, tokenValue);
                        } catch (Exception e) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                LOGGER.log(System.Logger.Level.DEBUG, "Invalid token in cookie", e);
                            }
                            return errorResponse(providerRequest,
                                                 Http.Status.UNAUTHORIZED_401,
                                                 null,
                                                 "Invalid token",
                                                 tenantId);
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Failed to extract token from one of the configured locations", e);
            return failOrAbstain("Failed to extract one of the configured tokens" + e);
        }

        if (token.isPresent()) {
            return validateToken(tenantId, providerRequest, token.get());
        } else {
            LOGGER.log(System.Logger.Level.DEBUG, () -> "Missing token, could not find in either of: " + missingLocations);
            return errorResponse(providerRequest,
                                 Http.Status.UNAUTHORIZED_401,
                                 null,
                                 "Missing token, could not find in either of: "
                                         + missingLocations,
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
                                                 Http.Status status,
                                                 String code,
                                                 String description,
                                                 String tenantId) {
        if (oidcConfig.shouldRedirect()) {
            // make sure we do not exceed redirect limit
            String state = origUri(providerRequest);
            int redirectAttempt = redirectAttempt(state);
            if (redirectAttempt >= oidcConfig.maxRedirects()) {
                return errorResponseNoRedirect(code, description, status);
            }

            Set<String> expectedScopes = expectedScopes(providerRequest);

            StringBuilder scopes = new StringBuilder(tenantConfig.baseScopes());

            for (String expectedScope : expectedScopes) {
                if (scopes.length() > 0) {
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


            StringBuilder queryString = new StringBuilder("?");
            queryString.append("client_id=").append(tenantConfig.clientId()).append("&");
            queryString.append("response_type=code&");
            queryString.append("redirect_uri=").append(redirectUri).append("&");
            queryString.append("scope=").append(scopeString).append("&");
            queryString.append("nonce=").append(nonce).append("&");
            queryString.append("state=").append(encode(state));

            // must redirect
            return AuthenticationResponse
                    .builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                    .statusCode(Http.Status.TEMPORARY_REDIRECT_307.code())
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
                String firstHost = entry.getValue().get(0);
                return oidcConfig.redirectUriWithHost(oidcConfig.forceHttpsRedirects() ? "https" : env.transport()
                        + "://" + firstHost);
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

    private AuthenticationResponse errorResponseNoRedirect(String code, String description, Http.Status status) {
        if (optional) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .description(description)
                    .build();
        }
        if (null == code) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .statusCode(Http.Status.UNAUTHORIZED_401.code())
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

    private String origUri(ProviderRequest providerRequest) {
        List<String> origUri = providerRequest.env().headers()
                .getOrDefault(Security.HEADER_ORIG_URI, List.of());

        if (origUri.isEmpty()) {
            origUri = List.of(providerRequest.env().targetUri().getPath());
        }

        return origUri.get(0);
    }

    private String encode(String state) {
        return URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    private AuthenticationResponse validateToken(String tenantId, ProviderRequest providerRequest, String token) {
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
            Errors.Collector collector = jwtValidator.apply(signedJwt, Errors.collector());
            return processValidationResult(providerRequest, signedJwt, tenantId, collector);
        } catch (Exception e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to validate request", e);
            }
            return AuthenticationResponse.failed("Failed to validate JWT", e);
        }
    }

    private AuthenticationResponse processValidationResult(ProviderRequest providerRequest,
                                                           SignedJwt signedJwt,
                                                           String tenantId,
                                                           Errors.Collector collector) {
        Jwt jwt = signedJwt.getJwt();
        Errors errors = collector.collect();
        Errors validationErrors = jwt.validate(tenant.issuer(),
                                               tenantConfig.audience(),
                                               tenantConfig.checkAudience());

        if (errors.isValid() && validationErrors.isValid()) {

            errors.log(LOGGER);
            Subject subject = buildSubject(jwt, signedJwt);

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
                return AuthenticationResponse.success(subject);
            } else {
                return errorResponse(providerRequest,
                                     Http.Status.FORBIDDEN_403,
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
                                 Http.Status.UNAUTHORIZED_401,
                                 "invalid_token",
                                 "Token not valid",
                                 tenantId);
        }
    }

    private Subject buildSubject(Jwt jwt, SignedJwt signedJwt) {
        Principal principal = buildPrincipal(jwt);

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

    private Principal buildPrincipal(Jwt jwt) {
        String subject = jwt.subject()
                .orElseThrow(() -> new JwtException("JWT does not contain subject claim, cannot create principal."));

        String name = jwt.preferredUsername()
                .orElse(subject);

        Principal.Builder builder = Principal.builder();

        builder.name(name)
                .id(subject);

        jwt.payloadClaims()
                .forEach((key, jsonValue) -> builder.addAttribute(key, JwtUtil.toObject(jsonValue)));

        jwt.email().ifPresent(value -> builder.addAttribute("email", value));
        jwt.emailVerified().ifPresent(value -> builder.addAttribute("email_verified", value));
        jwt.locale().ifPresent(value -> builder.addAttribute("locale", value));
        jwt.familyName().ifPresent(value -> builder.addAttribute("family_name", value));
        jwt.givenName().ifPresent(value -> builder.addAttribute("given_name", value));
        jwt.fullName().ifPresent(value -> builder.addAttribute("full_name", value));

        return builder.build();
    }
}
