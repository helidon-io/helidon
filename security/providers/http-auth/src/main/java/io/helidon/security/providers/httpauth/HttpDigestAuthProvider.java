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

package io.helidon.security.providers.httpauth;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.crypto.Cipher;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Http authentication security provider.
 * Provides support for username and password authentication, with support for roles list.
 */
public final class HttpDigestAuthProvider extends SynchronousProvider implements AuthenticationProvider {
    static final String HEADER_AUTHENTICATION_REQUIRED = "WWW-Authenticate";
    static final String HEADER_AUTHENTICATION = "authorization";
    static final String DIGEST_PREFIX = "digest ";
    private static final int UNAUTHORIZED_STATUS_CODE = 401;
    private static final int SALT_LENGTH = 16;
    private static final int AES_NONCE_LENGTH = 12;
    private static final Logger LOGGER = Logger.getLogger(HttpDigestAuthProvider.class.getName());

    private final List<HttpDigest.Qop> digestQopOptions = new LinkedList<>();
    private final SecureUserStore userStore;
    private final boolean optional;
    private final String realm;
    private final SubjectType subjectType;
    private final HttpDigest.Algorithm digestAlgorithm;
    private final SecureRandom random;
    // Nonce validity - basically how often should we re-request authentication from the browser
    private final long digestNonceTimeoutMillis;
    // secret to encrypt nonce with, so only we can create it, otherwise others may be able to create a nonce we accept
    private final char[] digestServerSecret;

    private HttpDigestAuthProvider(Builder builder) {
        this.userStore = builder.userStore;
        this.optional = builder.optional;
        this.realm = builder.realm;
        this.subjectType = builder.subjectType;
        this.digestAlgorithm = builder.digestAlgorithm;
        this.digestQopOptions.addAll(builder.digestQopOptions);
        this.digestNonceTimeoutMillis = builder.digestNonceTimeoutMillis;
        this.digestServerSecret = builder.digestServerSecret;

        this.random = new SecureRandom();
    }

    /**
     * Get a builder instance to construct a new security provider.
     * Alternative approach is {@link #create(Config)} (or {@link HttpDigestAuthProvider#create(Config)}).
     *
     * @return builder to fluently construct Basic security provider
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load this provider from configuration.
     *
     * @param config Configuration located at this provider's configuration (e.g. child is either http-basic-auth or
     *               http-digest-auth)
     * @return instance of provider configured from provided config
     */
    public static HttpDigestAuthProvider create(Config config) {
        return builder().config(config).build();
    }

    static String nonce(long timeInMillis, Random random, char[] serverSecret) {
        // nonce is:  encrypt(salt(random(16bytes)) + timestamp (currentTimeInMillis))
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] aesNonce = new byte[AES_NONCE_LENGTH];
        random.nextBytes(aesNonce);
        byte[] timestamp = HttpAuthUtil.toBytes(timeInMillis);

        Cipher cipher = HttpAuthUtil.cipher(serverSecret, salt, aesNonce, Cipher.ENCRYPT_MODE);
        try {
            timestamp = cipher.doFinal(timestamp);

            byte[] result = new byte[salt.length + aesNonce.length + timestamp.length];
            System.arraycopy(salt, 0, result, 0, salt.length);
            System.arraycopy(aesNonce, 0, result, salt.length, aesNonce.length);
            System.arraycopy(timestamp, 0, result, aesNonce.length + salt.length, timestamp.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Encryption failed, though this should not happen. This is a bug.", e);
            //returning an invalid nonce...
            return "failed_nonce_value";
        }
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        Map<String, List<String>> headers = providerRequest.env().headers();
        List<String> authorizationHeader = headers.get(HEADER_AUTHENTICATION);

        if (null == authorizationHeader) {
            return failOrAbstain("No " + HEADER_AUTHENTICATION + " header");
        }

        return authorizationHeader.stream()
                .filter(header -> header.toLowerCase().startsWith(DIGEST_PREFIX))
                .findFirst()
                .map(value -> validateDigestAuth(value, providerRequest.env()))
                .orElseGet(() ->
                        failOrAbstain("Authorization header does not contain digest authentication: " + authorizationHeader));

    }

