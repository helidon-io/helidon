/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.jwt.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.Errors;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredValue;
import io.helidon.http.HeaderNames;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityException;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.jwt.EncryptedJwt;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtHeaders;
import io.helidon.security.jwt.JwtValidator;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.Validator;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkEC;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.util.TokenHandler;

import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.jwt.JsonWebToken;

import static io.helidon.security.EndpointConfig.PROPERTY_OUTBOUND_ID;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provider that provides JWT authentication.
 */
public class JwtAuthProvider implements AuthenticationProvider, OutboundSecurityProvider {

    /**
     * Configuration key for expected issuer of incoming tokens. Used for validation of JWT.
     */
    public static final String CONFIG_EXPECTED_ISSUER = "mp.jwt.verify.issuer";
    /**
     * Configuration key for expected audiences of incoming tokens. Used for validation of JWT.
     */
    public static final String CONFIG_EXPECTED_AUDIENCES = "mp.jwt.verify.audiences";

    private static final String CONFIG_EXPECTED_MAX_TOKEN_AGE = "mp.jwt.verify.token.age";
    private static final String CONFIG_CLOCK_SKEW = "mp.jwt.verify.clock.skew";
    /**
     * Configuration of Cookie property name which contains JWT token.
     *
     * This will be ignored unless {@link #CONFIG_JWT_HEADER} is set to {@link io.helidon.http.HeaderNames#COOKIE}.
     */
    private static final String CONFIG_COOKIE_PROPERTY_NAME = "mp.jwt.token.cookie";
    /**
     * Configuration of the header where the JWT token is set.
     *
     * Default value is {@link io.helidon.http.HeaderNames#AUTHORIZATION}.
     */
    private static final String CONFIG_JWT_HEADER = "mp.jwt.token.header";
    private static final System.Logger LOGGER = System.getLogger(JwtAuthProvider.class.getName());
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    private final boolean optional;
    private final boolean authenticate;
    private final boolean propagate;
    private final boolean allowImpersonation;
    private final SubjectType subjectType;
    private final TokenHandler atnTokenHandler;
    private final TokenHandler defaultTokenHandler;
    private final LazyValue<JwkKeys> verifyKeys;
    private final Set<String> expectedAudiences;
    private final JwkKeys signKeys;
    private final LazyValue<JwkKeys> decryptionKeys;
    private final OutboundConfig outboundConfig;
    private final String issuer;
    private final LazyValue<Jwk> defaultJwk;
    private final LazyValue<Jwk> defaultDecryptionJwk;
    private final Map<OutboundTarget, JwtOutboundTarget> targetToJwtConfig = new IdentityHashMap<>();
    private final ReentrantLock targetToJwtConfigLock = new ReentrantLock();
    private final String expectedIssuer;
    private final String cookiePrefix;
    private final String decryptionKeyAlgorithm;
    private final boolean useCookie;
    private final Duration expectedMaxTokenAge;
    private final Duration clockSkew;

    private JwtAuthProvider(Builder builder) {
        this.optional = builder.optional;
        this.authenticate = builder.authenticate;
        this.propagate = builder.propagate && builder.outboundConfig.targets().size() > 0;
        this.allowImpersonation = builder.allowImpersonation;
        this.subjectType = builder.subjectType;
        this.atnTokenHandler = builder.atnTokenHandler;
        this.outboundConfig = builder.outboundConfig;
        this.verifyKeys = builder.verifyKeys;
        this.signKeys = builder.signKeys;
        this.issuer = builder.issuer;
        this.expectedAudiences = builder.expectedAudiences;
        this.defaultJwk = builder.defaultJwk;
        this.expectedIssuer = builder.expectedIssuer;
        this.cookiePrefix = builder.cookieProperty + "=";
        this.useCookie = builder.useCookie;
        this.decryptionKeys = builder.decryptionKeys;
        this.defaultDecryptionJwk = builder.defaultDecryptionJwk;
        this.decryptionKeyAlgorithm = builder.decryptionKeyAlgorithm;
        this.expectedMaxTokenAge = builder.expectedMaxTokenAge;
        this.clockSkew = builder.clockSkew;

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
    public static JwtAuthProvider create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(LoginConfig.class);
    }

