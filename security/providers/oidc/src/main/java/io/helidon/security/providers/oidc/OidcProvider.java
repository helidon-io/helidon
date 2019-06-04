/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.Errors;
import io.helidon.common.OptionalHelper;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
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
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.security.util.TokenHandler;

/**
 * Open ID Connect authentication provider.
 *
 * IDCS specific notes:
 * <ul>
 * <li>If you want to use JWK to validate tokens, you must give access to the endpoint (by default only admin can access it)</li>
 * <li>If you want to use introspect endpoint to validate tokens, you must give rights to the application to do so (Client
 * Configuration/Allowed Operations)</li>
 * <li>If you want to retrieve groups when using IDCS, you must add "Client Credentials" in "Allowed Grant Types" in
 * application configuration, as well as "Grant the client access to Identity Cloud Service Admin APIs." configured to "User
 * Administrator"</li>
 * </ul>
 */
public final class OidcProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private static final Logger LOGGER = Logger.getLogger(OidcProvider.class.getName());

    private final OidcConfig oidcConfig;
    private final TokenHandler paramHeaderHandler;
    private final BiConsumer<SignedJwt, Errors.Collector> jwtValidator;
    private final Pattern attemptPattern;

    private OidcProvider(OidcConfig oidcConfig) {
        this.oidcConfig = oidcConfig;

        attemptPattern = Pattern.compile(".*?" + oidcConfig.redirectAttemptParam() + "=(\\d+).*");

        // must re-configure integration with webserver and jersey

        if (oidcConfig.useParam()) {
            paramHeaderHandler = TokenHandler.forHeader(OidcConfig.PARAM_HEADER_NAME);
        } else {
            paramHeaderHandler = null;
        }

        if (oidcConfig.validateJwtWithJwk()) {
            this.jwtValidator = (signedJwt, collector) -> {
                JwkKeys jwk = oidcConfig.signJwk();
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
            };
        } else {
            this.jwtValidator = (signedJwt, collector) -> {

                MultivaluedHashMap<String, String> formValues = new MultivaluedHashMap<>();
                formValues.putSingle("token", signedJwt.tokenContent());
                Response response = oidcConfig.introspectEndpoint().request()
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .cacheControl(CacheControl.valueOf("no-cache, no-store, must-revalidate"))
                        .post(Entity.form(formValues));

                if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                    JsonObject jsonResponse = response.readEntity(JsonObject.class);
                    if (jsonResponse.getBoolean("active")) {
                        // fine
                        return;
                    }
                    collector.fatal(jsonResponse, "Token is not active");
                } else {
                    collector.fatal(response,
                                    "Failed to validate token, response code: " + response.getStatus() + ", entity. " + response
                                            .readEntity(String.class));
                }
            };
        }
    }

    /**
     * Load this provider from configuration.
     *
     * @param config configuration of this provider
     * @return a new provider configured for OIDC
     */
    public static OidcProvider create(Config config) {
        return new OidcProvider(OidcConfig.create(config));
    }

    /**
     * Create a new provider based on OIDC configuration.
     *
     * @param config config of OIDC server and client
     * @return a new provider configured for OIDC
     */
    public static OidcProvider create(OidcConfig config) {
        return new OidcProvider(config);
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return CollectionsHelper.setOf(ScopeValidator.Scope.class, ScopeValidator.Scopes.class);
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        /*
        1. Get token from request - if available, validate it and continue
        2. If not - Redirect to login page
         */
        List<String> missingLocations = new LinkedList<>();

        Optional<String> token = Optional.empty();

        try {
            if (oidcConfig.useHeader()) {
                token = OptionalHelper.from(token)
                        .or(() -> oidcConfig.headerHandler().extractToken(providerRequest.env().headers()))
                        .asOptional();
                if (!token.isPresent()) {
                    missingLocations.add("header");
                }
            }

            if (oidcConfig.useParam()) {
                token = OptionalHelper.from(token)
                        .or(() -> paramHeaderHandler.extractToken(providerRequest.env().headers()))
                        .asOptional();

                if (!token.isPresent()) {
                    missingLocations.add("query-param");
                }
            }

            if (oidcConfig.useCookie()) {
                token = OptionalHelper.from(token)
                        .or(() -> findCookie(providerRequest.env().headers()))
                        .asOptional();
                if (!token.isPresent()) {
                    missingLocations.add("cookie");
                }
            }
        } catch (SecurityException e) {
            return AuthenticationResponse.failed("Failed to extract one of the confiugred tokens", e);
        }

        if (token.isPresent()) {
            return validateToken(providerRequest, token.get());
        } else {
            return errorResponse(providerRequest,
                                 Http.Status.UNAUTHORIZED_401,
                                 null,
                                 "Missing token, could not find in either of: " + missingLocations);
        }
    }

    private Optional<String> findCookie(Map<String, List<String>> headers) {
        List<String> cookies = headers.get("Cookie");
        if ((null == cookies) || cookies.isEmpty()) {
            return Optional.empty();
        }

        for (String cookie : cookies) {
            //a=b; c=d; e=f
            String[] cookieValues = cookie.split(";");
            for (String cookieValue : cookieValues) {
                String trimmed = cookieValue.trim();
                if (trimmed.startsWith(oidcConfig.cookieValuePrefix())) {
                    return Optional.of(trimmed.substring(oidcConfig.cookieValuePrefix().length()));
                }
            }
        }

        return Optional.empty();
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
                                                 String description) {
        if (oidcConfig.shouldRedirect()) {
            // make sure we do not exceed redirect limit
            String state = origUri(providerRequest);
            int redirectAttempt = redirectAttempt(state);
            if (redirectAttempt >= oidcConfig.maxRedirects()) {
                return errorResponseNoRedirect(code, description, status);
            }

            Set<String> expectedScopes = expectedScopes(providerRequest);

            StringBuilder scopes = new StringBuilder(oidcConfig.baseScopes());

            expectedScopes
                    .forEach(scope -> scopes.append(' ').append(oidcConfig.scopeAudience()).append(scope));

            String scopeString;
            try {
                scopeString = URLEncoder.encode(scopes.toString(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // UTF-8 should be supported. If not, just use openid to be able to connect
                scopeString = oidcConfig.baseScopes();
            }

            String authorizationEndpoint = oidcConfig.authorizationEndpointUri();

            String nonce = UUID.randomUUID().toString();
            StringBuilder queryString = new StringBuilder("?");
            queryString.append("client_id=").append(oidcConfig.clientId()).append("&");
            queryString.append("response_type=code&");
            queryString.append("redirect_uri=").append(oidcConfig.redirectUriWithHost()).append("&");
            queryString.append("scope=").append(scopeString).append("&");
            queryString.append("nonce=").append(nonce).append("&");
            queryString.append("state=").append(encodeState(state));

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

    private AuthenticationResponse errorResponseNoRedirect(String code, String description, Http.Status status) {
        if (null == code) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .statusCode(Http.Status.UNAUTHORIZED_401.code())
                    .responseHeader(Http.Header.WWW_AUTHENTICATE, "Bearer realm=\"" + oidcConfig.realm() + "\"")
                    .description(description)
                    .build();
        } else {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .statusCode(status.code())
                    .responseHeader(Http.Header.WWW_AUTHENTICATE, errorHeader(code, description))
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
        return "Bearer realm=\"" + oidcConfig.realm() + "\", error=\"" + code + "\", error_description=\"" + description + "\"";
    }

    private String origUri(ProviderRequest providerRequest) {
        List<String> origUri = providerRequest.env().headers()
                .getOrDefault(Security.HEADER_ORIG_URI, CollectionsHelper.listOf());

        if (origUri.isEmpty()) {
            origUri = CollectionsHelper.listOf(providerRequest.env().targetUri().getPath());
        }

        return origUri.get(0);
    }

    private String encodeState(String state) {
        try {
            return URLEncoder.encode(state, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new SecurityException("UTF-8 must be supported for security to work", e);
        }
    }

    private AuthenticationResponse validateToken(ProviderRequest providerRequest, String token) {
        SignedJwt signedJwt;
        try {
            signedJwt = SignedJwt.parseToken(token);
        } catch (Exception e) {
            //invalid token
            return AuthenticationResponse.failed("Invalid token", e);
        }

        Jwt jwt = signedJwt.getJwt();
        Errors.Collector collector = Errors.collector();
        jwtValidator.accept(signedJwt, collector);

        Errors errors = collector.collect();
        Errors validationErrors = jwt.validate(oidcConfig.issuer(), oidcConfig.audience());

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
                                     "Scopes " + missingScopes + " are missing");
            }
        } else {
            if (LOGGER.isLoggable(Level.FINEST)) {
                // only log errors when details requested
                errors.log(LOGGER);
                validationErrors.log(LOGGER);
            }
            return errorResponse(providerRequest, Http.Status.UNAUTHORIZED_401, "invalid_token", "Token not valid");
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

        Optional<List<String>> scopes = jwt.scopes();

        Subject.Builder subjectBuilder = Subject.builder()
                .principal(principal)
                .addPublicCredential(TokenCredential.class, builder.build());

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
