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

package io.helidon.security.providers;

import java.time.Instant;
import java.util.Optional;

import io.helidon.security.ClassToInstanceStore;

/**
 * A public credential representing an access token.
 * Example is a Google access token you get when authenticating against Google's Open ID Connect.
 */
public class TokenCredential {
    private final Optional<String> issuer;
    private final Optional<Instant> issueTime;
    private final Optional<Instant> expTime;
    private final String token;
    private final ClassToInstanceStore<Object> tokens = new ClassToInstanceStore<>();

    private TokenCredential(Builder builder) {
        this.token = builder.token;
        this.issuer = Optional.ofNullable(builder.issuer);
        this.issueTime = Optional.ofNullable(builder.issueTime);
        this.expTime = Optional.ofNullable(builder.expTime);
        this.tokens.putAll(builder.tokens);
    }

    /**
     * Creates a new token credential for the specified token.
     *
     * @param token     Token value (as received from external client)
     * @param issuer    Issuer of the token (such as accounts.google.com) - optional
     * @param issueTime Time instant the token was issued - optional
     * @param expTime   Time instant the token will expire - optional
     * @return new instance of credential
     */
    public static TokenCredential create(String token, String issuer, Instant issueTime, Instant expTime) {
        return builder().token(token)
                .issuer(issuer)
                .issueTime(issueTime)
                .expTime(expTime)
                .build();
    }

    /**
     * Get a builder for this class.
     *
     * @return a new builder to build an instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getToken() {
        return token;
    }

    public Optional<Instant> getIssueTime() {
        return issueTime;
    }

    public Optional<Instant> getExpTime() {
        return expTime;
    }

    public Optional<String> getIssuer() {
        return issuer;
    }

    /**
     * Get a token of a specific class.
     * By default the String.class is supported - and returns the token content.
     * Other instances may be available from authentication provider (e.g. Jwt).
     *
     * @param tokenClass class we want to get
     * @param <U>        type of the class
     * @return instance of the token if present
     */
    public <U> Optional<U> getTokenInstance(Class<U> tokenClass) {
        return tokens.getInstance(tokenClass);
    }

    @Override
    public String toString() {
        return "TokenCredential{"
                + "issuer='" + issuer + '\''
                + ", issueTime=" + issueTime
                + ", expTime=" + expTime
                + '}';
    }

    /**
     * Fluent API builder for {@link TokenCredential}.
     */
    public static class Builder implements io.helidon.common.Builder<TokenCredential> {
        private Instant issueTime;
        private Instant expTime;
        private String issuer;
        private String token;
        private ClassToInstanceStore<Object> tokens = new ClassToInstanceStore<>();

        private Builder() {
        }

        /**
         * Set the token content (the actual string travelling on the network).
         *
         * @param token token value
         * @return updated builder instance
         */
        public Builder token(String token) {
            this.token = token;
            addToken(String.class, token);
            return this;
        }

        /**
         * Time the token was issued.
         *
         * @param issueTime issue instant
         * @return updated builder instance
         */
        public Builder issueTime(Instant issueTime) {
            this.issueTime = issueTime;
            return this;
        }

        /**
         * Time the token would expire.
         *
         * @param expirationTime expiration instant
         * @return updated builder instance
         */
        public Builder expTime(Instant expirationTime) {
            this.expTime = expirationTime;
            return this;
        }

        /**
         * Issuer of the token.
         *
         * @param issuer issuer (such as accounts.google.com)
         * @return updated builder instance
         */
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Add a token instance (such as JWT instance). May contain more than one instance (e.g. for JWT, we may send both
         * SignedJwt and Jwt).
         *
         * @param tokenClass    class we want to register the instance under
         * @param tokenInstance instance
         * @param <T>           type of instance
         * @param <U>           type of class to register instance by
         * @return updated builder instance
         */
        public <T, U extends T> Builder addToken(Class<T> tokenClass, U tokenInstance) {
            tokens.putInstance(tokenClass, tokenInstance);
            return this;
        }

        /**
         * Add a token instance (such as JWT instance). May contain more than one instance (e.g. for JWT, we may send both
         * SignedJwt and Jwt).
         * Object is registered under the class it provides through {@link Object#getClass()}.
         *
         * @param token instance
         * @return updated builder instance
         */
        public Builder addToken(Object token) {
            this.tokens.putInstance(token);
            return this;
        }

        @Override
        public TokenCredential build() {
            return new TokenCredential(this);
        }
    }
}
