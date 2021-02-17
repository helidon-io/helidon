/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
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
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.security.util.TokenHandler;

/**
 * Provider that can process JWT tokens in request headers and assert identity (e.g. create a {@link Principal}
 * for a {@link io.helidon.security.SubjectType#USER} or {@link io.helidon.security.SubjectType#SERVICE}.
 * This provider can also propagate identity using JWT token, either by creating a new
 * JWT or by propagating the existing token "as is".
 * Verification and signatures of tokens is done through JWK standard - two separate
 * JWK files are expected (one for verification, one for signatures).
 */
public final class JwtProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private static final Logger LOGGER = Logger.getLogger(JwtProvider.class.getName());

    /**
     * Configure this for outbound requests to override user to use.
     */
    public static final String EP_PROPERTY_OUTBOUND_USER = "io.helidon.security.outbound.user";

    private final boolean optional;
    private final boolean authenticate;
    private final boolean propagate;
    private final boolean allowImpersonation;
    private final boolean verifySignature;
    private final SubjectType subjectType;
    private final TokenHandler atnTokenHandler;
    private final TokenHandler defaultTokenHandler;
    private final JwkKeys verifyKeys;
    private final String expectedAudience;
    private final JwkKeys signKeys;
    private final OutboundConfig outboundConfig;
    private final String issuer;
    private final Map<OutboundTarget, JwtOutboundTarget> targetToJwtConfig = new IdentityHashMap<>();
    private final Jwk defaultJwk;
    private final boolean useJwtGroups;

    private JwtProvider(Builder builder) {
        this.optional = builder.optional;
        this.authenticate = builder.authenticate;
        this.propagate = builder.propagate;
        this.allowImpersonation = builder.allowImpersonation;
        this.subjectType = builder.subjectType;
        this.atnTokenHandler = builder.atnTokenHandler;
        this.outboundConfig = builder.outboundConfig;
        this.verifyKeys = builder.verifyKeys;
        this.signKeys = builder.signKeys;
        this.issuer = builder.issuer;
        this.expectedAudience = builder.expectedAudience;
        this.verifySignature = builder.verifySignature;
        this.useJwtGroups = builder.useJwtGroups;

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
            LOGGER.info("JWT Signature validation is disabled. Any JWT will be accepted.");
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
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        if (!authenticate) {
            return AuthenticationResponse.abstain();
        }
        Optional<String> maybeToken;
        try {
            maybeToken = atnTokenHandler.extractToken(providerRequest.env().headers());
        } catch (Exception e) {
            if (optional) {
                // maybe the token is for somebody else
                return AuthenticationResponse.abstain();
            } else {
                return AuthenticationResponse.failed("JWT header not available or in a wrong format", e);
            }
        }

        return maybeToken
                .map(this::authenticateToken)
                .orElseGet(() -> {
                    if (optional) {
                        return AuthenticationResponse.abstain();
                    } else {
                        return AuthenticationResponse.failed("JWT header not available or in a wrong format");
                    }
                });
    }

    private AuthenticationResponse authenticateToken(String token) {
        SignedJwt signedJwt;
        try {
            signedJwt = SignedJwt.parseToken(token);
        } catch (Exception e) {
            //invalid token
            return AuthenticationResponse.failed("Invalid token", e);
        }
        if (verifySignature) {
            Errors errors = signedJwt.verifySignature(verifyKeys, defaultJwk);
            if (errors.isValid()) {
                Jwt jwt = signedJwt.getJwt();
                // verify the audience is correct
                Errors validate = jwt.validate(null, expectedAudience);
                if (validate.isValid()) {
                    return AuthenticationResponse.success(buildSubject(jwt, signedJwt));
                } else {
                    return AuthenticationResponse.failed("Audience is invalid or missing: " + expectedAudience);
                }
            } else {
                return AuthenticationResponse.failed(errors.toString());
            }
        } else {
            return AuthenticationResponse.success(buildSubject(signedJwt.getJwt(), signedJwt));
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
            Optional<List<String>> userGroups = jwt.userGroups();
            userGroups.ifPresent(groups -> groups.forEach(group -> subjectBuilder.addGrant(Role.create(group))));
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

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        return propagate;
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {

        Optional<Object> maybeUsername = outboundEndpointConfig.abacAttribute(EP_PROPERTY_OUTBOUND_USER);
        return maybeUsername
                .map(String::valueOf)
                .flatMap(username -> attemptImpersonation(outboundEnv, username))
                .orElseGet(() -> attemptPropagation(providerRequest, outboundEnv));
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
        if (allowImpersonation) {// TODO allow additional claims from config?
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
                    .audience(jwtAudience);
        }

        /**
         * Fluent API builder for {@link io.helidon.security.providers.jwt.JwtProvider.JwtOutboundTarget}.
         */
        public static final class Builder implements io.helidon.common.Builder<JwtOutboundTarget> {
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
                        .asNode()
                        .map(TokenHandler::create)
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
    public static final class Builder implements io.helidon.common.Builder<JwtProvider> {
        private boolean verifySignature = true;
        private boolean optional = false;
        private boolean authenticate = true;
        private boolean propagate = true;
        private boolean allowImpersonation = false;
        private boolean allowUnsigned = false;
        private SubjectType subjectType = SubjectType.USER;
        private TokenHandler atnTokenHandler = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPrefix("bearer ")
                .build();
        private OutboundConfig outboundConfig;
        private JwkKeys verifyKeys;
        private JwkKeys signKeys;
        private String issuer;
        private String expectedAudience;
        private boolean useJwtGroups = true;

        private Builder() {
        }

        @Override
        public JwtProvider build() {
            if (verifySignature && (null == verifyKeys)) {
                throw new JwtException("Failed to extract verify JWK from configuration");
            }
            return new JwtProvider(this);
        }

        /**
         * Whether to propagate identity.
         *
         * @param propagate whether to propagate identity (true) or not (false)
         * @return updated builder instance
         */
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
        public Builder authenticate(boolean authenticate) {
            this.authenticate = authenticate;
            return this;
        }

        /**
         * Whether to allow impersonation by explicitly overriding
         * username from outbound requests using {@link #EP_PROPERTY_OUTBOUND_USER} property.
         * By default this is not allowed and identity can only be propagated.
         *
         * @param allowImpersonation set to true to allow impersonation
         * @return updated builder instance
         */
        public Builder allowImpersonation(boolean allowImpersonation) {
            this.allowImpersonation = allowImpersonation;
            return this;
        }

        /**
         * Configure support for unsigned JWT.
         * If this is set to {@code true} any JWT that has algorithm
         * set to {@code none} and no {@code kid} defined will be accepted.
         * Note that this has serious security impact - if JWT can be sent
         *  from a third party, this allows the third party to send ANY JWT
         *  and it would be accpted as valid.
         *
         * @param allowUnsigned to allow unsigned (insecure) JWT
         * @return updated builder insdtance
         */
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
         * is disabled. If signature verification is disabled, this service will accept <i>ANY</i> JWT</b>
         *
         * @param shouldValidate set to false to disable validation of JWT signatures
         * @return updated builder instance
         */
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
        public Builder atnTokenHandler(TokenHandler tokenHandler) {
            this.atnTokenHandler = tokenHandler;

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
        public Builder verifyJwk(Resource verifyJwkResource) {
            this.verifyKeys = JwkKeys.builder().resource(verifyJwkResource).build();

            return this;
        }

        /**
         * Issuer used to create new JWTs.
         *
         * @param issuer issuer to add to the issuer claim
         * @return updated builder instance
         */
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
            config.get("atn-token").ifExists(this::verifyKeys);
            config.get("atn-token.jwt-audience").asString().ifPresent(this::expectedAudience);
            config.get("atn-token.verify-signature").asBoolean().ifPresent(this::verifySignature);
            config.get("sign-token").ifExists(outbound -> outboundConfig(OutboundConfig.create(outbound)));
            config.get("sign-token").ifExists(this::outbound);
            config.get("allow-unsigned").asBoolean().ifPresent(this::allowUnsigned);
            config.get("use-jwt-groups").asBoolean().ifPresent(this::useJwtGroups);

            return this;
        }

        /**
         * Audience expected in inbound JWTs.
         *
         * @param audience audience string
         */
        public void expectedAudience(String audience) {
            this.expectedAudience = audience;
        }

        /**
         * Claim {@code groups} from JWT will be used to automatically add
         *  groups to current subject (may be used with {@link javax.annotation.security.RolesAllowed} annotation).
         *
         * @param useJwtGroups whether to use {@code groups} claim from JWT to retrieve roles
         * @return updated builder instance
         */
        public Builder useJwtGroups(boolean useJwtGroups) {
            this.useJwtGroups = useJwtGroups;
            return this;
        }

        private void verifyKeys(Config config) {
            config.get("jwk.resource").as(Resource::create).ifPresent(this::verifyJwk);

            // backward compatibility
            Resource.create(config, "jwk").ifPresent(this::verifyJwk);
        }

        private void outbound(Config config) {
            config.get("jwt-issuer").asString().ifPresent(this::issuer);


            // jwk is optional, we may be propagating existing token
            config.get("jwk.resource").as(Resource::create).ifPresent(this::signJwk);
            // backward compatibility
            Resource.create(config, "jwk").ifPresent(this::signJwk);

        }
    }
}
