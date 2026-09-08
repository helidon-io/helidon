/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.jwt;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceConfig;
import io.helidon.common.configurable.ResourceException;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.Timeout;
import io.helidon.faulttolerance.TimeoutConfig;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonException;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;
import io.helidon.security.jwt.JwtValidator;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.util.TokenHandler;

/**
 * Provider that can process JWT tokens in request headers and assert identity (e.g. create a {@link Principal}
 * for a {@link io.helidon.security.SubjectType#USER} or {@link io.helidon.security.SubjectType#SERVICE}.
 * This provider can also propagate identity using JWT token, either by creating a new
 * JWT or by propagating the existing token "as is".
 * Verification and signatures of tokens is done through JWK standard - two separate
 * JWK files are expected (one for verification, one for signatures).
 */
public final class JwtProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private static final System.Logger LOGGER = System.getLogger(JwtProvider.class.getName());
    private static final String DEFAULT_JWT_GROUPS_PATH = "groups";
    private static final JwkKeys EMPTY_JWK_KEYS = JwkKeys.builder().build();

    private final boolean optional;
    private final boolean authenticate;
    private final boolean propagate;
    private final boolean allowImpersonation;
    private final boolean verifySignature;
    private final boolean useBearerChallenge;
    private final SubjectType subjectType;
    private final TokenHandler atnTokenHandler;
    private final TokenHandler defaultTokenHandler;
    private final JwkKeys verifyKeys;
    private final ResilientValue<JwkKeys> verifyKeysLoader;
    private final String expectedAudience;
    private final String expectedIssuer;
    private final JwkKeys signKeys;
    private final OutboundConfig outboundConfig;
    private final String issuer;
    private final Map<OutboundTarget, JwtOutboundTarget> targetToJwtConfig = new IdentityHashMap<>();
    private final Jwk defaultJwk;
    private final boolean useJwtGroups;
    private final String jwtGroupsPath;
    private final String jwtGroupsSeparator;

    private JwtProvider(Builder builder) {
        this.optional = builder.optional;
        this.authenticate = builder.authenticate;
        this.propagate = builder.propagate && builder.outboundConfig.targets().size() > 0;
        this.allowImpersonation = builder.allowImpersonation;
        this.useBearerChallenge = builder.useBearerChallenge;
        this.subjectType = builder.subjectType;
        this.atnTokenHandler = builder.atnTokenHandler;
        this.outboundConfig = builder.outboundConfig;
        this.verifyKeys = builder.verifyKeys;
        this.verifyKeysLoader = builder.verifyKeysLoader;
        this.signKeys = builder.signKeys;
        this.issuer = builder.issuer;
        this.expectedAudience = builder.expectedAudience;
        this.expectedIssuer = builder.expectedIssuer;
        this.verifySignature = builder.verifySignature;
        this.useJwtGroups = builder.useJwtGroups;
        this.jwtGroupsPath = builder.jwtGroupsPath;
        this.jwtGroupsSeparator = builder.jwtGroupsSeparator;

        if (null == atnTokenHandler) {
            defaultTokenHandler = TokenHandler.builder()
                    .tokenHeader("Authorization")
                    .tokenPrefix("bearer ")
                    .build();
        } else {
            defaultTokenHandler = atnTokenHandler;
        }

        if (builder.allowUnsigned) {
            defaultJwk = Jwk.NONE_JWK;
        } else {
            defaultJwk = null;
        }

        if (!verifySignature) {
            LOGGER.log(Level.INFO,
                       "JWT signature validation is disabled. JWT claims will still be validated.");
        }
    }

    /**
     * A builder for this provider.
     *
     * @return builder to create a new instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create provider instance from configuration.
     *
     * @param config configuration of this provider
     * @return provider instance
     */
    public static JwtProvider create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public AuthenticationResponse authenticate(ProviderRequest providerRequest) {
        if (!authenticate) {
            return AuthenticationResponse.abstain();
        }
        Optional<String> maybeToken;
        try {
            maybeToken = atnTokenHandler.extractToken(providerRequest.env().headers());
        } catch (Exception e) {
            return failOrAbstain("JWT header not available or in a wrong format" + e);
        }

        return maybeToken
                .map(this::authenticateToken)
                .orElseGet(() -> failOrAbstain("JWT header not available or in a wrong format"));
    }

    private AuthenticationResponse authenticateToken(String token) {
        SignedJwt signedJwt;
        Jwt jwt;
        try {
            signedJwt = SignedJwt.parseToken(token);
            jwt = signedJwt.getJwt();
        } catch (Exception e) {
            //invalid token
            return failOrAbstain("Invalid token" + e);
        }
        if (verifySignature) {
            JwkKeys keys;
            try {
                keys = verificationKeys(jwt);
            } catch (ResilientValue.UnavailableException e) {
                return unavailableOrAbstain("JWT verification keys are temporarily unavailable");
            }
            Jwk fallbackJwk = jwt.keyId().isEmpty() ? defaultJwk : null;
            Errors errors = signedJwt.verifySignature(keys, fallbackJwk);
            if (!errors.isValid()) {
                return failOrAbstain(errors.toString());
            }
        }

        Errors validate = validateJwt(jwt);
        if (!validate.isValid()) {
            return failOrAbstain(validate.toString());
        }
        try {
            return AuthenticationResponse.success(buildSubject(jwt, signedJwt));
        } catch (JwtException e) {
            return failOrAbstain(e.getMessage());
        }
    }

    private Errors validateJwt(Jwt jwt) {
        JwtValidator.Builder jwtValidatorBuilder = JwtValidator.builder()
                .addDefaultTimeValidators()
                .addCriticalValidator()
                .addUserPrincipalValidator();
        if (expectedAudience != null) {
            jwtValidatorBuilder.addAudienceValidator(expectedAudience);
        }
        if (expectedIssuer != null) {
            jwtValidatorBuilder.addIssuerValidator(expectedIssuer);
        }
        JwtValidator jwtValidator = jwtValidatorBuilder.build();
        return jwtValidator.validate(jwt);
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

    Subject buildSubject(Jwt jwt, SignedJwt signedJwt) {
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
            jwtGroups(jwt).forEach(group -> subjectBuilder.addGrant(Role.create(group)));
        }

        Optional<List<String>> scopes = jwt.scopes();
        scopes.ifPresent(scopeList -> {
            scopeList.forEach(scope -> subjectBuilder.addGrant(Grant.builder()
                                                                       .name(scope)
                                                                       .type("scope")
                                                                       .build()));
        });

        return subjectBuilder.build();

    }

    Principal buildPrincipal(Jwt jwt) {
        String subject = jwt.subject()
                .orElseThrow(() -> new JwtException("JWT does not contain subject claim, cannot create principal."));

        String name = jwt.preferredUsername()
                .orElse(subject);

        Principal.Builder builder = Principal.builder();

        builder.name(name)
                .id(subject);

        jwt.payloadClaimsJson()
                .forEach((key, jsonValue) -> builder.addAttribute(key, JwtUtil.toObject(jsonValue)));

        jwt.email().ifPresent(value -> builder.addAttribute("email", value));
        jwt.emailVerified().ifPresent(value -> builder.addAttribute("email_verified", value));
        jwt.locale().ifPresent(value -> builder.addAttribute("locale", value));
        jwt.familyName().ifPresent(value -> builder.addAttribute("family_name", value));
        jwt.givenName().ifPresent(value -> builder.addAttribute("given_name", value));
        jwt.fullName().ifPresent(value -> builder.addAttribute("full_name", value));

        return builder.build();
    }

    private List<String> jwtGroups(Jwt jwt) {
        if (DEFAULT_JWT_GROUPS_PATH.equals(jwtGroupsPath)) {
            return jwt.userGroups().orElse(List.of());
        }
        return jwtGroupsClaim(jwt)
                .map(this::toGroups)
                .orElse(List.of());
    }

    private Optional<JsonValue> jwtGroupsClaim(Jwt jwt) {
        String[] pathSegments = jwtGroupsPath.split("/");
        Optional<JsonValue> currentValue = jwt.payloadClaimValue(pathSegments[0]);
        for (int i = 1; i < pathSegments.length; i++) {
            String pathSegment = pathSegments[i];
            currentValue = currentValue
                    .filter(it -> it instanceof JsonObject)
                    .flatMap(it -> it.asObject().value(pathSegment));
        }
        return currentValue;
    }

    private List<String> toGroups(JsonValue claimValue) {
        if (claimValue instanceof JsonArray groups) {
            return groups.values()
                    .stream()
                    .map(this::toGroup)
                    .toList();
        }
        String group = toGroup(claimValue);
        if (jwtGroupsSeparator == null) {
            return List.of(group);
        }
        return Stream.of(group.split(Pattern.quote(jwtGroupsSeparator)))
                .filter(it -> !it.isBlank())
                .toList();
    }

    private String toGroup(JsonValue groupValue) {
        if (groupValue instanceof JsonString group) {
            return group.value();
        }
        throw new JwtException("Invalid value. Expecting a string or string array for key " + jwtGroupsPath);
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        // only propagate if we have an actual target configured
        return propagate && this.outboundConfig.findTarget(outboundEnv).isPresent();
    }

    @Override
    public OutboundSecurityResponse outboundSecurity(ProviderRequest providerRequest,
                                                     SecurityEnvironment outboundEnv,
                                                     EndpointConfig outboundEndpointConfig) {

        Optional<Object> maybeUsername = outboundEndpointConfig.abacAttribute(EndpointConfig.PROPERTY_OUTBOUND_ID);
        return maybeUsername
                .map(String::valueOf)
                .flatMap(username -> attemptImpersonation(outboundEnv, username))
                .orElseGet(() -> attemptPropagation(providerRequest, outboundEnv));
    }

    private JwkKeys verificationKeys(Jwt jwt) {
        if (jwt.keyId().isEmpty()) {
            return EMPTY_JWK_KEYS;
        }
        if (verifyKeys != null) {
            return verifyKeys;
        }
        if (verifyKeysLoader != null) {
            return verifyKeysLoader.get();
        }
        return EMPTY_JWK_KEYS;
    }

    private AuthenticationResponse unavailableOrAbstain(String message) {
        if (optional) {
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .description(message)
                    .build();
        }
        var builder = AuthenticationResponse.builder()
                .status(AuthenticationResponse.SecurityStatus.FAILURE)
                .description(message);
        if (useBearerChallenge) {
            return builder.statusCode(401)
                    .responseHeader("WWW-Authenticate", "Bearer")
                    .build();
        }
        return builder.statusCode(503)
                .build();
    }

    private OutboundSecurityResponse attemptPropagation(ProviderRequest providerRequest, SecurityEnvironment outboundEnv) {
        Optional<Subject> maybeSubject;
        if (subjectType == SubjectType.USER) {
            maybeSubject = providerRequest.securityContext().user();
        } else {
            maybeSubject = providerRequest.securityContext().service();
        }

        return maybeSubject.flatMap(subject -> {
            Optional<OutboundTarget> maybeTarget = outboundConfig.findTarget(outboundEnv);

            return maybeTarget.flatMap(target -> {
                JwtOutboundTarget jwtOutboundTarget = targetToJwtConfig
                        .computeIfAbsent(target, this::toOutboundTarget);

                if (null == jwtOutboundTarget.jwkKid) {
                    // just propagate existing token
                    return subject.publicCredential(TokenCredential.class)
                            .map(tokenCredential -> propagate(jwtOutboundTarget, tokenCredential.token()));
                } else {
                    // we do have kid - we are creating a new token of our own
                    return Optional.of(propagate(jwtOutboundTarget, subject));
                }
            });
        }).orElseGet(OutboundSecurityResponse::abstain);
    }

    private Optional<OutboundSecurityResponse> attemptImpersonation(SecurityEnvironment outboundEnv, String username) {
        if (allowImpersonation) {
            Optional<OutboundTarget> maybeTarget = outboundConfig.findTarget(outboundEnv);

            return maybeTarget.flatMap(target -> {
                JwtOutboundTarget jwtOutboundTarget = targetToJwtConfig.computeIfAbsent(target, this::toOutboundTarget);

                if (null == jwtOutboundTarget.jwkKid) {
                    return Optional.of(OutboundSecurityResponse.builder()
                                               .description("Cannot do explicit user propagation if no kid is defined.")
                                               .status(SecurityResponse.SecurityStatus.FAILURE)
                                               .build());
                } else {
                    // we do have kid - we are creating a new token of our own
                    return Optional.of(impersonate(jwtOutboundTarget, username));
                }
            });
        } else {
            return Optional.of(OutboundSecurityResponse.builder()
                                       .description(
                                               "Attempting to impersonate a user, when impersonation is not allowed"
                                                       + " for JWT provider")
                                       .status(SecurityResponse.SecurityStatus.FAILURE)
                                       .build());
        }
    }

    private OutboundSecurityResponse propagate(JwtOutboundTarget outboundTarget, String token) {
        Map<String, List<String>> headers = new HashMap<>();
        outboundTarget.outboundHandler.header(headers, token);
        return OutboundSecurityResponse.withHeaders(headers);
    }

    private OutboundSecurityResponse propagate(JwtOutboundTarget ot, Subject subject) {
        Map<String, List<String>> headers = new HashMap<>();
        Jwk jwk = signKeys.forKeyId(ot.jwkKid)
                .orElseThrow(() -> new JwtException("Signing JWK with kid: " + ot.jwkKid + " is not defined."));

        Principal principal = subject.principal();

        Jwt.Builder builder = Jwt.builder();

        principal.abacAttributeNames().forEach(name -> {
            principal.abacAttribute(name).ifPresent(val -> builder.addPayloadClaim(name, val));
        });

        principal.abacAttribute("full_name")
                .ifPresentOrElse(name -> builder.addPayloadClaim("name", name),
                                 () -> builder.removePayloadClaim("name"));

        builder.subject(principal.id())
                .preferredUsername(principal.getName())
                .issuer(issuer)
                .algorithm(jwk.algorithm());

        ot.update(builder);

        Jwt jwt = builder.build();
        SignedJwt signed = SignedJwt.sign(jwt, jwk);
        ot.outboundHandler.header(headers, signed.tokenContent());

        return OutboundSecurityResponse.withHeaders(headers);
    }

    private OutboundSecurityResponse impersonate(JwtOutboundTarget ot, String username) {
        Map<String, List<String>> headers = new HashMap<>();
        Jwk jwk = signKeys.forKeyId(ot.jwkKid)
                .orElseThrow(() -> new JwtException("Signing JWK with kid: " + ot.jwkKid + " is not defined."));

        Jwt.Builder builder = Jwt.builder();

        builder.addPayloadClaim("name", username);

        builder.subject(username)
                .preferredUsername(username)
                .issuer(issuer)
                .algorithm(jwk.algorithm());

        ot.update(builder);

        Jwt jwt = builder.build();
        SignedJwt signed = SignedJwt.sign(jwt, jwk);
        ot.outboundHandler.header(headers, signed.tokenContent());

        return OutboundSecurityResponse.withHeaders(headers);
    }

    private JwtOutboundTarget toOutboundTarget(OutboundTarget outboundTarget) {
        // first check if a custom object is defined
        Optional<? extends JwtOutboundTarget> customObject = outboundTarget.customObject(JwtOutboundTarget.class);
        if (customObject.isPresent()) {
            return customObject.get();
        }
        return JwtOutboundTarget.create(outboundTarget.getConfig()
                                                .orElse(Config.empty()), defaultTokenHandler);
    }

    /**
     * A custom object to configure specific handling of outbound calls.
     */
    public static class JwtOutboundTarget {
        /**
         * Default token validity for an outbound target.
         * Value is 1 day ({@value} seconds)
         */
        public static final long DEFAULT_VALIDITY_SECONDS = 60L * 60 * 24;
        /**
         * Default token validity before issue time.
         * This is used to allow for a time difference on machines - the default value of {@value} seconds means that
         * the token is valid up to {@value} seconds before it was issued.
         */
        public static final int DEFAULT_NOT_BEFORE_SECONDS = 5;
        private final TokenHandler outboundHandler;
        private final String jwtKid;
        private final String jwkKid;
        private final String jwtAudience;
        private final int notBeforeSeconds;
        private final long validitySeconds;

        private JwtOutboundTarget(Builder builder) {
            this.outboundHandler = builder.outboundHandler;
            this.jwtKid = builder.jwtKid;
            this.jwkKid = builder.jwkKid;
            this.jwtAudience = builder.jwtAudience;
            this.notBeforeSeconds = builder.notBeforeSeconds;
            this.validitySeconds = builder.validitySeconds;
        }

        /**
         * Get a fluent API builder to configure a new instance.
         *
         * @return a builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Load an instance from configuration.
         * Expected keys:
         * <ul>
         * <li>jwt-kid - the key id to put into JWT</li>
         * <li>jwk-kid - the key id to look for when signing the JWT</li>
         * <li>jwt-audience - the audience of this JWT</li>
         * <li>jwt-not-before-seconds - not before seconds</li>
         * <li>jwt-validity-seconds - validity of JWT</li>
         * </ul>
         *
         * @param config         configuration to load data from
         * @param defaultHandler default outbound token handler
         * @return a new instance configured from config
         * @see io.helidon.security.providers.jwt.JwtProvider.JwtOutboundTarget.Builder
         */
        public static JwtOutboundTarget create(Config config, TokenHandler defaultHandler) {
            return builder()
                    .tokenHandler(defaultHandler)
                    .config(config)
                    .build();
        }

        private void update(Jwt.Builder builder) {
            Instant now = Instant.now();
            Instant exp = now.plus(validitySeconds, ChronoUnit.SECONDS);
            Instant notBefore = now.minus(notBeforeSeconds, ChronoUnit.SECONDS);

            builder.issueTime(now)
                    .expirationTime(exp)
                    .notBefore(notBefore)
                    .keyId(jwtKid)
                    .addAudience(jwtAudience);
        }

        /**
         * Fluent API builder for {@link io.helidon.security.providers.jwt.JwtProvider.JwtOutboundTarget}.
         */
        public static final class Builder implements io.helidon.common.Builder<Builder, JwtOutboundTarget> {
            private TokenHandler outboundHandler = TokenHandler.builder()
                    .tokenHeader("Authorization")
                    .tokenPrefix("bearer ")
                    .build();
            private String jwtKid;
            private String jwkKid;
            private String jwtAudience;
            private int notBeforeSeconds = DEFAULT_NOT_BEFORE_SECONDS;
            private long validitySeconds = DEFAULT_VALIDITY_SECONDS;

            private Builder() {
            }

            @Override
            public JwtOutboundTarget build() {
                return new JwtOutboundTarget(this);
            }

            /**
             * Update builder from configuration. See
             * {@link JwtProvider.JwtOutboundTarget#create(Config, TokenHandler)}
             * for configuration options description.
             *
             * @param config to update builder from
             * @return updated builder instance
             */
            public Builder config(Config config) {
                config.get("outbound-token")
                        .as(TokenHandler::create)
                        .ifPresent(this::tokenHandler);

                config.get("jwt-kid").asString().ifPresent(this::jwtKid);
                config.get("jwk-kid").asString().ifPresent(this::jwkKid);
                config.get("jwt-audience").asString().ifPresent(this::jwtAudience);
                config.get("jwt-not-before-seconds").asInt().ifPresent(this::notBeforeSeconds);
                config.get("jwt-validity-seconds").asLong().ifPresent(this::validitySeconds);

                return this;
            }

            /**
             * Outbound token hanlder to insert the token into outbound request headers.
             *
             * @param outboundHandler handler to use
             * @return updated builder instance
             */
            public Builder tokenHandler(TokenHandler outboundHandler) {
                this.outboundHandler = outboundHandler;
                return this;
            }

            /**
             * JWT key id of the outbound token, used by target service to map
             * to configuration to validate our signature.
             *
             * @param jwtKid key id to be written to the JWT.
             * @return updated builder instance
             */
            public Builder jwtKid(String jwtKid) {
                this.jwtKid = jwtKid;
                return this;
            }

            /**
             * JWK key id to locate JWK to sign our request.
             *
             * @param jwkKid key id of JWK
             * @return updated builder instance
             */
            public Builder jwkKid(String jwkKid) {
                this.jwkKid = jwkKid;
                return this;
            }

            /**
             * JWT Audience.
             *
             * @param jwtAudience audience to be written to the outbound token
             * @return updated builder instance
             */
            public Builder jwtAudience(String jwtAudience) {
                this.jwtAudience = jwtAudience;
                return this;
            }

            /**
             * Allowed validity before issue time.
             *
             * @param notBeforeSeconds seconds the outbound token is valid before issue time
             * @return updated builder instance
             */
            public Builder notBeforeSeconds(int notBeforeSeconds) {
                this.notBeforeSeconds = notBeforeSeconds;
                return this;
            }

            /**
             * Validity of the token.
             *
             * @param validitySeconds seconds the token is valid for
             * @return updated builder instance
             */
            public Builder validitySeconds(long validitySeconds) {
                this.validitySeconds = validitySeconds;
                return this;
            }
        }

    }

    /**
     * Fluent API builder for {@link JwtProvider}.
     */
    @Configured(prefix = JwtProviderService.PROVIDER_CONFIG_KEY,
                description = "JWT authentication provider",
                provides = {SecurityProvider.class, AuthenticationProvider.class})
    public static final class Builder implements io.helidon.common.Builder<Builder, JwtProvider> {
        private static final RetryConfig DEFAULT_JWK_RETRY_CONFIG = RetryConfig.builder()
                .calls(2)
                .overallTimeout(Duration.ofSeconds(11))
                .addApplyOn(ResilientValue.UnavailableException.class)
                .buildPrototype();
        private static final CircuitBreakerConfig DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG = CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .addApplyOn(ResilientValue.UnavailableException.class)
                .buildPrototype();
        private static final TimeoutConfig DEFAULT_JWK_TIMEOUT_CONFIG = TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(5))
                .currentThread(true)
                .buildPrototype();

        private boolean verifySignature = true;
        private boolean optional = false;
        private boolean authenticate = true;
        private boolean propagate = true;
        private boolean allowImpersonation = false;
        private boolean allowUnsigned = false;
        private boolean useBearerChallenge = true;
        private SubjectType subjectType = SubjectType.USER;
        private TokenHandler atnTokenHandler = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPrefix("bearer ")
                .build();
        private OutboundConfig outboundConfig = OutboundConfig.builder().build();
        private JwkKeys verifyKeys;
        private ResourceConfig verifyKeysResource;
        private ResilientValue<JwkKeys> verifyKeysLoader;
        private RetryConfig jwkRetryConfig = DEFAULT_JWK_RETRY_CONFIG;
        private Retry jwkRetry;
        private Supplier<? extends Retry> jwkRetrySupplier;
        private CircuitBreakerConfig jwkCircuitBreakerConfig = DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG;
        private CircuitBreaker jwkCircuitBreaker;
        private Supplier<? extends CircuitBreaker> jwkCircuitBreakerSupplier;
        private TimeoutConfig jwkTimeoutConfig = DEFAULT_JWK_TIMEOUT_CONFIG;
        private Timeout jwkTimeout;
        private Supplier<? extends Timeout> jwkTimeoutSupplier;
        private JwkKeys signKeys;
        private String issuer;
        private String expectedAudience;
        private String expectedIssuer;
        private boolean useJwtGroups = true;
        private String jwtGroupsPath = DEFAULT_JWT_GROUPS_PATH;
        private String jwtGroupsSeparator;

        private Builder() {
        }

        @Override
        public JwtProvider build() {
            if (verifyKeysResource != null) {
                validateResourceConfig(verifyKeysResource);
                if (!isDynamic(verifyKeysResource) || (authenticate && verifySignature)) {
                    prepareVerifyKeys();
                } else {
                    verifyKeysLoader = null;
                }
            }
            if (verifyKeys != null && !allowUnsigned) {
                verifyKeys = requireUsableKeys(verifyKeys);
            }
            if (authenticate && verifySignature && !allowUnsigned && verifyKeys == null && verifyKeysLoader == null) {
                throw new JwtException("Failed to extract verify JWK from configuration");
            }
            if (authenticate
                    && !verifySignature
                    && (expectedIssuer == null
                    || expectedIssuer.isBlank()
                    || expectedAudience == null
                    || expectedAudience.isBlank())) {
                throw new JwtException("Expected issuer and audience must be configured when JWT signature validation"
                                               + " is disabled");
            }
            return new JwtProvider(this);
        }

        /**
         * Whether to propagate identity.
         *
         * @param propagate whether to propagate identity (true) or not (false)
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder propagate(boolean propagate) {
            this.propagate = propagate;
            return this;
        }

        /**
         * Whether to authenticate requests.
         *
         * @param authenticate whether to authenticate (true) or not (false)
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder authenticate(boolean authenticate) {
            this.authenticate = authenticate;
            return this;
        }

        /**
         * Whether to allow impersonation by explicitly overriding
         * username from outbound requests using {@link io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID}
         * property.
         * By default this is not allowed and identity can only be propagated.
         *
         * @param allowImpersonation set to true to allow impersonation
         * @return updated builder instance
         */
        @ConfiguredOption("false")
        public Builder allowImpersonation(boolean allowImpersonation) {
            this.allowImpersonation = allowImpersonation;
            return this;
        }

        /**
         * Configure support for unsigned JWTs without requiring verification JWKs.
         * If this is set to {@code true} any JWT that has algorithm
         * set to {@code none} and no {@code kid} defined will be accepted.
         * Such a token does not trigger loading of a configured verification JWK resource. Signed tokens continue to
         * require matching verification keys.
         * Note that this has serious security impact - if JWT can be sent
         *  from a third party, this allows the third party to send ANY JWT
         *  and it would be accepted as valid.
         *
         * @param allowUnsigned to allow unsigned (insecure) JWT
         * @return updated builder instance
         */
        @ConfiguredOption("false")
        public Builder allowUnsigned(boolean allowUnsigned) {
            this.allowUnsigned = allowUnsigned;
            return this;
        }

        /**
         * Configure whether to verify signatures.
         * Signatures verification is enabled by default. You can configure the provider
         * not to verify signatures.
         * <p>
         * <b>Make sure your service is properly secured on network level and only
         * accessible from a secure endpoint that provides the JWTs when signature verification
         * is disabled. If signature verification is disabled, configured claim validation still applies,
         * but signatures are not checked.</b>
         *
         * @param shouldValidate set to false to disable validation of JWT signatures
         * @return updated builder instance
         */
        @ConfiguredOption(key = "atn-token.verify-signature", value = "true")
        public Builder verifySignature(boolean shouldValidate) {
            this.verifySignature = shouldValidate;
            return this;
        }

        /**
         * Principal type this provider extracts (and also propagates).
         *
         * @param subjectType type of principal
         * @return updated builder instance
         */
        @ConfiguredOption(key = "principal-type", value = "USER")
        public Builder subjectType(SubjectType subjectType) {
            this.subjectType = subjectType;

            switch (subjectType) {
            case USER:
            case SERVICE:
                break;
            default:
                throw new SecurityException("Invalid configuration. Principal type not supported: " + subjectType);
            }

            return this;
        }

        /**
         * Token handler to extract username from request.
         *
         * @param tokenHandler token handler instance
         * @return updated builder instance
         */
        @ConfiguredOption(key = "atn-token.handler")
        public Builder atnTokenHandler(TokenHandler tokenHandler) {
            this.atnTokenHandler = tokenHandler;
            this.useBearerChallenge = false;
            return this;
        }

        /**
         * Whether authentication is required.
         * By default, request will fail if the username cannot be extracted.
         * If set to false, request will process and this provider will abstain.
         *
         * @param optional whether authentication is optional (true) or required (false)
         * @return updated builder instance
         */
        @ConfiguredOption("false")
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Configuration of outbound rules.
         *
         * @param config outbound configuration, each target may contain custom object {@link JwtOutboundTarget}
         *               to add our configuration.
         * @return updated builder instance
         */
        @ConfiguredOption(key = "sign-token")
        public Builder outboundConfig(OutboundConfig config) {
            this.outboundConfig = config;
            return this;
        }

        /**
         * JWK resource used to sign JWTs created by us.
         *
         * @param signJwkResource resource pointing to a JSON with keys
         * @return updated builder instance
         */
        @ConfiguredOption(key = "sign-token.jwk.resource")
        public Builder signJwk(Resource signJwkResource) {
            this.signKeys = JwkKeys.builder().resource(signJwkResource).build();
            return this;
        }

        /**
         * JWK resource used to verify JWTs created by other parties.
         *
         * @param verifyJwkResource resource pointing to a JSON with keys
         * @return updated builder instance
         */
        @ConfiguredOption(key = "atn-token.jwk.resource")
        public Builder verifyJwk(Resource verifyJwkResource) {
            this.verifyKeys = JwkKeys.builder()
                    .resource(Objects.requireNonNull(verifyJwkResource))
                    .build();
            this.verifyKeysResource = null;
            this.verifyKeysLoader = null;

            return this;
        }

        /**
         * Fixed JWK keys used to verify JWTs created by other parties.
         *
         * @param verifyKeys keys used to verify inbound JWTs
         * @return updated builder instance
         */
        public Builder verifyJwk(JwkKeys verifyKeys) {
            this.verifyKeys = Objects.requireNonNull(verifyKeys);
            this.verifyKeysResource = null;
            this.verifyKeysLoader = null;

            return this;
        }

        /**
         * JWK resource configuration used to verify JWTs created by other parties.
         * Filesystem paths and URIs are loaded lazily and protected by the configured retry and circuit breaker.
         * Classpath and inline resources are loaded when the provider is built.
         *
         * @param verifyJwkResource configuration of the resource containing verification keys
         * @return updated builder instance
         */
        public Builder verifyJwk(ResourceConfig verifyJwkResource) {
            this.verifyKeysResource = Objects.requireNonNull(verifyJwkResource);
            this.verifyKeys = null;
            this.verifyKeysLoader = null;

            return this;
        }

        /**
         * Retry used when loading verification keys from a filesystem path or URI; by default, it wraps two
         * timeout-guarded attempts within an 11-second overall timeout.
         *
         * @param jwkRetry retry to use
         * @return updated builder instance
         */
        @ConfiguredOption(key = "jwk-loader.retry", type = Retry.class)
        public Builder jwkRetry(Retry jwkRetry) {
            this.jwkRetry = Objects.requireNonNull(jwkRetry);
            this.jwkRetryConfig = jwkRetry.prototype();
            this.jwkRetrySupplier = null;
            this.verifyKeysLoader = null;

            return this;
        }

        /**
         * Retry used when loading verification keys from a filesystem path or URI.
         * The supplier is invoked only when a dynamic verification JWK source requires the retry.
         *
         * @param jwkRetry prototype of retry to use
         * @return updated builder instance
         */
        public Builder jwkRetry(RetryConfig jwkRetry) {
            this.jwkRetryConfig = Objects.requireNonNull(jwkRetry);
            this.jwkRetry = null;
            this.jwkRetrySupplier = null;
            this.verifyKeysLoader = null;
            return this;
        }

        /**
         * Retry used when loading verification keys from a filesystem path or URI.
         *
         * @param consumer consumer of builder of retry to use
         * @return updated builder instance
         */
        public Builder jwkRetry(Consumer<RetryConfig.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = RetryConfig.builder();
            consumer.accept(builder);
            return jwkRetry(builder.buildPrototype());
        }

        /**
         * Retry used when loading verification keys from a filesystem path or URI.
         *
         * @param supplier supplier of retry to use
         * @return updated builder instance
         */
        public Builder jwkRetry(Supplier<? extends Retry> supplier) {
            this.jwkRetrySupplier = Objects.requireNonNull(supplier);
            this.jwkRetryConfig = null;
            this.jwkRetry = null;
            this.verifyKeysLoader = null;
            return this;
        }

        /**
         * Timeout applied to each attempt to load verification keys from a filesystem path or URI; it defaults to
         * 5 seconds, must be positive, must execute on the current thread, and must not exceed the retry overall
         * timeout. Current-thread execution ensures that a retry cannot overlap an attempt that is still unwinding
         * after an interrupt. The deadline interrupts the loader; prompt termination also depends on the underlying
         * I/O honoring interruption or enforcing its own timeout.
         *
         * @param jwkTimeout timeout to use
         * @return updated builder instance
         */
        @ConfiguredOption(key = "jwk-loader.timeout", type = Timeout.class)
        public Builder jwkTimeout(Timeout jwkTimeout) {
            this.jwkTimeout = Objects.requireNonNull(jwkTimeout);
            this.jwkTimeoutConfig = jwkTimeout.prototype();
            this.jwkTimeoutSupplier = null;
            this.verifyKeysLoader = null;

            return this;
        }

        /**
         * Timeout applied to each attempt to load verification keys from a filesystem path or URI.
         * The supplier is invoked only when a dynamic verification JWK source requires the timeout.
         *
         * @param jwkTimeout prototype of timeout to use
         * @return updated builder instance
         */
        public Builder jwkTimeout(TimeoutConfig jwkTimeout) {
            this.jwkTimeoutConfig = Objects.requireNonNull(jwkTimeout);
            this.jwkTimeout = null;
            this.jwkTimeoutSupplier = null;
            this.verifyKeysLoader = null;
            return this;
        }

        /**
         * Timeout applied to each attempt to load verification keys from a filesystem path or URI.
         *
         * @param consumer consumer of builder of timeout to use
         * @return updated builder instance
         */
        public Builder jwkTimeout(Consumer<TimeoutConfig.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TimeoutConfig.builder(DEFAULT_JWK_TIMEOUT_CONFIG);
            consumer.accept(builder);
            return jwkTimeout(builder.buildPrototype());
        }

        /**
         * Timeout applied to each attempt to load verification keys from a filesystem path or URI.
         *
         * @param supplier supplier of timeout to use
         * @return updated builder instance
         */
        public Builder jwkTimeout(Supplier<? extends Timeout> supplier) {
            this.jwkTimeoutSupplier = Objects.requireNonNull(supplier);
            this.jwkTimeoutConfig = null;
            this.jwkTimeout = null;
            this.verifyKeysLoader = null;
            return this;
        }

        /**
         * Circuit breaker around each complete retry batch used to load verification keys from a filesystem path or
         * URI; by default, the circuit opens after one exhausted batch and permits a recovery probe after 5 seconds.
         *
         * @param jwkCircuitBreaker circuit breaker to use
         * @return updated builder instance
         */
        @ConfiguredOption(key = "jwk-loader.circuit-breaker", type = CircuitBreaker.class)
        public Builder jwkCircuitBreaker(CircuitBreaker jwkCircuitBreaker) {
            this.jwkCircuitBreaker = Objects.requireNonNull(jwkCircuitBreaker);
            this.jwkCircuitBreakerConfig = jwkCircuitBreaker.prototype();
            this.jwkCircuitBreakerSupplier = null;
            this.verifyKeysLoader = null;

            return this;
        }

        /**
         * Circuit breaker used when loading verification keys from a filesystem path or URI.
         * The supplier is invoked only when a dynamic verification JWK source requires the circuit breaker.
         *
         * @param jwkCircuitBreaker prototype of circuit breaker to use
         * @return updated builder instance
         */
        public Builder jwkCircuitBreaker(CircuitBreakerConfig jwkCircuitBreaker) {
            this.jwkCircuitBreakerConfig = Objects.requireNonNull(jwkCircuitBreaker);
            this.jwkCircuitBreaker = null;
            this.jwkCircuitBreakerSupplier = null;
            this.verifyKeysLoader = null;
            return this;
        }

        /**
         * Circuit breaker used when loading verification keys from a filesystem path or URI.
         *
         * @param consumer consumer of builder of circuit breaker to use
         * @return updated builder instance
         */
        public Builder jwkCircuitBreaker(Consumer<CircuitBreakerConfig.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = CircuitBreakerConfig.builder();
            consumer.accept(builder);
            return jwkCircuitBreaker(builder.buildPrototype());
        }

        /**
         * Circuit breaker used when loading verification keys from a filesystem path or URI.
         *
         * @param supplier supplier of circuit breaker to use
         * @return updated builder instance
         */
        public Builder jwkCircuitBreaker(Supplier<? extends CircuitBreaker> supplier) {
            this.jwkCircuitBreakerSupplier = Objects.requireNonNull(supplier);
            this.jwkCircuitBreakerConfig = null;
            this.jwkCircuitBreaker = null;
            this.verifyKeysLoader = null;
            return this;
        }

        /**
         * Issuer used to create new JWTs.
         *
         * @param issuer issuer to add to the issuer claim
         * @return updated builder instance
         */
        @ConfiguredOption(key = "sign-token.jwt-issuer")
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Load this builder from a configuration.
         *
         * @param config configuration to load from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("authenticate").asBoolean().ifPresent(this::authenticate);
            config.get("propagate").asBoolean().ifPresent(this::propagate);
            config.get("allow-impersonation").asBoolean().ifPresent(this::allowImpersonation);
            config.get("principal-type").asString().map(SubjectType::valueOf).ifPresent(this::subjectType);
            config.get("atn-token.handler").as(TokenHandler::create).ifPresent(this::atnTokenHandler);
            Config atnToken = config.get("atn-token");
            if (atnToken.exists()) {
                atnToken.get("verify-signature").asBoolean().ifPresent(this::verifySignature);
                verifyKeys(atnToken);
                atnToken.get("jwt-audience").asString().ifPresent(this::expectedAudience);
                atnToken.get("jwt-issuer").asString().ifPresent(this::expectedIssuer);
            }
            config.get("jwk-loader.retry")
                    .as(it -> RetryConfig.builder(DEFAULT_JWK_RETRY_CONFIG).config(it).buildPrototype())
                    .ifPresent(this::jwkRetry);
            config.get("jwk-loader.timeout")
                    .as(it -> TimeoutConfig.builder(DEFAULT_JWK_TIMEOUT_CONFIG).config(it).buildPrototype())
                    .ifPresent(this::jwkTimeout);
            config.get("jwk-loader.circuit-breaker")
                    .as(it -> CircuitBreakerConfig.builder(DEFAULT_JWK_CIRCUIT_BREAKER_CONFIG).config(it).buildPrototype())
                    .ifPresent(this::jwkCircuitBreaker);
            Config signToken = config.get("sign-token");
            if (signToken.exists()) {
                outboundConfig(OutboundConfig.create(signToken));
                outbound(signToken);
            }
            config.get("allow-unsigned").asBoolean().ifPresent(this::allowUnsigned);
            config.get("use-jwt-groups").asBoolean().ifPresent(this::useJwtGroups);
            config.get("jwt-groups-path").asString().ifPresent(this::jwtGroupsPath);
            config.get("jwt-groups-separator").asString().ifPresent(this::jwtGroupsSeparator);

            return this;
        }

        /**
         * Audience expected in inbound JWTs.
         *
         * @param audience audience string
         */
        @ConfiguredOption(key = "atn-token.jwt-audience")
        public void expectedAudience(String audience) {
            this.expectedAudience = audience;
        }

        /**
         * Issuer expected in inbound JWTs.
         *
         * @param issuer issuer string
         * @return updated builder instance
         */
        @ConfiguredOption(key = "atn-token.jwt-issuer")
        public Builder expectedIssuer(String issuer) {
            this.expectedIssuer = issuer;
            return this;
        }

        /**
         * Claim {@code groups} from JWT will be used to automatically add
         *  groups to current subject (may be used with {@link jakarta.annotation.security.RolesAllowed} annotation).
         *
         * @param useJwtGroups whether to use {@code groups} claim from JWT to retrieve roles
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder useJwtGroups(boolean useJwtGroups) {
            this.useJwtGroups = useJwtGroups;
            return this;
        }

        /**
         * Path to the JWT payload claim containing the groups to add as role grants.
         * The default path is {@code groups}. Nested object claims can be configured with slash-separated path segments,
         * such as {@code realm/groups}.
         *
         * @param jwtGroupsPath JWT groups claim path
         * @return updated builder instance
         */
        @ConfiguredOption("groups")
        public Builder jwtGroupsPath(String jwtGroupsPath) {
            this.jwtGroupsPath = requireText(jwtGroupsPath, "JWT groups path");
            return this;
        }

        /**
         * Separator used to split a string claim value into multiple groups.
         * This is used only when {@link #jwtGroupsPath(String)} configures a custom path other than {@code groups}.
         * The default {@code groups} claim keeps the standard JWT behavior.
         * Setting this property without changing the JWT groups path has no effect.
         *
         * @param jwtGroupsSeparator separator for string-valued custom groups claim
         * @return updated builder instance
         */
        @ConfiguredOption
        public Builder jwtGroupsSeparator(String jwtGroupsSeparator) {
            this.jwtGroupsSeparator = requireText(jwtGroupsSeparator, "JWT groups separator");
            return this;
        }

        private static String requireText(String value, String description) {
            Objects.requireNonNull(value, description + " must not be null");
            if (value.isEmpty()) {
                throw new IllegalArgumentException(description + " must not be empty");
            }
            return value;
        }

        private static JwkKeys loadDynamicKeys(ResourceConfig resourceConfig,
                                               String description,
                                               Duration ioTimeout) {
            try {
                return requireUsableKeys(JwkKeys.builder()
                                                 .resource(Resource.create(resourceConfig, ioTimeout))
                                                 .build());
            } catch (ResourceException e) {
                throw new ResilientValue.UnavailableException(description + " could not be read", e);
            } catch (JsonException e) {
                String detail = hasCause(e, IOException.class)
                        ? " could not be read"
                        : " does not contain valid JSON";
                throw new ResilientValue.UnavailableException(description + detail, e);
            } catch (JwtException e) {
                throw new ResilientValue.UnavailableException(description + " does not contain usable verification keys", e);
            }
        }

        private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
            Throwable current = throwable;
            while (current != null) {
                if (causeType.isInstance(current)) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }

        private static JwkKeys requireUsableKeys(JwkKeys keys) {
            Objects.requireNonNull(keys, "Verification JWK keys must not be null");
            if (keys.keys().isEmpty()) {
                throw new JwtException("Verification JWK does not contain any usable keys");
            }
            return keys;
        }

        private static boolean isDynamic(ResourceConfig resourceConfig) {
            return resourceConfig.path().isPresent() || resourceConfig.uri().isPresent();
        }

        private static String sourceDescription(ResourceConfig resourceConfig) {
            if (resourceConfig.path().isPresent()) {
                return "JWT verification JWK filesystem source";
            }
            return "JWT verification JWK URI source";
        }

        private static void validateResourceConfig(ResourceConfig resourceConfig) {
            int selectors = selectorCount(resourceConfig.resourcePath().isPresent(),
                                          resourceConfig.path().isPresent(),
                                          resourceConfig.uri().isPresent(),
                                          resourceConfig.contentPlain().isPresent(),
                                          resourceConfig.content().isPresent());
            if (selectors != 1) {
                throw new JwtException("Verification JWK resource must configure exactly one of resource-path, path, uri,"
                                               + " content-plain, or content");
            }
            resourceConfig.uri().ifPresent(Builder::validateUri);
            if (resourceConfig.uri().isEmpty()
                    && (resourceConfig.proxyHost().isPresent() || resourceConfig.proxy().isPresent())) {
                throw new JwtException("Verification JWK proxy can only be configured with a URI resource");
            }
            if (resourceConfig.proxyHost().filter(String::isBlank).isPresent()) {
                throw new JwtException("Verification JWK proxy host must not be blank");
            }
            if (resourceConfig.proxyHost().isPresent()
                    && (resourceConfig.proxyPort() < 1 || resourceConfig.proxyPort() > 65535)) {
                throw new JwtException("Verification JWK proxy port must be between 1 and 65535");
            }
        }

        private static void validateResourceConfig(Config resourceConfig) {
            int selectors = selectorCount(resourceConfig.get("resource-path").exists(),
                                          resourceConfig.get("path").exists(),
                                          resourceConfig.get("uri").exists(),
                                          resourceConfig.get("content-plain").exists(),
                                          resourceConfig.get("content").exists());
            if (selectors != 1) {
                throw new JwtException("Verification JWK resource must configure exactly one of resource-path, path, uri,"
                                               + " content-plain, or content");
            }
            boolean uri = resourceConfig.get("uri").exists();
            if (!uri && (resourceConfig.get("proxy-host").exists()
                    || resourceConfig.get("proxy-port").exists()
                    || resourceConfig.get("use-proxy").exists())) {
                throw new JwtException("Verification JWK proxy can only be configured with a URI resource");
            }
            if (resourceConfig.get("proxy-port").exists() && !resourceConfig.get("proxy-host").exists()) {
                throw new JwtException("Verification JWK proxy port requires proxy-host");
            }
            resourceConfig.get("proxy-host")
                    .asString()
                    .filter(String::isBlank)
                    .ifPresent(_ -> {
                        throw new JwtException("Verification JWK proxy host must not be blank");
                    });
        }

        private static int selectorCount(boolean... selectors) {
            int count = 0;
            for (boolean selector : selectors) {
                if (selector) {
                    count++;
                }
            }
            return count;
        }

        private static void validateUri(URI uri) {
            if (!uri.isAbsolute()) {
                throw new JwtException("Verification JWK URI must be absolute");
            }
            try {
                uri.toURL();
            } catch (MalformedURLException e) {
                throw new JwtException("Verification JWK URI scheme is not supported", e);
            }
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() == null) {
                throw new JwtException("Verification JWK HTTP URI must include a host");
            }
        }

        private void verifyKeys(Config config) {
            Config resource = config.get("jwk.resource");
            if (resource.exists()) {
                validateResourceConfig(resource);
                verifyJwk(ResourceConfig.create(resource));
            }
        }

        private Retry newJwkRetry() {
            if (jwkRetry != null) {
                return jwkRetry;
            }
            if (jwkRetrySupplier != null) {
                return Objects.requireNonNull(jwkRetrySupplier.get());
            }
            return RetryConfig.builder(jwkRetryConfig).build();
        }

        private CircuitBreaker newJwkCircuitBreaker() {
            if (jwkCircuitBreaker != null) {
                return jwkCircuitBreaker;
            }
            if (jwkCircuitBreakerSupplier != null) {
                return Objects.requireNonNull(jwkCircuitBreakerSupplier.get());
            }
            return CircuitBreakerConfig.builder(jwkCircuitBreakerConfig).build();
        }

        private Timeout newJwkTimeout() {
            if (jwkTimeout != null) {
                return jwkTimeout;
            }
            if (jwkTimeoutSupplier != null) {
                return Objects.requireNonNull(jwkTimeoutSupplier.get());
            }
            return TimeoutConfig.builder(jwkTimeoutConfig).build();
        }

        private void prepareVerifyKeys() {
            if (verifyKeys != null || verifyKeysResource == null) {
                return;
            }
            ResourceConfig resourceConfig = verifyKeysResource;
            if (isDynamic(resourceConfig)) {
                Retry retry = newJwkRetry();
                CircuitBreaker circuitBreaker = newJwkCircuitBreaker();
                Timeout timeout = newJwkTimeout();
                validateJwkFaultTolerance(retry, timeout);
                String description = sourceDescription(resourceConfig);
                Duration ioTimeout = timeout.prototype().timeout();
                verifyKeysLoader = ResilientValue.create(description,
                                                         () -> loadDynamicKeys(resourceConfig, description, ioTimeout),
                                                         retry,
                                                         circuitBreaker,
                                                         timeout);
            } else {
                verifyKeys = JwkKeys.builder()
                        .resource(Resource.create(resourceConfig))
                        .build();
            }
        }

        private void validateJwkFaultTolerance(Retry retry, Timeout timeout) {
            Duration timeoutDuration = timeout.prototype().timeout();
            if (timeoutDuration.isNegative() || timeoutDuration.isZero()) {
                throw new IllegalArgumentException("jwk-loader.timeout.timeout must be positive");
            }
            if (!timeout.prototype().currentThread()) {
                throw new IllegalArgumentException("jwk-loader.timeout.current-thread must be true");
            }
            if (timeoutDuration.compareTo(retry.prototype().overallTimeout()) > 0) {
                throw new IllegalArgumentException("jwk-loader.timeout.timeout must not exceed "
                                                           + "jwk-loader.retry.overall-timeout");
            }
        }

        private void outbound(Config config) {
            config.get("jwt-issuer").asString().ifPresent(this::issuer);

            // jwk is optional, we may be propagating existing token
            config.get("jwk.resource").as(Resource::create).ifPresent(this::signJwk);
        }
    }
}