    private AuthenticationResponse validateDigestAuth(String headerValue, SecurityEnvironment env) {
        DigestToken token;
        try {
            token = DigestToken.fromAuthorizationHeader(headerValue.substring(DIGEST_PREFIX.length()),
                                                        env.method().toLowerCase());
        } catch (HttpAuthException e) {
            LOGGER.log(Level.FINEST, "Failed to process digest token", e);
            return failOrAbstain(e.getMessage());
        }
        // decrypt
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(token.getNonce());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINEST, "Failed to base64 decode nonce", e);
            // not base 64
            return failOrAbstain("Nonce must be base64 encoded");
        }
        if (bytes.length < 17) {
            return failOrAbstain("Invalid nonce length");
        }
        byte[] salt = new byte[SALT_LENGTH];
        byte[] aesNonce = new byte[AES_NONCE_LENGTH];
        byte[] encryptedBytes = new byte[bytes.length - SALT_LENGTH - AES_NONCE_LENGTH];

        System.arraycopy(bytes, 0, salt, 0, salt.length);
        System.arraycopy(bytes, SALT_LENGTH, aesNonce, 0, aesNonce.length);
        System.arraycopy(bytes, SALT_LENGTH + AES_NONCE_LENGTH, encryptedBytes, 0, encryptedBytes.length);
        Cipher cipher = HttpAuthUtil.cipher(digestServerSecret, salt, aesNonce, Cipher.DECRYPT_MODE);

        try {
            byte[] timestampBytes = cipher.doFinal(encryptedBytes);
            long nonceTimestamp = HttpAuthUtil.toLong(timestampBytes, 0, timestampBytes.length);
            //validate nonce
            if ((System.currentTimeMillis() - nonceTimestamp) > digestNonceTimeoutMillis) {
                return failOrAbstain("Nonce timeout");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Failed to validate nonce", e);
            return failOrAbstain("Invalid nonce value");
        }

        // validate realm
        if (!realm.equals(token.getRealm())) {
            return failOrAbstain("Invalid realm");
        }

        return userStore.user(token.getUsername())
                .map(user -> {
                    if (token.validateLogin(user)) {
                        // yay, correct user and password!!!
                        if (subjectType == SubjectType.USER) {
                            return AuthenticationResponse.success(buildSubject(user));
                        } else {
                            return AuthenticationResponse.successService(buildSubject(user));
                        }
                    } else {
                        return failOrAbstain("Invalid username or password");
                    }
                })
                .orElse(failOrAbstain("Invalid username or password"));
    }

    private AuthenticationResponse failOrAbstain(String message) {
        if (optional)
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .description(message)
                    .build();
       else
            return AuthenticationResponse.builder()
                    .statusCode(UNAUTHORIZED_STATUS_CODE)
                    .responseHeader(HEADER_AUTHENTICATION_REQUIRED, buildChallenge())
                    .status(AuthenticationResponse.SecurityStatus.FAILURE)
                    .description(message)
                    .build();
    }

    private String buildChallenge() {
        StringBuilder challenge = new StringBuilder();
        challenge.append("Digest realm=\"").append(realm).append("\"");

        //challenge for digest
        if (!digestQopOptions.isEmpty()) {
            challenge.append(", qop=\"").append(join(digestQopOptions)).append("\"");
        }
        challenge.append(", algorithm=\"").append(digestAlgorithm.getAlgorithm()).append("\"");
        challenge.append(", nonce=\"").append(nonce(System.currentTimeMillis(), random, digestServerSecret)).append("\"");
        challenge.append(", opaque=\"").append(opaque()).append("\"");

        return challenge.toString();
    }

    private String opaque() {
        //opaque is now just a random string
        //todo we may provide this as builder option, to allow user to supply their opaque (we should provide them request)
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String join(List<HttpDigest.Qop> digestQopOptions) {
        return digestQopOptions.stream().map(HttpDigest.Qop::getQop).collect(Collectors.joining(","));
    }

    private Subject buildSubject(SecureUserStore.User user) {
        Subject.Builder builder = Subject.builder()
                .principal(Principal.builder()
                                   .name(user.login())
                                   .build())
                .addPrivateCredential(SecureUserStore.User.class, user);

        user.roles()
                .forEach(role -> builder.addGrant(Role.create(role)));

        return builder.build();
    }

    /**
     * {@link HttpDigestAuthProvider} fluent API builder.
     */
    public static final class Builder implements io.helidon.common.Builder<HttpDigestAuthProvider> {
        private static final SecureUserStore EMPTY_STORE = login -> Optional.empty();
        /**
         * Default is 24 hours.
         */
        public static final long DEFAULT_DIGEST_NONCE_TIMEOUT = 24 * 60 * 60 * 1000;
        private final List<HttpDigest.Qop> digestQopOptions = new LinkedList<>();
        private SecureUserStore userStore = EMPTY_STORE;
        private boolean optional = false;
        private String realm = "Helidon";
        private SubjectType subjectType = SubjectType.USER;
        private HttpDigest.Algorithm digestAlgorithm = HttpDigest.Algorithm.MD5;
        private boolean noDigestQop = false;
        private long digestNonceTimeoutMillis = DEFAULT_DIGEST_NONCE_TIMEOUT;
        private char[] digestServerSecret = randomSecret();

        private Builder() {
        }

        /**
         * Update builder from configuration.
         * @param config to read configuration from, located on the node of the provider
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("realm").asString().ifPresent(this::realm);
            config.get("users").as(ConfigUserStore::create).ifPresent(this::userStore);
            config.get("algorithm").asString().as(HttpDigest.Algorithm::valueOf).ifPresent(this::digestAlgorithm);
            config.get("nonce-timeout-millis").asLong()
                    .ifPresent(timeout -> this.digestNonceTimeout(timeout, TimeUnit.MILLISECONDS));
            config.get("principal-type").asString().as(SubjectType::valueOf).ifPresent(this::subjectType);

            config.get("server-secret")
                    .asString()
                    .map(String::toCharArray)
                    .ifPresent(this::digestServerSecret);

            config.get("qop").asList(HttpDigest.Qop::create).ifPresent(qop -> {
                if (qop.isEmpty()) {
                    noDigestQop();
                } else {
                    qop.forEach(this::addDigestQop);
                }
            });

            return this;
        }

        private static char[] randomSecret() {
            Random random = new Random();
            String pwd = new BigInteger(130, random).toString(32);

            return pwd.toCharArray();
        }

        @Override
        public HttpDigestAuthProvider build() {
            if (digestQopOptions.isEmpty() && !noDigestQop) {
                digestQopOptions.add(HttpDigest.Qop.AUTH);
            }

            Objects.requireNonNull(userStore, "User store must be configured");

            return new HttpDigestAuthProvider(this);
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
         * Set user store to obtain passwords and roles based on logins.
         *
         * @param store User store to use
         * @return updated builder instance
         */
        public Builder userStore(SecureUserStore store) {
            this.userStore = store;
            return this;
        }

        /**
         * Whether authentication is required.
         * By default, request will fail if the authentication cannot be verified.
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
         * Set the realm to use when challenging users.
         *
         * @param realm security realm name to send to browser (or any other client) when unauthenticated
         * @return updated builder instance
         */
        public Builder realm(String realm) {
            this.realm = realm;
            return this;
        }

        /**
         * Digest algorithm to use.
         *
         * @param algorithm Algorithm to use, default is {@link HttpDigest.Algorithm#MD5}
         * @return updated builder instance
         */
        public Builder digestAlgorithm(HttpDigest.Algorithm algorithm) {
            this.digestAlgorithm = algorithm;
            return this;
        }

        /**
         * How long will the nonce value be valid. When timed-out, browser will re-request username/password.
         * Defaults to {@link #DEFAULT_DIGEST_NONCE_TIMEOUT} {@link TimeUnit#MILLISECONDS}.
         *
         * @param duration Duration value
         * @param unit     Duration time unit
         * @return updated builder instance
         */
        public Builder digestNonceTimeout(long duration, TimeUnit unit) {
            this.digestNonceTimeoutMillis = unit.toMillis(duration);
            return this;
        }

        /**
         * The nonce is encrypted using this secret - to make sure the nonce we get back was generated by us and to
         * make sure we can safely time-out nonce values.
         * This secret must be the same for all service instances (or all services that want to share the same authentication).
         * Defaults to a random password - e.g. if deployed to multiple servers, the authentication WILL NOT WORK. You MUST
         * provide your own password to work in a distributed environment with non-sticky load balancing.
         *
         * @param serverSecret a password to encrypt our nonce values with
         * @return updated builder instance
         */
        public Builder digestServerSecret(char[] serverSecret) {
            this.digestServerSecret = Arrays.copyOf(serverSecret, serverSecret.length);

            return this;
        }

        /**
         * Digest QOP to support.
         *
         * @param qop qop to add to list of supported qops
         * @return updated builder instance
         */
        public Builder addDigestQop(HttpDigest.Qop qop) {
            this.digestQopOptions.add(qop);
            return this;
        }

        /**
         * Do not use qop in challenge (will fallback to legacy RFC-2069 instead of RFC-2617.
         *
         * @return updated builder instance
         */
        public Builder noDigestQop() {
            this.noDigestQop = true;
            this.digestQopOptions.clear();
            return this;
        }
    }
}
