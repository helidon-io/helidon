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

package io.helidon.security.provider.httpauth;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Http authentication security provider.
 * Provides support for username and password authentication, with support for roles list.
 */
public class HttpDigestAuthProvider extends SynchronousProvider implements AuthenticationProvider {
    static final String HEADER_AUTHENTICATION_REQUIRED = "WWW-Authenticate";
    static final String HEADER_AUTHENTICATION = "authorization";
    static final String DIGEST_PREFIX = "digest ";
    private static final Logger LOGGER = Logger.getLogger(HttpDigestAuthProvider.class.getName());
    private final List<HttpDigest.Qop> digestQopOptions = new LinkedList<>();
    private final UserStore userStore;
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
     * Alternative approach is {@link #fromConfig(Config)} (or {@link HttpDigestAuthProvider#fromConfig(Config)}).
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
    public static HttpDigestAuthProvider fromConfig(Config config) {
        return Builder.fromConfig(config).build();
    }

    static String nonce(long timeInMillis, Random random, char[] serverSecret) {
        // nonce is:  encrypt(salt(random(16bytes)) + timestamp (currentTimeInMillis))
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] timestamp = HttpAuthUtil.toBytes(timeInMillis);

        Cipher cipher = HttpAuthUtil.cipher(serverSecret, salt, Cipher.ENCRYPT_MODE);
        try {
            timestamp = cipher.doFinal(timestamp);

            byte[] result = new byte[salt.length + timestamp.length];
            System.arraycopy(salt, 0, result, 0, salt.length);
            System.arraycopy(timestamp, 0, result, salt.length, timestamp.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Encryption failed, though this should not happen. This is a bug.", e);
            //returning an invalid nonce...
            return "failed_nonce_value";
        }
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        Map<String, List<String>> headers = providerRequest.getEnv().getHeaders();
        List<String> authorizationHeader = headers.get(HEADER_AUTHENTICATION);

        if (null == authorizationHeader) {
            return fail("No " + HEADER_AUTHENTICATION + " header");
        }

        return authorizationHeader.stream()
                .filter(header -> header.toLowerCase().startsWith(DIGEST_PREFIX))
                .findFirst()
                .map(value -> validateDigestAuth(value, providerRequest.getEnv()))
                .orElseGet(() -> fail("Authorization header does not contain digest authentication: " + authorizationHeader));

    }

    private AuthenticationResponse validateDigestAuth(String headerValue, SecurityEnvironment env) {
        DigestToken token;
        try {
            token = DigestToken.fromAuthorizationHeader(headerValue.substring(DIGEST_PREFIX.length()),
                                                        env.getMethod().toLowerCase());
        } catch (HttpAuthException e) {
            LOGGER.log(Level.FINEST, "Failed to process digest token", e);
            return fail(e.getMessage());
        }
        // decrypt
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(token.getNonce());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINEST, "Failed to base64 decode nonce", e);
            // not base 64
            return fail("Nonce must be base64 encoded");
        }
        if (bytes.length < 17) {
            return fail("Invalid nonce length");
        }
        byte[] salt = new byte[16];
        System.arraycopy(bytes, 0, salt, 0, salt.length);
        Cipher cipher = HttpAuthUtil.cipher(digestServerSecret, salt, Cipher.DECRYPT_MODE);

        try {
            byte[] timestampBytes = cipher.doFinal(bytes, salt.length, bytes.length - salt.length);
            long nonceTimestamp = HttpAuthUtil.toLong(timestampBytes, 0, timestampBytes.length);
            //validate nonce
            if ((System.currentTimeMillis() - nonceTimestamp) > digestNonceTimeoutMillis) {
                return fail("Nonce timeout");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Failed to validate nonce", e);
            return fail("Invalid nonce value");
        }

        // validate realm
        if (!realm.equals(token.getRealm())) {
            return fail("Invalid realm");
        }

        return userStore.getUser(token.getUsername())
                .map(user -> {
                    if (token.validateLogin(user.getPassword())) {
                        // yay, correct user and password!!!
                        if (subjectType == SubjectType.USER) {
                            return AuthenticationResponse.success(buildSubject(user));
                        } else {
                            return AuthenticationResponse.successService(buildSubject(user));
                        }
                    } else {
                        return fail("Invalid username or password");
                    }
                })
                .orElse(fail("Invalid username or password"));
    }

    private AuthenticationResponse fail(String message) {
        return AuthenticationResponse.builder()
                .statusCode(401)
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
        return String.join(",", digestQopOptions.stream().map(HttpDigest.Qop::getQop).collect(Collectors.toList()));
    }

    private Subject buildSubject(UserStore.User user) {
        Subject.Builder builder = Subject.builder()
                .principal(Principal.builder()
                                   .name(user.getLogin())
                                   .build())
                .addPrivateCredential(UserStore.User.class, user);

        user.getRoles()
                .forEach(role -> builder.addGrant(Role.create(role)));

        return builder.build();
    }

    /**
     * {@link HttpDigestAuthProvider} fluent API builder.
     */
    public static class Builder implements io.helidon.common.Builder<HttpDigestAuthProvider> {
        /**
         * Default is 24 hours.
         */
        public static final long DEFAULT_DIGEST_NONCE_TIMEOUT = 24 * 60 * 60 * 1000;
        private final List<HttpDigest.Qop> digestQopOptions = new LinkedList<>();
        private UserStore userStore;
        private String realm;
        private SubjectType subjectType = SubjectType.USER;
        private HttpDigest.Algorithm digestAlgorithm = HttpDigest.Algorithm.MD5;
        private boolean noDigestQop = false;
        private long digestNonceTimeoutMillis = DEFAULT_DIGEST_NONCE_TIMEOUT;
        private char[] digestServerSecret;

        private Builder() {
        }

        static Builder fromConfig(Config config) {
            Builder builder = new Builder();

            builder.realm(config.get("realm").asString("realm"))
                    .userStore(config.get("users").asOptional(ConfigUserStore.class)
                                       .orElseThrow(() -> new HttpAuthException(
                                               "No users configured! Key \"users\" must be in configuration")))
                    .digestAlgorithm(config.get("algorithm")
                                             .as(HttpDigest.Algorithm.class, HttpDigest.Algorithm.MD5))
                    .digestNonceTimeout(config.get("nonce-timeout-millis")
                                                .asLong(DEFAULT_DIGEST_NONCE_TIMEOUT), TimeUnit.MILLISECONDS)
                    .digestServerSecret(config.get("server-secret")
                                                .value()
                                                .map(String::toCharArray)
                                                .orElse(randomSecret()));

            config.get("principal-type").asOptional(SubjectType.class).ifPresent(builder::subjectType);

            config.get("qop").asOptionalList(HttpDigest.Qop.class).ifPresent(qop -> {
                if (qop.isEmpty()) {
                    builder.noDigestQop();
                } else {
                    qop.forEach(builder::addDigestQop);
                }
            });

            return builder;
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
        public Builder userStore(UserStore store) {
            this.userStore = store;
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
