/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.oidc;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
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
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.oidc.common.OidcConfig;
import io.helidon.security.providers.TokenCredential;
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

    private OidcProvider(OidcConfig oidcConfig) {
        this.oidcConfig = oidcConfig;
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
                formValues.putSingle("token", signedJwt.getTokenContent());
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
    public static OidcProvider fromConfig(Config config) {
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
        Optional<String> token = Optional.empty();
        if (oidcConfig.useHeader()) {
            token = OptionalHelper.from(token)
                    .or(() -> oidcConfig.headerHandler().extractToken(providerRequest.getEnv().getHeaders()))
                    .asOptional();
        }

        if (oidcConfig.useParam()) {
            token = OptionalHelper.from(token)
                    .or(() -> paramHeaderHandler.extractToken(providerRequest.getEnv().getHeaders()))
                    .asOptional();
        }

        if (oidcConfig.useCookie()) {
            token = OptionalHelper.from(token)
                    .or(() -> findCookie(providerRequest.getEnv().getHeaders()))
                    .asOptional();
        }

        if (token.isPresent()) {
            return validateToken(providerRequest, token.get());
        } else {
            return missingToken(providerRequest);
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
        List<ScopeValidator.Scopes> expectedScopes = request.getEndpointConfig()
                .combineAnnotations(ScopeValidator.Scopes.class, EndpointConfig.AnnotationScope.values());

        Set<String> result = new HashSet<>();

        expectedScopes.stream()
                .map(ScopeValidator.Scopes::value)
                .map(Arrays::asList)
                .map(List::stream)
                .forEach(stream -> stream.map(ScopeValidator.Scope::value)
                        .forEach(result::add));

        return result;
    }

    private AuthenticationResponse missingToken(ProviderRequest providerRequest) {
        if (oidcConfig.shouldRedirect()) {
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
            queryString.append("state=").append(origUri(providerRequest));

            // must redirect
            return AuthenticationResponse
                    .builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                    .statusCode(Http.Status.TEMPORARY_REDIRECT_307.code())
                    .description("Missing token, redirecting to identity server")
                    .responseHeader("Location", authorizationEndpoint + queryString)
                    .build();
        } else {
            return AuthenticationResponse.failed("Missing token");
        }
    }

    private String origUri(ProviderRequest providerRequest) {
        List<String> origUri = providerRequest.getEnv().getHeaders()
                .getOrDefault(Security.HEADER_ORIG_URI, CollectionsHelper.listOf());

        if (origUri.isEmpty()) {
            origUri = CollectionsHelper.listOf(providerRequest.getEnv().getTargetUri().getPath());
        }

        try {
            return URLEncoder.encode(origUri.get(0), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new SecurityException("UTF-8 must be supported for security to work", e);
        }
    }

    private AuthenticationResponse validateToken(ProviderRequest providerRequest, String token) {
        SignedJwt signed = SignedJwt.parseToken(token);

        Jwt jwt = signed.getJwt();
        Errors.Collector collector = Errors.collector();
        jwtValidator.accept(signed, collector);

        Errors errors = collector.collect();
        Errors validationErrors = jwt.validate(oidcConfig.issuer(), oidcConfig.audience());

        if (errors.isValid() && validationErrors.isValid()) {

            errors.log(LOGGER);
            Subject subject = buildSubject(jwt, signed);

            Set<String> scopes = subject.getGrantsByType("scope")
                    .stream()
                    .map(Grant::getName)
                    .collect(Collectors.toSet());

            // make sure we have the correct scopes
            Set<String> expectedScopes = expectedScopes(providerRequest);

            for (String expectedScope : expectedScopes) {
                if (!scopes.contains(expectedScope)) {
                    return missingToken(providerRequest);
                }
            }

            return AuthenticationResponse.success(subject);
        } else {
            errors.log(LOGGER);
            validationErrors.log(LOGGER);
            return missingToken(providerRequest);
        }
    }

    private Subject buildSubject(Jwt jwt, SignedJwt signedJwt) {
        Principal principal = buildPrincipal(jwt);

        TokenCredential.Builder builder = TokenCredential.builder();
        jwt.getIssueTime().ifPresent(builder::issueTime);
        jwt.getExpirationTime().ifPresent(builder::expTime);
        jwt.getIssuer().ifPresent(builder::issuer);
        builder.token(signedJwt.getTokenContent());
        builder.addToken(Jwt.class, jwt);
        builder.addToken(SignedJwt.class, signedJwt);

        Optional<List<String>> scopes = jwt.getScopes();

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
        String subject = jwt.getSubject()
                .orElseThrow(() -> new JwtException("JWT does not contain subject claim, cannot create principal."));

        String name = jwt.getPreferredUsername()
                .orElse(subject);

        Principal.Builder builder = Principal.builder();

        builder.name(name)
                .id(subject);

        jwt.getPayloadClaims()
                .forEach((key, jsonValue) -> builder.addAttribute(key, JwtUtil.toObject(jsonValue)));

        jwt.getEmail().ifPresent(value -> builder.addAttribute("email", value));
        jwt.getEmailVerified().ifPresent(value -> builder.addAttribute("email_verified", value));
        jwt.getLocale().ifPresent(value -> builder.addAttribute("locale", value));
        jwt.getFamilyName().ifPresent(value -> builder.addAttribute("family_name", value));
        jwt.getGivenName().ifPresent(value -> builder.addAttribute("given_name", value));
        jwt.getFullName().ifPresent(value -> builder.addAttribute("full_name", value));

        return builder.build();
    }
}