    @Override
    public AuthenticationResponse authenticate(ProviderRequest providerRequest) {
        if (!authenticate) {
            return AuthenticationResponse.abstain();
        }

        //Obtains Application level of security
        List<LoginConfig> loginConfigs = providerRequest.endpointConfig().securityLevels().get(0)
                .filterAnnotations(LoginConfig.class, EndpointConfig.AnnotationScope.CLASS);

        try {
            return loginConfigs.stream()
                    .filter(JwtAuthAnnotationAnalyzer::isMpJwt)
                    .findFirst()
                    .map(loginConfig -> authenticate(providerRequest, loginConfig))
                    .orElseGet(AuthenticationResponse::abstain);
        } catch (java.lang.SecurityException e) {
            return AuthenticationResponse.failed("Failed to process authentication header", e);
        }
    }

    AuthenticationResponse authenticate(ProviderRequest providerRequest, LoginConfig loginConfig) {
        Optional<String> maybeToken;
        try {
            Map<String, List<String>> headers = providerRequest.env().headers();
            if (useCookie) {
                maybeToken = findCookie(headers);
            } else {
                maybeToken = atnTokenHandler.extractToken(headers);
            }
        } catch (Exception e) {
            if (optional) {
                return AuthenticationResponse.abstain();
            } else {
                return AuthenticationResponse.failed("Header not available or in a wrong format", e);
            }
        }

        return maybeToken
                .map(token -> {
                    JwtHeaders headers;
                    SignedJwt signedJwt;
                    try {
                        headers = JwtHeaders.parseToken(token);
                        if (headers.encryption().isPresent() || decryptionKeys.get() != null) {
                            EncryptedJwt encryptedJwt = EncryptedJwt.parseToken(headers, token);
                            if (!headers.contentType().map("JWT"::equals).orElse(false)) {
                                throw new JwtException("Header \"cty\" (content type) must be set to \"JWT\" "
                                                               + "for encrypted tokens");
                            }
                            List<Validator<EncryptedJwt>> validators = new LinkedList<>();
                            EncryptedJwt.addKekValidator(validators, decryptionKeyAlgorithm, true);
                            Errors errors = encryptedJwt.validate(validators);
                            if (errors.isValid()) {
                                signedJwt = encryptedJwt.decrypt(decryptionKeys.get(), defaultDecryptionJwk.get());
                            } else {
                                return AuthenticationResponse.failed(errors.toString());
                            }
                        } else {
                            signedJwt = SignedJwt.parseToken(token);
                        }
                    } catch (Exception e) {
                        if (LOGGER.isLoggable(Level.TRACE)) {
                            LOGGER.log(Level.TRACE, "Failed to parse token String into JWT", e);
                        }
                        //invalid token
                        return AuthenticationResponse.failed("Invalid token", e);
                    }
                    Errors errors = signedJwt.verifySignature(verifyKeys.get(), defaultJwk.get());
                    if (errors.isValid()) {
                        Jwt jwt = signedJwt.getJwt();
                        JwtValidator.Builder valBuilder = JwtValidator.builder();
                        if (expectedIssuer != null) {
                            // validate issuer
                            valBuilder.addIssuerValidator(expectedIssuer);
                        }
                        if (!expectedAudiences.isEmpty()) {
                            // validate audience(s)
                            valBuilder.addAudienceValidator(expectedAudiences);
                        }
                        // validate user principal is present
                        valBuilder.addUserPrincipalValidator()
                                .addExpirationValidator(builder -> builder.now(Instant.now())
                                        .allowedTimeSkew(clockSkew)
                                        .mandatory(true));
                        if (expectedMaxTokenAge != null) {
                            valBuilder.addMaxTokenAgeValidator(builder -> builder.expectedMaxTokenAge(expectedMaxTokenAge)
                                    .allowedTimeSkew(clockSkew)
                                    .mandatory(true));
                        }
                        JwtValidator jwtValidator = valBuilder.build();
                        Errors validate = jwtValidator.validate(jwt);

                        if (validate.isValid()) {
                            return AuthenticationResponse.success(buildSubject(jwt, signedJwt));
                        } else {
                            return AuthenticationResponse.failed(validate.toString());
                        }
                    } else {
                        return AuthenticationResponse.failed(errors.toString());
                    }
                }).orElseGet(AuthenticationResponse::abstain);
    }

    private Optional<String> findCookie(Map<String, List<String>> headers) {
        List<String> cookies = headers.get(HeaderNames.COOKIE.defaultCase());
        if ((null == cookies) || cookies.isEmpty()) {
            return Optional.empty();
        }

        for (String cookie : cookies) {
            //a=b; c=d; e=f
            String[] cookieValues = cookie.split(";\\s?");
            for (String cookieValue : cookieValues) {
                String trimmed = cookieValue.trim();
                if (trimmed.startsWith(cookiePrefix)) {
                    return Optional.of(trimmed.substring(cookiePrefix.length()));
                }
            }
        }

        return Optional.empty();
    }

