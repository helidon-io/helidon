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

package io.helidon.security.provider.jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Errors;
import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
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
import io.helidon.security.providers.OutboundConfig;
import io.helidon.security.providers.OutboundTarget;
import io.helidon.security.providers.TokenCredential;
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
public class JwtProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    /**
     * Configure this for outbound requests to override user to use.
     */
    public static final String EP_PROPERTY_OUTBOUND_USER = "io.helidon.security.outbound.user";

    private final boolean optional;
    private final boolean authenticate;
    private final boolean propagate;
    private final boolean allowImpersonation;
    private final SubjectType subjectType;
    private final TokenHandler atnTokenHandler;
    private final TokenHandler defaultTokenHandler;
    private final JwkKeys verifyKeys;
    private final String expectedAudience;
    private final JwkKeys signKeys;
    private final OutboundConfig outboundConfig;
    private final String issuer;
    private final Map<OutboundTarget, JwtOutboundTarget> targetToJwtConfig = new IdentityHashMap<>();

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

        if (null == atnTokenHandler) {
            defaultTokenHandler = TokenHandler.builder()
                    .tokenHeader("Authorization")
                    .tokenPrefix("bearer ")
                    .build();
        } else {
            defaultTokenHandler = atnTokenHandler;
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
    public static JwtProvider fromConfig(Config config) {
        return builder().fromConfig(config).build();
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        if (!authenticate) {
            return AuthenticationResponse.abstain();
        }

        return atnTokenHandler.extractToken(providerRequest.getEnv().getHeaders())
                .map(token -> {
                    SignedJwt signedJwt = SignedJwt.parseToken(token);
                    Errors errors = signedJwt.verifySignature(verifyKeys);
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
                }).orElseGet(() -> {
                    if (optional) {
                        return AuthenticationResponse.abstain();
                    } else {
                        return AuthenticationResponse.failed("Header not available or in a wrong format");
                    }
                });
    }

    Subject buildSubject(Jwt jwt, SignedJwt signedJwt) {
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

        scopes.ifPresent(scopeList -> {
            scopeList.forEach(scope -> subjectBuilder.addGrant(Grant.builder()
                                                                       .name(scope)
                                                                       .type("scope")
                                                                       .build()));
        });

        return subjectBuilder.build();

    }

    Principal buildPrincipal(Jwt jwt) {
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

        Optional<Object> maybeUsername = outboundEndpointConfig.getAttribute(EP_PROPERTY_OUTBOUND_USER);
        return maybeUsername
                .map(String::valueOf)
                .flatMap(username -> {
                    if (!allowImpersonation) {
                        return Optional.of(OutboundSecurityResponse.builder()
                                                   .description(
                                                           "Attempting to impersonate a user, when impersonation is not allowed"
                                                                   + " for JWT provider")
                                                   .status(SecurityResponse.SecurityStatus.FAILURE)
                                                   .build());
                    }

                    // TODO allow additional claims from config?
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
                }).orElseGet(() -> {
                    Optional<Subject> maybeSubject;
                    if (subjectType == SubjectType.USER) {
                        maybeSubject = providerRequest.getContext().getUser();
                    } else {
                        maybeSubject = providerRequest.getContext().getService();
                    }

                    return maybeSubject.flatMap(subject -> {
                        Optional<OutboundTarget> maybeTarget = outboundConfig.findTarget(outboundEnv);

                        return maybeTarget.flatMap(target -> {
                            JwtOutboundTarget jwtOutboundTarget = targetToJwtConfig
                                    .computeIfAbsent(target, this::toOutboundTarget);

                            if (null == jwtOutboundTarget.jwkKid) {
                                // just propagate existing token
                                return subject.getPublicCredential(TokenCredential.class)
                                        .map(tokenCredential -> propagate(jwtOutboundTarget, tokenCredential.getToken()));
                            } else {
                                // we do have kid - we are creating a new token of our own
                                return Optional.of(propagate(jwtOutboundTarget, subject));
                            }
                        });
                    }).orElseGet(OutboundSecurityResponse::abstain);
                });
    }

    private OutboundSecurityResponse propagate(JwtOutboundTarget outboundTarget, String token) {
        Map<String, List<String>> headers = new HashMap<>();
        outboundTarget.outboundHandler.setHeader(headers, token);
        return OutboundSecurityResponse.withHeaders(headers);
    }

    private OutboundSecurityResponse propagate(JwtOutboundTarget ot, Subject subject) {
        Map<String, List<String>> headers = new HashMap<>();
        Jwk jwk = signKeys.forKeyId(ot.jwkKid)
                .orElseThrow(() -> new JwtException("Signing JWK with kid: " + ot.jwkKid + " is not defined."));

        Principal principal = subject.getPrincipal();

        Jwt.Builder builder = Jwt.builder();

        principal.getAttributeNames().forEach(name -> {
            principal.getAttribute(name).ifPresent(val -> builder.addPayloadClaim(name, val));
        });

        OptionalHelper.from(principal.getAttribute("full_name"))
                .ifPresentOrElse(name -> builder.addPayloadClaim("name", name),
                                 () -> builder.removePayloadClaim("name"));

        builder.subject(principal.getId())
                .preferredUsername(principal.getName())
                .issuer(issuer)
                .algorithm(jwk.getAlgorithm());

        ot.update(builder);

        Jwt jwt = builder.build();
        SignedJwt signed = SignedJwt.sign(jwt, jwk);
        ot.outboundHandler.setHeader(headers, signed.getTokenContent());

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
                .algorithm(jwk.getAlgorithm());

        ot.update(builder);

        Jwt jwt = builder.build();
        SignedJwt signed = SignedJwt.sign(jwt, jwk);
        ot.outboundHandler.setHeader(headers, signed.getTokenContent());

        return OutboundSecurityResponse.withHeaders(headers);
    }

    private JwtOutboundTarget toOutboundTarget(OutboundTarget outboundTarget) {
        // first check if a custom object is defined
        Optional<? extends JwtOutboundTarget> customObject = outboundTarget.getCustomObject(JwtOutboundTarget.class);
        if (customObject.isPresent()) {
            return customObject.get();
        }
        return JwtOutboundTarget.fromConfig(outboundTarget.getConfig()
                                                    .orElse(Config.empty()), defaultTokenHandler);
    }

    /**
     * A custom object to configure specific handling of outbound calls.
     */
    public static class JwtOutboundTarget {
        private final TokenHandler outboundHandler;
        private final String jwtKid;
        private final String jwkKid;
        private final String jwtAudience;
        private final int notBeforeSeconds;
        private final long validitySeconds;

        /**
         * Create an instance to add to {@link OutboundTarget}.
         *
         * @param outboundHandler  token handler to inject JWT into outbound headers
         * @param jwtKid           key id to put into a JWT
         * @param jwkKid           key id to use to sign using JWK - if not defined, existing token will be propagated if present
         * @param audience         audience to create a JWT for
         * @param notBeforeSeconds seconds before now the token is valid (e.g. now - notBeforeSeconds = JWT not before)
         * @param validitySeconds  seconds after now the token is valid (e.g. now + validitySeconds = JWT expiration time)
         */
        public JwtOutboundTarget(TokenHandler outboundHandler,
                                 String jwtKid,
                                 String jwkKid,
                                 String audience,
                                 int notBeforeSeconds,
                                 long validitySeconds
        ) {
            this.outboundHandler = outboundHandler;
            this.jwtKid = jwtKid;
            this.jwkKid = jwkKid;
            this.jwtAudience = audience;
            this.notBeforeSeconds = notBeforeSeconds;
            this.validitySeconds = validitySeconds;
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
         * @see #JwtOutboundTarget(TokenHandler, String, String, String, int, long)
         */
        public static JwtOutboundTarget fromConfig(Config config, TokenHandler defaultHandler) {
            TokenHandler tokenHandler = config.get("outbound-token")
                    .asOptional(Config.class)
                    .map(TokenHandler::fromConfig)
                    .orElse(defaultHandler);

            return new JwtOutboundTarget(
                    tokenHandler,
                    config.get("jwt-kid").asString(null),
                    config.get("jwk-kid").asString(null),
                    config.get("jwt-audience").asString(null),
                    config.get("jwt-not-before-seconds").asInt(5),
                    config.get("jwt-validity-seconds").asLong(60 * 60 * 24));
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
    }

    /**
     * Fluent API builder for {@link JwtProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<JwtProvider> {
        private boolean optional = false;
        private boolean authenticate = true;
        private boolean propagate = true;
        private boolean allowImpersonation = false;
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

        @Override
        public JwtProvider build() {
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
        public Builder fromConfig(Config config) {
            config.get("optional").asOptional(Boolean.class).ifPresent(this::optional);
            config.get("authenticate").asOptional(Boolean.class).ifPresent(this::authenticate);
            config.get("propagate").asOptional(Boolean.class).ifPresent(this::propagate);
            config.get("allow-impersonation").asOptionalBoolean().ifPresent(this::allowImpersonation);
            config.get("principal-type").asOptional(SubjectType.class).ifPresent(this::subjectType);
            config.get("atn-token.handler").asOptional(TokenHandler.class).ifPresent(this::atnTokenHandler);
            config.get("atn-token").ifExists(this::verifyKeys);
            config.get("atn-token.jwt-audience").asOptionalString().ifPresent(this::expectedAudience);
            config.get("sign-token").ifExists(outbound -> outboundConfig(OutboundConfig.parseTargets(outbound)));
            config.get("sign-token").ifExists(this::outbound);

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

        private void verifyKeys(Config config) {
            verifyJwk(Resource.from(config, "jwk")
                              .orElseThrow(() -> new JwtException("Failed to extract verify JWK from configuration")));
        }

        private void outbound(Config config) {
            // jwk is optional, we may be propagating existing token
            Resource.from(config, "jwk").ifPresent(this::signJwk);
            config.get("jwt-issuer").asOptionalString().ifPresent(this::issuer);
        }
    }
}
