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
package io.helidon.microprofile.jwt.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.inject.spi.DeploymentException;
import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.Errors;
import io.helidon.common.InputStreamHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
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
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;
import io.helidon.security.providers.OutboundConfig;
import io.helidon.security.providers.OutboundTarget;
import io.helidon.security.providers.TokenCredential;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.security.util.TokenHandler;

import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.jwt.JsonWebToken;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provider that provides JWT authentication.
 */
public class JwtAuthProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private static final Logger LOGGER = Logger.getLogger(JwtAuthProvider.class.getName());

    /**
     * Configure this for outbound requests to override user to use.
     */
    public static final String EP_PROPERTY_OUTBOUND_USER = "io.helidon.security.outbound.user";
    /**
     * Configuration key for expected issuer of incoming tokens. Used for validation of JWT.
     */
    public static final String CONFIG_EXPECTED_ISSUER = "mp.jwt.verify.issuer";

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
    private final Jwk defaultJwk;
    private final Map<OutboundTarget, JwtOutboundTarget> targetToJwtConfig = new IdentityHashMap<>();
    private final String expectedIssuer;

    private JwtAuthProvider(Builder builder) {
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
        this.defaultJwk = builder.defaultJwk;
        this.expectedIssuer = builder.expectedIssuer;

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
        return CollectionsHelper.setOf(LoginConfig.class);
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        if (!authenticate) {
            return AuthenticationResponse.abstain();
        }

        List<LoginConfig> loginConfigs = providerRequest.getEndpointConfig()
                .combineAnnotations(LoginConfig.class, EndpointConfig.AnnotationScope.APPLICATION);

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
        return atnTokenHandler.extractToken(providerRequest.getEnv().getHeaders())
                .map(token -> {
                    SignedJwt signedJwt = SignedJwt.parseToken(token);
                    Errors errors = signedJwt.verifySignature(verifyKeys, defaultJwk);
                    if (errors.isValid()) {
                        Jwt jwt = signedJwt.getJwt();
                        // verify the audience is correct
                        Errors validate = jwt.validate(expectedIssuer, expectedAudience);
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
        JsonWebTokenImpl principal = buildPrincipal(jwt, signedJwt);

        TokenCredential.Builder builder = TokenCredential.builder();
        jwt.getIssueTime().ifPresent(builder::issueTime);
        jwt.getExpirationTime().ifPresent(builder::expTime);
        jwt.getIssuer().ifPresent(builder::issuer);
        builder.token(signedJwt.getTokenContent());
        builder.addToken(JsonWebToken.class, principal);
        builder.addToken(Jwt.class, jwt);
        builder.addToken(SignedJwt.class, signedJwt);



        Subject.Builder subjectBuilder = Subject.builder()
                .principal(principal)
                .addPublicCredential(TokenCredential.class, builder.build());

        Optional<List<String>> userGroups = jwt.getUserGroups();
        userGroups.ifPresent(groups -> groups.forEach(group -> subjectBuilder.addGrant(Role.create(group))));

        Optional<List<String>> scopes = jwt.getScopes();
        scopes.ifPresent(scopeList ->
                                 scopeList.forEach(scope -> subjectBuilder.addGrant(Grant.builder()
                                                                                            .name(scope)
                                                                                            .type("scope")
                                                                                            .build())));

        return subjectBuilder.build();
    }

    JsonWebTokenImpl buildPrincipal(Jwt jwt, SignedJwt signedJwt) {
        return JsonWebTokenImpl.create(signedJwt);
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        return propagate;
    }

    @Override
    public OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
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

        // MP specific
        if (!principal.getAttribute("upn").isPresent()) {
            builder.userPrincipal(principal.getName());
        }

        Security.getRoles(subject)
                .forEach(builder::addUserGroup);

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
                    .asNode()
                    .map(TokenHandler::fromConfig)
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
                    .audience(jwtAudience);
        }
    }

    /**
     * Fluent API builder for {@link JwtAuthProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<JwtAuthProvider> {
        private static final String CONFIG_PUBLIC_KEY = "mp.jwt.verify.publickey";
        private static final String CONFIG_PUBLIC_KEY_PATH = "mp.jwt.verify.publickey.location";
        private static final String JSON_START_MARK = "{";
        private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
                "-+BEGIN\\s+.*PUBLIC\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                        "([a-z0-9+/=\\r\\n\\s]+)" +                       // Base64 text
                        "-+END\\s+.*PUBLIC\\s+KEY[^-]*-+",            // Footer
                Pattern.CASE_INSENSITIVE);

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
        private OutboundConfig outboundConfig;
        private JwkKeys verifyKeys;
        private JwkKeys signKeys;
        private Jwk defaultJwk;
        private String defaultKeyId;
        private String issuer;
        private String expectedAudience;
        private String publicKeyPath;
        private String publicKey;

        private Builder() {
        }

        @Override
        public JwtAuthProvider build() {
            if (verifyKeys == null) {
                if ((publicKeyPath != null) && (publicKey != null)) {
                    throw new DeploymentException("Both " + CONFIG_PUBLIC_KEY + " and " + CONFIG_PUBLIC_KEY_PATH + " are set! "
                                                          + "Only one of them should be picked.");
                }
                verifyKeys = createJwkKeys();
            }

            if ((null == defaultJwk) && (null != defaultKeyId)) {
                defaultJwk = verifyKeys.forKeyId(defaultKeyId)
                        .orElseThrow(() -> new DeploymentException("Default key id defined as \"" + defaultKeyId + "\" yet the "
                                                                           + "key id is not present in the JWK keys"));
            }

            if (null == defaultJwk) {
                List<Jwk> keys = verifyKeys.keys();
                if (!keys.isEmpty()) {
                    defaultJwk = keys.get(0);
                }
            }

            return new JwtAuthProvider(this);
        }

        private JwkKeys createJwkKeys() {
            return OptionalHelper
                    .from(Optional.ofNullable(publicKeyPath)
                            .map(this::loadJwkKeysFromLocation))
                    .or(() -> Optional.ofNullable(publicKey)
                            .map(this::loadJwkKeys))
                    .or(() -> Optional.ofNullable(defaultJwk)
                            .map(jwk -> JwkKeys.builder()
                                    .addKey(jwk)
                                    .build()))
                    .asOptional()
                    .orElseThrow(() -> new SecurityException("No public key or default JWK set."));
        }

        private JwkKeys loadJwkKeysFromLocation(String uri) {
            Path path = Paths.get(uri);
            if (Files.exists(path)) {
                try {
                    return loadJwkKeys(new String(Files.readAllBytes(path), UTF_8));
                } catch (IOException e) {
                    throw new SecurityException("Failed to load public key(s) from path: " + path.toAbsolutePath(), e);
                }
            } else {

                try (InputStream is = locateStream(uri)) {
                    return getPublicKeyFromContent(is);
                } catch (IOException e) {
                    throw new SecurityException("Failed to load public key(s) from : " + uri, e);
                }
            }
        }

        private InputStream locateStream(String uri) throws IOException {
            InputStream is;

            URL url = Thread.currentThread().getContextClassLoader().getResource(uri);
            if (url == null) {
                is = JwtAuthProvider.class.getResourceAsStream(uri);

                if (null == is) {
                    try {
                        url = new URL(uri);
                    } catch (MalformedURLException ignored2) {
                        //ignored not and valid URL
                        LOGGER.finest(() -> "Configuration of public key(s) is not a valid URL: " + uri);
                        return null;
                    }
                    is = url.openStream();
                }
            } else {
                is = url.openStream();
            }

            return is;
        }

        private JwkKeys getPublicKeyFromContent(InputStream bufferedInputStream) throws IOException {
            return loadJwkKeys(new String(InputStreamHelper.readAllBytes(bufferedInputStream), UTF_8));
        }

        private JwkKeys loadJwkKeys(String stringContent) {
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
            return JwkKeys.builder()
                    .addKey(JwkRSA.builder()
                                    .publicKey((RSAPublicKey) KeyConfig.pemBuilder()
                                            .publicKey(Resource.create("public key from PKCS8", stringContent))
                                            .build()
                                            .publicKey()
                                            .orElseThrow(() -> new DeploymentException(
                                                    "Failed to load public key from string content")))
                                    .build())
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
            JsonObject jsonObject = Json.createReader(new StringReader(jwkJson)).readObject();
            return JwkKeys.builder().addKey(Jwk.fromJson(jsonObject)).build();
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
         * String representation of the public key.
         *
         * @param publicKey String representation
         * @return updated builder instance
         */
        public Builder publicKey(String publicKey) {
            // from MP specification - if defined, get rid of publicKeyPath from Helidon Config,
            // as we must fail if both are defined using MP configuration options
            this.publicKey = publicKey;
            this.publicKeyPath = null;
            return this;
        }

        /**
         * Path to public key.
         *
         * @param publicKeyPath Public key path
         * @return updated builder instance
         */
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
            this.defaultJwk = defaultJwk;
            return this;
        }

        /**
         * Default JWT key ID which should be used.
         *
         * @param defaultKeyId Default JWT key ID
         * @return updated builder instance
         */
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
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("authenticate").asBoolean().ifPresent(this::authenticate);
            config.get("propagate").asBoolean().ifPresent(this::propagate);
            config.get("allow-impersonation").asBoolean().ifPresent(this::allowImpersonation);
            config.get("principal-type").asString().as(SubjectType::valueOf).ifPresent(this::subjectType);
            config.get("atn-token.handler").as(TokenHandler::fromConfig).ifPresent(this::atnTokenHandler);
            config.get("atn-token").ifExists(this::verifyKeys);
            config.get("atn-token.jwt-audience").asString().ifPresent(this::expectedAudience);
            config.get("atn-token.default-key-id").asString().ifPresent(this::defaultKeyId);
            config.get("atn-token.verify-key").asString().ifPresent(this::publicKeyPath);
            config.get("sign-token").ifExists(outbound -> outboundConfig(OutboundConfig.parseTargets(outbound)));
            config.get("sign-token").ifExists(this::outbound);

            org.eclipse.microprofile.config.Config mpConfig = ConfigProviderResolver.instance().getConfig();

            mpConfig.getOptionalValue(CONFIG_PUBLIC_KEY, String.class).ifPresent(this::publicKey);
            mpConfig.getOptionalValue(CONFIG_PUBLIC_KEY_PATH, String.class).ifPresent(this::publicKeyPath);
            mpConfig.getOptionalValue(CONFIG_EXPECTED_ISSUER, String.class).ifPresent(this::expectedIssuer);

            return this;
        }

        /**
         * Expected issuer in incoming requests.
         *
         * @param issuer name of issuer
         * @return updated builder instance
         */
        public Builder expectedIssuer(String issuer) {
            this.expectedIssuer = issuer;
            return this;
        }

        /**
         * Audience expected in inbound JWTs.
         *
         * @param audience audience string
         * @return updated builder instance
         */
        public Builder expectedAudience(String audience) {
            this.expectedAudience = audience;
            return this;
        }

        private void verifyKeys(Config config) {
            Resource.create(config, "jwk")
                    .map(this::verifyJwk);
        }

        private void outbound(Config config) {
            // jwk is optional, we may be propagating existing token
            Resource.create(config, "jwk").ifPresent(this::signJwk);
            config.get("jwt-issuer").asString().ifPresent(this::issuer);
        }
    }

}