    Subject buildSubject(Jwt jwt, SignedJwt signedJwt) {
        JsonWebTokenImpl principal = buildPrincipal(signedJwt);

        TokenCredential.Builder builder = TokenCredential.builder();
        jwt.issueTime().ifPresent(builder::issueTime);
        jwt.expirationTime().ifPresent(builder::expTime);
        jwt.issuer().ifPresent(builder::issuer);
        builder.token(signedJwt.tokenContent());
        builder.addToken(JsonWebToken.class, principal);
        builder.addToken(Jwt.class, jwt);
        builder.addToken(SignedJwt.class, signedJwt);

        Subject.Builder subjectBuilder = Subject.builder()
                .principal(principal)
                .addPublicCredential(TokenCredential.class, builder.build());

        Optional<List<String>> userGroups = jwt.userGroups();
        userGroups.ifPresent(groups -> groups.forEach(group -> subjectBuilder.addGrant(Role.create(group))));

        Optional<List<String>> scopes = jwt.scopes();
        scopes.ifPresent(scopeList ->
                                 scopeList.forEach(scope -> subjectBuilder.addGrant(Grant.builder()
                                                                                            .name(scope)
                                                                                            .type("scope")
                                                                                            .build())));

        return subjectBuilder.build();
    }

    static JsonWebTokenImpl buildPrincipal(SignedJwt signedJwt) {
        return JsonWebTokenImpl.create(signedJwt);
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

        Optional<Object> maybeUsername = outboundEndpointConfig.abacAttribute(PROPERTY_OUTBOUND_ID);
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

                    Optional<OutboundTarget> maybeTarget = outboundConfig.findTarget(outboundEnv);

                    return maybeTarget.flatMap(target -> {
                        JwtOutboundTarget jwtOutboundTarget;
                        try {
                            targetToJwtConfigLock.lock();
                            jwtOutboundTarget = targetToJwtConfig.computeIfAbsent(target, this::toOutboundTarget);
                        } finally {
                            targetToJwtConfigLock.unlock();
                        }

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
                        maybeSubject = providerRequest.securityContext().user();
                    } else {
                        maybeSubject = providerRequest.securityContext().service();
                    }

                    return maybeSubject.flatMap(subject -> {
                        Optional<OutboundTarget> maybeTarget = outboundConfig.findTarget(outboundEnv);

                        return maybeTarget.flatMap(target -> {
                            JwtOutboundTarget jwtOutboundTarget;
                            try {
                                targetToJwtConfigLock.lock();
                                jwtOutboundTarget = targetToJwtConfig.computeIfAbsent(target, this::toOutboundTarget);
                            } finally {
                                targetToJwtConfigLock.unlock();
                            }

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
                });
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

        // MP specific
        if (!principal.abacAttribute("upn").isPresent()) {
            builder.userPrincipal(principal.getName());
        }

        Security.getRoles(subject)
                .forEach(builder::addUserGroup);

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
                    .asNode()
                    .map(TokenHandler::create)
                    .orElse(defaultHandler);

            return new JwtOutboundTarget(
                    tokenHandler,
                    config.get("jwt-kid").asString().orElse(null),
                    config.get("jwk-kid").asString().orElse(null),
                    config.get("jwt-audience").asString().orElse(null),
                    config.get("jwt-not-before-seconds").asInt().orElse(5),
                    config.get("jwt-validity-seconds").asLong().orElse(60L * 60 * 24));
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
    }

    /**
     * Fluent API builder for {@link JwtAuthProvider}.
     */
    @Configured(description = "MP-JWT Auth configuration is defined by the spec (options prefixed with `mp.jwt.`), "
            + "and we add a few configuration options for the security provider (options prefixed with "
            + "`security.providers.mp-jwt-auth.`)")
    public static class Builder implements io.helidon.common.Builder<Builder, JwtAuthProvider> {
        private static final String HELIDON_CONFIG_PREFIX = "security.providers.mp-jwt-auth.";
        private static final String CONFIG_PUBLIC_KEY = "mp.jwt.verify.publickey";
        private static final String CONFIG_PUBLIC_KEY_PATH = "mp.jwt.verify.publickey.location";
        private static final String CONFIG_JWT_DECRYPT_KEY_LOCATION = "mp.jwt.decrypt.key.location";
        private static final String CONFIG_JWT_DECRYPT_KEY_ALGORITHM = "mp.jwt.decrypt.key.algorithm";
        private static final String JSON_START_MARK = "{";
        private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
                "-+BEGIN\\s+.*PUBLIC\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" // Header
                        + "([a-z0-9+/=\\r\\n\\s]+)"                       // Base64 text
                        + "-+END\\s+.*PUBLIC\\s+KEY[^-]*-+",              // Footer
                Pattern.CASE_INSENSITIVE);
        private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
                "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" // Header
                        + "([a-z0-9+/=\\r\\n\\s]+)"                        // Base64 text
                        + "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+",              // Footer
                Pattern.CASE_INSENSITIVE);

        private final Set<String> expectedAudiences = new HashSet<>();

        private String expectedIssuer;
        private boolean optional = false;
        private boolean authenticate = true;
        private boolean propagate = true;
        private boolean allowImpersonation = false;
        private SubjectType subjectType = SubjectType.USER;
        private TokenHandler atnTokenHandler = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPrefix("bearer ")
                .build();
        private OutboundConfig outboundConfig = OutboundConfig.builder().build();
        private LazyValue<JwkKeys> verifyKeys;
        private LazyValue<JwkKeys> decryptionKeys;
        private LazyValue<Jwk> defaultJwk;
        private LazyValue<Jwk> defaultDecryptionJwk;
        private JwkKeys signKeys;
        private String defaultKeyId;
        private String issuer;
        private String publicKeyPath;
        private String publicKey;
        private String cookieProperty = "Bearer";
        private String decryptKeyLocation;
        private String decryptionKeyAlgorithm;
        private boolean useCookie = false;
        private boolean loadOnStartup = false;
        private Duration expectedMaxTokenAge = null;
        private Duration clockSkew = Duration.ofSeconds(5);

        private Builder() {
        }

        @Override
        public JwtAuthProvider build() {
            if (verifyKeys == null) {
                if ((publicKeyPath != null) && (publicKey != null)) {
                    throw new DeploymentException("Both " + CONFIG_PUBLIC_KEY + " and " + CONFIG_PUBLIC_KEY_PATH + " are set! "
                                                          + "Only one of them should be picked.");
                }
                String publicKeyPath = this.publicKeyPath;
                String publicKey = this.publicKey;
                LazyValue<Jwk> defaultJwk = this.defaultJwk;
                verifyKeys = LazyValue.create(() -> createJwkKeys(publicKeyPath, publicKey, defaultJwk));
            }

            LazyValue<JwkKeys> verifyKeys = this.verifyKeys;

            if ((null == defaultJwk) && (null != defaultKeyId)) {
                String defaultKeyId = this.defaultKeyId;
                Supplier<Jwk> jwkSupplier = () -> verifyKeys.get().forKeyId(defaultKeyId)
                        .orElseThrow(() -> new DeploymentException("Default key id defined as \"" + defaultKeyId + "\" yet "
                                                                           + "the key id is not present in the JWK keys"));
                defaultJwk = LazyValue.create(jwkSupplier);
            }

            if (null == defaultJwk) {
                Supplier<Jwk> jwkSupplier = () -> {
                    List<Jwk> keys = verifyKeys.get().keys();
                    if (!keys.isEmpty()) {
                        return keys.get(0);
                    }
                    return null;
                };
                defaultJwk = LazyValue.create(jwkSupplier);
            }

            if (decryptKeyLocation != null) {
                String decryptKeyLocation = this.decryptKeyLocation;
                decryptionKeys = LazyValue.create(() -> createDecryptionJwkKeys(decryptKeyLocation));

                LazyValue<JwkKeys> decryptionKeys = this.decryptionKeys;
                defaultDecryptionJwk = LazyValue.create(() -> {
                    List<Jwk> keys = decryptionKeys.get().keys();
                    if (!keys.isEmpty() && keys.get(0).keyId() == null) {
                        return keys.get(0);
                    }
                    return null;
                });
            } else {
                decryptionKeys = LazyValue.create(() -> null);
                defaultDecryptionJwk = LazyValue.create(() -> null);
            }

            if (loadOnStartup) {
                defaultJwk.get();
                defaultDecryptionJwk.get();
            }
            return new JwtAuthProvider(this);
        }

        private JwkKeys createDecryptionJwkKeys(String decryptKeyLocation) {
            return Optional.of(decryptKeyLocation)
                    .map(this::loadDecryptionJwkKeysFromLocation)
                    .get();
        }

        private JwkKeys loadDecryptionJwkKeysFromLocation(String uri) {
            return locatePath(uri)
                    .map(path -> {
                        try {
                            return loadPrivateJwkKeys("file " + path, Files.readString(path, UTF_8));
                        } catch (IOException e) {
                            throw new SecurityException("Failed to load private key(s) from path: " + path.toAbsolutePath(), e);
                        }
                    })
                    .orElseGet(() -> {
                        try (InputStream is = locateStream(uri)) {
                            if (null == is) {
                                throw new SecurityException("Could not find private key resource for MP JWT-Auth at: " + uri);
                            }
                            return getPrivateKeyFromContent(uri, is);
                        } catch (IOException e) {
                            throw new SecurityException("Failed to load private key(s) from : " + uri, e);
                        }
                    });
        }

        private JwkKeys getPrivateKeyFromContent(String location, InputStream bufferedInputStream) throws IOException {
            return loadPrivateJwkKeys(location, new String(bufferedInputStream.readAllBytes(), UTF_8));
        }

        private JwkKeys loadPrivateJwkKeys(String location, String stringContent) {
            if (stringContent.isEmpty()) {
                throw new SecurityException("Cannot load public key from " + location + ", as its content is empty");
            }
            Matcher m = PRIVATE_KEY_PATTERN.matcher(stringContent);
            if (m.find()) {
                return loadPlainPrivateKey(stringContent);
            } else if (stringContent.startsWith(JSON_START_MARK)) {
                return loadPrivateKeyJWK(stringContent);
            } else {
                return loadPrivateKeyJWKBase64(stringContent);
            }
        }

        private JwkKeys loadPlainPrivateKey(String stringContent) {
            PrivateKey privateKey = Keys.builder()
                    .pem(pem -> pem.key(Resource.create("private key from PKCS8", stringContent)))
                    .build()
                    .privateKey()
                    .orElseThrow(() -> new DeploymentException(
                            "Failed to load private key from string content"));
            Jwk jwk;
            String algorithm = privateKey.getAlgorithm();
            if ("EC".equals(algorithm)) {
                jwk = JwkEC.builder()
                        .privateKey((ECPrivateKey) privateKey)
                        .build();
            } else {
                jwk = JwkRSA.builder()
                        .privateKey((RSAPrivateKey) privateKey)
                        .build();
            }
            return JwkKeys.builder()
                    .addKey(jwk)
                    .build();
        }

        private JwkKeys loadPrivateKeyJWKBase64(String base64Encoded) {
            return loadPrivateKeyJWK(new String(Base64.getUrlDecoder().decode(base64Encoded), UTF_8));
        }

        private JwkKeys loadPrivateKeyJWK(String jwkJson) {
            if (jwkJson.contains("keys")) {
                return JwkKeys.builder()
                        .resource(Resource.create("public key from PKCS8", jwkJson))
                        .build();
            }
            JsonObject jsonObject = JSON.createReader(new StringReader(jwkJson)).readObject();
            return JwkKeys.builder().addKey(Jwk.create(jsonObject)).build();
        }

        private JwkKeys createJwkKeys(String publicKeyPath, String publicKey, LazyValue<Jwk> defaultJwk) {
            if ((null == publicKeyPath) && (null == publicKey) && (null == defaultJwk)) {
                LOGGER.log(Level.ERROR, "Either \""
                                      + CONFIG_PUBLIC_KEY
                                      + "\", or \""
                                      + CONFIG_PUBLIC_KEY_PATH
                                      + "\" must be configured; \""
                                      + CONFIG_EXPECTED_ISSUER
                                      + "\" should be configured.");
            }
            return Optional.ofNullable(publicKeyPath)
                    .map(this::loadJwkKeysFromLocation)
                    .or(() -> Optional.ofNullable(publicKey)
                            .map(pk -> loadJwkKeys("configuration", pk)))
                    .or(() -> Optional.ofNullable(defaultJwk)
                            .map(jwk -> JwkKeys.builder()
                                    .addKey(jwk.get())
                                    .build()))
                    .orElseThrow(() -> new SecurityException("No public key or default JWK set for MP JWT-Auth Provider."));
        }

        private JwkKeys loadJwkKeysFromLocation(String uri) {
            return locatePath(uri)
                    .map(path -> {
                        try {
                            return loadJwkKeys("file " + path, Files.readString(path, UTF_8));
                        } catch (IOException e) {
                            throw new SecurityException("Failed to load public key(s) from path: " + path.toAbsolutePath(), e);
                        }
                    })
                    .orElseGet(() -> {
                        try (InputStream is = locateStream(uri)) {
                            if (null == is) {
                                throw new SecurityException("Could not find public key resource for MP JWT-Auth at: " + uri);
                            }
                            return getPublicKeyFromContent(uri, is);
                        } catch (IOException e) {
                            throw new SecurityException("Failed to load public key(s) from : " + uri, e);
                        }
                    });
        }

        private Optional<Path> locatePath(String uri) {
            try {
                Path path = Paths.get(uri);
                if (Files.exists(path)) {
                    return Optional.of(path);
                }
            } catch (InvalidPathException e) {
                LOGGER.log(Level.TRACE, "Could not locate path: " + uri, e);
            }

            return Optional.empty();
        }

        private InputStream locateStream(String uri) throws IOException {
            InputStream is;

            URL url = Thread.currentThread().getContextClassLoader().getResource(uri);
            if (url == null) {
                // if uri starts with "/", remove it
                if (uri.startsWith("/")) {
                    url = Thread.currentThread().getContextClassLoader().getResource(uri.substring(1));
                }
            }

            if (url == null) {
                is = JwtAuthProvider.class.getResourceAsStream(uri);

                if (null == is) {
                    try {
                        url = new URL(uri);
                    } catch (MalformedURLException ignored2) {
                        //ignored not and valid URL
                        LOGGER.log(Level.TRACE, () -> "Configuration of public key(s) is not a valid URL: " + uri);
                        return null;
                    }
                    is = url.openStream();
                }
            } else {
                is = url.openStream();
            }

            return is;
        }

        private JwkKeys getPublicKeyFromContent(String location, InputStream bufferedInputStream) throws IOException {
            return loadJwkKeys(location, new String(bufferedInputStream.readAllBytes(), UTF_8));
        }

        private JwkKeys loadJwkKeys(String location, String stringContent) {
            if (stringContent.isEmpty()) {
                throw new SecurityException("Cannot load public key from " + location + ", as its content is empty");
            }
            Matcher m = PUBLIC_KEY_PATTERN.matcher(stringContent);
            if (m.find()) {
                return loadPlainPublicKey(stringContent);
            } else if (stringContent.startsWith(JSON_START_MARK)) {
                return loadPublicKeyJWK(stringContent);
            } else {
                return loadPublicKeyJWKBase64(stringContent);
            }
        }

        private JwkKeys loadPlainPublicKey(String stringContent) {
            PublicKey publicKey = Keys.builder()
                    .pem(pem -> pem.publicKey(Resource.create("public key from PKCS8", stringContent)))
                    .build()
                    .publicKey()
                    .orElseThrow(() -> new DeploymentException(
                            "Failed to load public key from string content"));
            Jwk jwk;
            String algorithm = publicKey.getAlgorithm();
            if ("EC".equals(algorithm)) {
                jwk = JwkEC.builder()
                        .publicKey((ECPublicKey) publicKey)
                        .build();
            } else {
                jwk = JwkRSA.builder()
                        .publicKey((RSAPublicKey) publicKey)
                        .build();
            }
            return JwkKeys.builder()
                    .addKey(jwk)
                    .build();
        }

        private JwkKeys loadPublicKeyJWKBase64(String base64Encoded) {
            return loadPublicKeyJWK(new String(Base64.getUrlDecoder().decode(base64Encoded), UTF_8));
        }

        private JwkKeys loadPublicKeyJWK(String jwkJson) {
            if (jwkJson.contains("keys")) {
                return JwkKeys.builder()
                        .resource(Resource.create("public key from PKCS8", jwkJson))
                        .build();
            }
            JsonObject jsonObject = JSON.createReader(new StringReader(jwkJson)).readObject();
            return JwkKeys.builder().addKey(Jwk.create(jsonObject)).build();
        }

        /**
         * Whether to propagate identity.
         *
         * @param propagate whether to propagate identity (true) or not (false)
         * @return updated builder instance
         */
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "propagate", value = "true")
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
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "authenticate", value = "true")
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
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "allow-impersonation", value = "false")
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
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "principal-type", value = "USER")
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
         * Uses {@code Authorization} header with {@code bearer } prefix by default.
         *
         * @param tokenHandler token handler instance
         * @return updated builder instance
         */
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "atn-token.handler")
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
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "optional", value = "false")
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
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "sign-token")
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
            this.verifyKeys = LazyValue.create(JwkKeys.builder().resource(verifyJwkResource).build());
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
         * String representation of the public key.
         *
         * @param publicKey String representation
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_PUBLIC_KEY)
        public Builder publicKey(String publicKey) {
            // from MP specification - if defined, get rid of publicKeyPath from Helidon Config,
            // as we must fail if both are defined using MP configuration options
            this.publicKey = publicKey;
            this.publicKeyPath = null;
            return this;
        }

        /**
         * Path to public key.
         * The value may be a relative path or a URL.
         *
         * @param publicKeyPath Public key path
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_PUBLIC_KEY_PATH)
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "atn-token.verify-key")
        public Builder publicKeyPath(String publicKeyPath) {
            this.publicKeyPath = publicKeyPath;
            return this;
        }

        /**
         * Default JWK which should be used.
         *
         * @param defaultJwk Default JWK
         * @return updated builder instance
         */
        public Builder defaultJwk(Jwk defaultJwk) {
            this.defaultJwk = LazyValue.create(defaultJwk);
            return this;
        }

        /**
         * Default JWT key ID which should be used.
         *
         * @param defaultKeyId Default JWT key ID
         * @return updated builder instance
         */
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "atn-token.default-key-id")
        public Builder defaultKeyId(String defaultKeyId) {
            this.defaultKeyId = defaultKeyId;
            return this;
        }

        /**
         * Load this builder from a configuration.
         *
         * @param config configuration to load from
         * @return updated builder instance
         */
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "atn-token.jwk.resource",
                          type = Resource.class,
                          description = "JWK resource for authenticating the request")
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("authenticate").asBoolean().ifPresent(this::authenticate);
            config.get("propagate").asBoolean().ifPresent(this::propagate);
            config.get("allow-impersonation").asBoolean().ifPresent(this::allowImpersonation);
            config.get("principal-type").asString().as(SubjectType::valueOf).ifPresent(this::subjectType);
            config.get("atn-token.handler").map(TokenHandler::create).ifPresent(this::atnTokenHandler);
            config.get("atn-token").asNode().ifPresent(this::verifyKeys);
            config.get("atn-token.jwt-audience").asString().ifPresent(this::expectedAudience);
            config.get("atn-token.default-key-id").asString().ifPresent(this::defaultKeyId);
            config.get("atn-token.verify-key").asString().ifPresent(this::publicKeyPath);
            config.get("sign-token").asNode().ifPresent(outbound -> outboundConfig(OutboundConfig.create(outbound)));
            config.get("sign-token").asNode().ifPresent(this::outbound);
            config.get("load-on-startup").asBoolean().ifPresent(this::loadOnStartup);

            org.eclipse.microprofile.config.Config mpConfig = ConfigProviderResolver.instance().getConfig();

            mpConfig.getOptionalValue(CONFIG_PUBLIC_KEY, String.class).ifPresent(this::publicKey);
            mpConfig.getOptionalValue(CONFIG_PUBLIC_KEY_PATH, String.class).ifPresent(this::publicKeyPath);
            mpConfig.getOptionalValue(CONFIG_EXPECTED_ISSUER, String.class).ifPresent(this::expectedIssuer);
            mpConfig.getOptionalValue(CONFIG_EXPECTED_AUDIENCES, String[].class).map(List::of).ifPresent(this::expectedAudiences);
            mpConfig.getOptionalValue(CONFIG_EXPECTED_MAX_TOKEN_AGE, int.class).ifPresent(this::expectedMaxTokenAge);
            mpConfig.getOptionalValue(CONFIG_COOKIE_PROPERTY_NAME, String.class).ifPresent(this::cookieProperty);
            mpConfig.getOptionalValue(CONFIG_JWT_HEADER, String.class).ifPresent(this::jwtHeader);
            mpConfig.getOptionalValue(CONFIG_JWT_DECRYPT_KEY_LOCATION, String.class).ifPresent(this::decryptKeyLocation);
            mpConfig.getOptionalValue(CONFIG_JWT_DECRYPT_KEY_ALGORITHM, String.class).ifPresent(this::decryptKeyAlgorithm);
            mpConfig.getOptionalValue(CONFIG_CLOCK_SKEW, int.class).ifPresent(this::clockSkew);

            if (null == publicKey && null == publicKeyPath) {
                // this is a fix for incomplete TCK tests
                // we will configure this location in our tck configuration
                String key = "helidon.mp.jwt.verify.publickey.location";
                mpConfig.getOptionalValue(key, String.class).ifPresent(it -> {
                    publicKeyPath(it);
                    LOGGER.log(Level.WARNING, "You have configured public key for JWT-Auth provider using a property"
                                           + " reserved for TCK tests (" + key + "). Please use "
                                           + CONFIG_PUBLIC_KEY_PATH + " instead.");
                });
            }

            return this;
        }

        /**
         * Name of the header expected to contain the token.
         *
         * @param header header name which should be used
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_JWT_HEADER, value = "Authorization")
        public Builder jwtHeader(String header) {
            if (HeaderNames.COOKIE.defaultCase().equalsIgnoreCase(header)) {
                useCookie = true;
            } else {
                useCookie = false;
                atnTokenHandler = TokenHandler.builder()
                        .tokenHeader(header)
                        .tokenPrefix("bearer ")
                        .build();
            }
            return this;
        }

        /**
         * Specific cookie property name where we should search for JWT property.
         *
         * @param cookieProperty cookie property name
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_COOKIE_PROPERTY_NAME, value = "Bearer")
        public Builder cookieProperty(String cookieProperty) {
            this.cookieProperty = cookieProperty;
            return this;
        }

        /**
         * Expected issuer in incoming requests.
         *
         * @param issuer name of issuer
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_EXPECTED_ISSUER)
        public Builder expectedIssuer(String issuer) {
            this.expectedIssuer = issuer;
            return this;
        }

        /**
         * Audience expected in inbound JWTs.
         *
         * @param audience audience string
         * @return updated builder instance
         * @deprecated use {@link #addExpectedAudience(String)} instead
         */
        @Deprecated(forRemoval = true, since = "2.4.0")
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "atn-token.jwt-audience")
        public Builder expectedAudience(String audience) {
            return addExpectedAudience(audience);
        }

        /**
         * Add an audience expected in inbound JWTs.
         *
         * @param audience audience string
         * @return updated builder instance
         */
        public Builder addExpectedAudience(String audience) {
            this.expectedAudiences.add(audience);
            return this;
        }

        /**
         * Expected audiences of incoming tokens.
         *
         * @param audiences expected audiences to use
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_EXPECTED_AUDIENCES,
                          type = String.class,
                          kind = ConfiguredOption.Kind.LIST)
        public Builder expectedAudiences(Collection<String> audiences) {
            this.expectedAudiences.clear();
            this.expectedAudiences.addAll(audiences);
            return this;
        }

        /**
         * Maximal expected token age in seconds. If this value is set, {@code iat} claim needs to be present in the JWT.
         *
         * @param expectedMaxTokenAge expected maximal token age in seconds
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_EXPECTED_MAX_TOKEN_AGE)
        public Builder expectedMaxTokenAge(int expectedMaxTokenAge) {
            this.expectedMaxTokenAge = Duration.ofSeconds(expectedMaxTokenAge);
            return this;
        }

        /**
         * Private key for decryption of encrypted claims.
         * The value may be a relative path or a URL.
         *
         * @param decryptKeyLocation private key location
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_JWT_DECRYPT_KEY_LOCATION)
        public Builder decryptKeyLocation(String decryptKeyLocation) {
            this.decryptKeyLocation = decryptKeyLocation;
            return this;
        }

        /**
         * Expected key management algorithm supported by the MP JWT endpoint.
         * Supported algorithms are either {@code RSA-OAEP} or {@code RSA-OAEP-256}.
         * If no algorithm is set, both algorithms must be accepted.
         *
         * @param decryptionKeyAlgorithm expected decryption key algorithm
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_JWT_DECRYPT_KEY_ALGORITHM,
                          allowedValues = {@ConfiguredValue(value = "RSA-OAEP", description = "RSA-OAEP Algorithm"),
                                  @ConfiguredValue(value = "RSA-OAEP-256", description = "RSA-OAEP-256 Algorithm")})
        public Builder decryptKeyAlgorithm(String decryptionKeyAlgorithm) {
            this.decryptionKeyAlgorithm = decryptionKeyAlgorithm;
            return this;
        }

        /**
         * Whether to load JWK verification keys on server startup
         * Default value is {@code false}.
         *
         * @param loadOnStartup load verification keys on server startup
         * @return updated builder instance
         */
        @ConfiguredOption(key = HELIDON_CONFIG_PREFIX + "load-on-startup", value = "false")
        public Builder loadOnStartup(boolean loadOnStartup) {
            this.loadOnStartup = loadOnStartup;
            return this;
        }

        /**
         * Clock skew to be accounted for in token expiration and max age validations in seconds.
         *
         * @param clockSkew clock skew
         * @return updated builder instance
         */
        @ConfiguredOption(key = CONFIG_CLOCK_SKEW, value = "5")
        public Builder clockSkew(int clockSkew) {
            this.clockSkew = Duration.ofSeconds(clockSkew);
            return this;
        }

        private void verifyKeys(Config config) {
            config.get("jwk.resource").map(Resource::create).ifPresent(this::verifyJwk);
        }

        private void outbound(Config config) {
            config.get("jwt-issuer").asString().ifPresent(this::issuer);

            // jwk is optional, we may be propagating existing token
            config.get("jwk.resource").map(Resource::create).ifPresent(this::signJwk);
        }
    }

}
