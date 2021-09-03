/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.providers.google.login;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.providers.common.EvictableCache;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.security.util.TokenHandler;

import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Provider supporting login button from front-end.
 * This expects the token to be sent in a header.
 * By default, Authorization header with bearer is expected, e.g.:
 * {@code Authorization: bearer abcdefg_google_id_token_from_login_button_callback}.
 *
 * Configure login button as described here:
 * https://developers.google.com/identity/sign-in/web/sign-in
 *
 * See google-login example.
 */
public final class GoogleTokenProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private static final Logger LOGGER = Logger.getLogger(GoogleTokenProvider.class.getName());
    private static final String HEADER_AUTHENTICATION_REQUIRED = "WWW-Authenticate";

    static final long TIME_SKEW_SECONDS = TimeUnit.SECONDS.convert(5, TimeUnit.MINUTES);

    private final EvictableCache<String, CachedRecord> subjectCache = EvictableCache.<String, CachedRecord>builder()
            .evictor((key, record) -> record.getValidSupplier().get())
            .build();

    private final boolean optional;
    private final String realm;
    private final TokenHandler tokenHandler;
    private final GoogleIdTokenVerifier verifier;
    private final JsonFactory jsonFactory;
    private final BiFunction<JsonFactory, String, GoogleIdToken> tokenParser;
    private final OutboundConfig outboundConfig;

    private GoogleTokenProvider(Builder builder) {
        String clientId = builder.clientId;
        this.optional = builder.optional;
        this.realm = builder.realm;
        this.tokenHandler = builder.tokenHandler;
        this.outboundConfig = (builder.outboundConfig == null) ? OutboundConfig.builder().build() : builder.outboundConfig;
        this.jsonFactory = JacksonFactory.getDefaultInstance();

        if (null == builder.verifier) {
            // not covered by unit tests, as this creates a component connecting to internet
            try {
                NetHttpTransport.Builder transportBuilder = new NetHttpTransport.Builder()
                        .trustCertificates(GoogleUtils.getCertificateTrustStore());
                if (null != builder.proxyHost) {
                    transportBuilder.setProxy(new Proxy(Proxy.Type.HTTP,
                                                        new InetSocketAddress(builder.proxyHost, builder.proxyPort)));
                }
                NetHttpTransport transport = transportBuilder.build();

                // thread safe according to documentation
                this.verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                        .setAudience(Set.of(clientId))
                        .build();
            } catch (Exception e) {
                throw new GoogleTokenException("Failed to initialize transport", e);
            }
        } else {
            // support mocking
            this.verifier = builder.verifier;
        }

        if (null == builder.tokenParser) {
            this.tokenParser = (jsonFactory, token) -> {
                try {
                    return GoogleIdToken.parse(jsonFactory, token);
                } catch (IOException e) {
                    throw new SecurityException("Failed to parse Google token", e);
                }
            };
        } else {
            this.tokenParser = builder.tokenParser;
        }
    }

    /**
     * Create an instance from configuration.
     * Used by Security when configuring this provider from configuration by class name.
     *
     * @param config Configuration located on the provider's key
     * @return Instance configured from the configuration instance
     */
    public static GoogleTokenProvider create(Config config) {
        // not covered by unit tests, as this creates a component connecting to internet
        return builder().config(config).build();
    }

    /**
     * Fluent API builder to build {@link GoogleTokenProvider} instance.
     *
     * @return Builder with just default values
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        Optional<String> maybeToken;
        try {
            maybeToken = tokenHandler.extractToken(providerRequest.env().headers());
        } catch (Exception e) {
            return failInvalidRequest(e);
        }

        SecurityContext sContext = providerRequest.securityContext();
        return maybeToken
                .map(token -> cachedResponse(token, sContext.tracer(), sContext.tracingSpan()))
                .orElseGet(this::failNoToken);

    }

    private AuthenticationResponse cachedResponse(String token, Tracer tracer, SpanContext tracingSpan) {
        try {
            GoogleIdToken gToken = tokenParser.apply(jsonFactory, token);
            GoogleIdToken.Payload payload = gToken.getPayload();

            // validate timeout
            if (verifyLocal(payload)) {
                return subjectCache.computeValue(token,
                                                 () -> verifyGoogle(token, gToken, tracer, tracingSpan))
                        .map(CachedRecord::getSubject)
                        .map(AuthenticationResponse::success)
                        .orElseGet(() -> fail(null));
            } else {
                subjectCache.remove(token);
                return fail(null);
            }
        } catch (SecurityException e) {
            if (e.getCause() instanceof IOException) {
                return failInvalidRequest((IOException) e.getCause());
            }
            return fail(e.getCause());
        } catch (Exception e) {
            return fail(e);
        }
    }

    private boolean verifyLocal(GoogleIdToken.Payload payload) {
        Long issueTime = payload.getIssuedAtTimeSeconds();
        Long expiryTime = payload.getExpirationTimeSeconds();

        long currentTime = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        if (null != issueTime) {
            // lowest allowed time
            long checkTime = currentTime + TIME_SKEW_SECONDS;
            if (issueTime > checkTime) {
                LOGGER.log(Level.FINEST,
                           () -> "Token pre-validation failed, issue time too late: " + issueTime + " for " + payload);
                return false;
            }
        }

        if (null != expiryTime) {
            long checkTime = currentTime - TIME_SKEW_SECONDS;
            if (expiryTime < checkTime) {
                LOGGER.log(Level.FINEST,
                           () -> "Token pre-validation failed, expiration time too early: " + expiryTime + " for " + payload);
                return false;
            }
        }

        return true;
    }

    private Optional<CachedRecord> verifyGoogle(String accessToken,
                                                GoogleIdToken token,
                                                Tracer tracer,
                                                SpanContext tracingSpan) throws SecurityException {
        Span span = tracer.buildSpan("googleTokenVerification")
                .asChildOf(tracingSpan)
                .start();

        try {

            if (verifier.verify(token)) {
                return Optional.of(new CachedRecord(buildSubject(accessToken, token.getPayload()),
                                                    () -> !verifyLocal(token.getPayload())));
            } else {
                return Optional.empty();
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new SecurityException("Failed to verify Google token", e);
        } finally {
            span.finish();
        }
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {

        boolean canPropagate = providerRequest.securityContext()
                .user()
                .flatMap(subject -> subject.publicCredential(TokenCredential.class))
                .flatMap(token -> token.getIssuer()
                        .map(issuer -> issuer.endsWith(".google.com")))
                .orElse(false);

        if (!canPropagate) {
            return canPropagate;
        }

        // only propagate if an actual outbound target exists (no custom config per target)
        return this.outboundConfig.findTarget(outboundEnv).isPresent();
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {
        return providerRequest.securityContext()
                .user()
                .flatMap(subject -> subject.publicCredential(TokenCredential.class))
                .map(token -> outboundSecurity(providerRequest, outboundEnv, outboundEndpointConfig, token))
                .orElse(OutboundSecurityResponse.abstain());
    }

    private OutboundSecurityResponse outboundSecurity(ProviderRequest providerRequest,
                                                      SecurityEnvironment outboundEnv,
                                                      EndpointConfig outboundEndpointConfig,
                                                      TokenCredential token) {

        if (!token.getIssuer().map(issuer -> issuer.endsWith(".google.com")).orElse(false)) {
            // not our token :(
            return OutboundSecurityResponse.abstain();
        }

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(outboundEnv.headers());

        tokenHandler.header(headers, token.token());

        return OutboundSecurityResponse.withHeaders(headers);
    }

    private AuthenticationResponse failInvalidRequest(Exception e) {
        if (optional) {
            LOGGER.log(Level.FINE, "Failed to authenticate Google token", e);
            return AuthenticationResponse.abstain();
        }
        return AuthenticationResponse.builder()
                .statusCode(400)
                .responseHeader(HEADER_AUTHENTICATION_REQUIRED, buildInvalidRequestChallenge(e))
                .status(AuthenticationResponse.SecurityStatus.FAILURE)
                .description("Invalid authorization header")
                .throwable(e)
                .build();
    }

    private AuthenticationResponse failNoToken() {
        if (optional) {
            LOGGER.log(Level.FINE, "Failed to authenticate Google token, token not present");
            return AuthenticationResponse.abstain();
        }

        return AuthenticationResponse.builder()
                .statusCode(401)
                .responseHeader(HEADER_AUTHENTICATION_REQUIRED, buildChallenge(null))
                .status(AuthenticationResponse.SecurityStatus.FAILURE)
                .description("Missing authorization header")
                .build();
    }

    private AuthenticationResponse fail(Throwable throwable) {
        if (optional) {
            LOGGER.log(Level.FINE, "Failed to authenticate Google token", throwable);
            return AuthenticationResponse.abstain();
        }

        String description = ((null == throwable) ? null : throwable.getMessage());

        if (null == description) {
            description = ((null == throwable) ? "verification failed" : throwable.getClass().getName());
        }
        return AuthenticationResponse.builder()
                .statusCode(401)
                .responseHeader(HEADER_AUTHENTICATION_REQUIRED,
                                buildChallenge(description))
                .status(AuthenticationResponse.SecurityStatus.FAILURE)
                .description(description)
                .throwable(throwable)
                .build();
    }

    private String buildInvalidRequestChallenge(Exception e) {

        // TODO possible enhancement: configure scopes, or even get them from annotations???
        return "Bearer"
                + " realm=\"" + realm + "\""
                + ",error=\"invalid_request\""
                + ",error_description=\"" + e.getMessage() + "\""
                + ",scope=\"openid profile email\"";
    }

    private String buildChallenge(String description) {
        StringBuilder challenge = new StringBuilder();
        challenge.append("Bearer");
        challenge.append(" realm=\"").append(realm).append("\"");
        if (null != description) {
            challenge.append(",error=\"invalid_token\"");
            challenge.append(",error_description=\"").append(description).append("\"");
        }
        // TODO possible enhancement: configure scopes, or even get them from annotations???
        challenge.append(",scope=\"openid profile email\"");

        return challenge.toString();
    }

    private Subject buildSubject(String accessToken, GoogleIdToken.Payload payload) {
        TokenCredential.Builder builder = TokenCredential.builder();
        builder.issueTime(toInstant(payload.getIssuedAtTimeSeconds()));
        builder.expTime(toInstant(payload.getExpirationTimeSeconds()));
        builder.issuer(payload.getIssuer());
        builder.token(accessToken);
        builder.addToken(GoogleIdToken.Payload.class, payload);

        String email = payload.getEmail();
        String userId = payload.getSubject();

        Principal principal = Principal.builder()
                .id(userId)
                .name((null == email) ? userId : email)
                .addAttribute("fullName", payload.get("name"))
                .addAttribute("emailVerified", payload.getEmailVerified())
                .addAttribute("locale", payload.get("locale"))
                .addAttribute("familyName", payload.get("family_name"))
                .addAttribute("givenName", payload.get("given_name"))
                .addAttribute("pictureUrl", payload.get("picture"))
                .build();

        return Subject.builder()
                .principal(principal)
                .addPublicCredential(TokenCredential.class, builder.build())
                .build();
    }

    private Instant toInstant(Long epochSeconds) {
        if (null == epochSeconds) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds);
    }

    /**
     * Fluent API builder to build {@link GoogleTokenProvider} instance.
     */
    public static final class Builder implements io.helidon.common.Builder<GoogleTokenProvider> {
        private String clientId;

        private String proxyHost;
        private int proxyPort = 80;
        private TokenHandler tokenHandler = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPrefix("bearer ")
                .build();
        private String realm = "helidon";
        private GoogleIdTokenVerifier verifier;
        private BiFunction<JsonFactory, String, GoogleIdToken> tokenParser;
        private boolean optional;
        private OutboundConfig outboundConfig;

        private Builder() {
        }

        @Override
        public GoogleTokenProvider build() {
            Objects.requireNonNull(clientId,
                                   "client-id must be configured (or call Builder.clientId) with Google "
                                           + "application client id string, usually ending with .apps.googleusercontent.com");
            return new GoogleTokenProvider(this);
        }

        Builder verifier(GoogleIdTokenVerifier verifier) {
            this.verifier = verifier;
            return this;
        }

        Builder tokenParser(BiFunction<JsonFactory, String, GoogleIdToken> tokenParser) {
            this.tokenParser = tokenParser;
            return this;
        }

        /**
         * Google application client id, to validate that the token was generated by Google for us.
         *
         * @param clientId client id as obtained from Google developer console
         * @return updated builder instance
         */
        public Builder clientId(String clientId) {
            Objects.requireNonNull(clientId);

            this.clientId = clientId;
            return this;
        }

        /**
         * If set to true, this provider will return {@link io.helidon.security.SecurityResponse.SecurityStatus#ABSTAIN} instead
         * of failing in case of invalid request.
         *
         * @param optional whether to be optional or not
         * @return updated builder instance
         */
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }


        /**
         * Token provider to extract Google access token from request, defaults to "Authorization" header with a "bearer " prefix.
         *
         * @param provider token provider
         * @return updated builder instance
         */
        public Builder tokenProvider(TokenHandler provider) {
            this.tokenHandler = provider;
            return this;
        }

        /**
         * Set the authentication realm to build challenge, defaults to "helidon".
         *
         * @param realm realm of authentication
         * @return updated builder instance
         */
        public Builder realm(String realm) {
            this.realm = realm;
            return this;
        }

        /**
         * Set proxy host when talking to Google.
         *
         * @param host host of http proxy server
         * @return updated builder instance
         */
        public Builder proxyHost(String host) {
            if ((null == host) || host.isEmpty()) {
                this.proxyHost = null;
                return this;
            }
            this.proxyHost = host;
            return this;
        }

        /**
         * Set proxy port when talking to Google.
         *
         * @param port port of http proxy server, defaults to 80
         * @return updated builder instance
         */
        public Builder proxyPort(int port) {
            this.proxyPort = port;
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config Configuration at provider (security.provider.x) key
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("client-id").asString().ifPresent(this::clientId);
            config.get("proxy-host").asString().ifPresent(this::proxyHost);
            config.get("proxy-port").asInt().ifPresent(this::proxyPort);
            config.get("realm").asString().ifPresent(this::realm);
            config.get("token").as(TokenHandler::create).ifPresent(this::tokenProvider);
            // OutboundConfig.create() expects provider configuration, not outbound
            config.get("outbound").ifExists(outbound -> outboundConfig(OutboundConfig.create(config)));

            return this;
        }

        /**
         * Outbound configuration - a set of outbound targets that
         * will have the token propagated.
         *
         * @param outboundConfig configuration of outbound
         * @return updated builder instance
         */
        public Builder outboundConfig(OutboundConfig outboundConfig) {
            this.outboundConfig = outboundConfig;
            return this;
        }
    }

    private static final class CachedRecord {
        private final Subject subject;
        private final Supplier<Boolean> validSupplier;

        private CachedRecord(Subject subject, Supplier<Boolean> validSupplier) {
            this.subject = subject;
            this.validSupplier = validSupplier;
        }

        public Subject getSubject() {
            return subject;
        }

        public Supplier<Boolean> getValidSupplier() {
            return validSupplier;
        }
    }
}
