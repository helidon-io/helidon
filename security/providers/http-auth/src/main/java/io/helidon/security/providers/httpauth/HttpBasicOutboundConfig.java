/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.util.TokenHandler;

import static java.util.Objects.requireNonNull;

/**
 * Security provider configuration for outbound.
 */
public class HttpBasicOutboundConfig {
    /**
     * An empty char array used for empty passwords.
     */
    public static final char[] EMPTY_PASSWORD = new char[0];
    /**
     * Default token handler for HTTP basic authentication - uses {@code Authorization} header
     * and {@code basic } prefix.
     */
    public static final TokenHandler DEFAULT_TOKEN_HANDLER = TokenHandler.builder()
            .tokenHeader("Authorization")
            .tokenPrefix("basic ")
            .build();

    private final TokenHandler tokenHandler;
    private final boolean hasExplicitUser;
    private final String explicitUser;
    private final char[] explicitPassword;

    private HttpBasicOutboundConfig(Builder builder) {
        this.tokenHandler = builder.tokenHandler;
        this.hasExplicitUser = builder.hasExplicitUser;
        this.explicitUser = builder.explicitUser;
        this.explicitPassword = (builder.explicitPassword == null) ? EMPTY_PASSWORD : builder.explicitPassword.toCharArray();
    }

    /**
     * Fluent API builder to create basic outbound configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a default basic outbound configuration.
     * This configuration is to propagate current identity.
     *
     * @return a new configuration
     */
    public static HttpBasicOutboundConfig create() {
        return builder().build();
    }

    /**
     * Create basic outbound configuration from config.
     *
     * @param config configuration for outbound config
     * @return a new configuration
     */
    public static HttpBasicOutboundConfig create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create basic outbound configuration for an explicit user and password.
     *
     * @param user username
     * @param password password
     * @return a new configuration
     */
    public static HttpBasicOutboundConfig create(String user, String password) {
        return HttpBasicOutboundConfig.builder()
                .explicitUser(user)
                .explicitPassword(password)
                .build();
    }

    /**
     * Create basic outbound configuration from an outbound target.
     *
     * @param outboundTarget outbound target
     * @return a new basic outbound config from custom object, configuration, or the default one
     */
    public static HttpBasicOutboundConfig create(OutboundTarget outboundTarget) {
        return outboundTarget.customObject(HttpBasicOutboundConfig.class)
                .map(HttpBasicOutboundConfig.class::cast)
                .or(() -> outboundTarget.getConfig().map(HttpBasicOutboundConfig::create))
                .orElseGet(HttpBasicOutboundConfig::create);
    }

    TokenHandler tokenHandler() {
        return tokenHandler;
    }

    boolean hasExplicitUser() {
        return hasExplicitUser;
    }

    String explicitUser() {
        return explicitUser;
    }

    char[] explicitPassword() {
        return explicitPassword;
    }

    /**
     * Fluent API builder for {@link io.helidon.security.providers.httpauth.HttpBasicOutboundConfig}.
     */
    public static class Builder implements io.helidon.common.Builder<HttpBasicOutboundConfig> {
        private TokenHandler tokenHandler = DEFAULT_TOKEN_HANDLER;
        private boolean hasExplicitUser = false;
        private String explicitUser;
        private String explicitPassword;

        private Builder() {
        }

        @Override
        public HttpBasicOutboundConfig build() {
            if (explicitPassword != null && explicitUser == null) {
                throw new SecurityException("User must be configured if password is configured for HTTP Basic Authentication"
                                                    + " outbound");
            }
            return new HttpBasicOutboundConfig(this);
        }

        /**
         * Updated this configuration from the config instance.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("outbound-token").as(TokenHandler::create)
                    .ifPresent(this::tokenHandler);
            config.get("username").asString().ifPresent(this::explicitUser);
            config.get("password").asString().ifPresent(this::explicitPassword);

            return this;
        }

        /**
         * Token handler to add the outbound basic authentication to headers.
         *
         * @param tokenHandler handler for outbound headers
         * @return updated builder instance
         */
        public Builder tokenHandler(TokenHandler tokenHandler) {
            this.tokenHandler = requireNonNull(tokenHandler);
            return this;
        }

        /**
         * Configure explicit user to use for this outbound target.
         *
         * @param explicitUser username to use
         * @return updated builder instance
         */
        public Builder explicitUser(String explicitUser) {
            this.explicitUser = requireNonNull(explicitUser);
            this.hasExplicitUser = true;
            return this;
        }

        /**
         * Configure explicit password to use for this outbound target.
         *
         * @param explicitPassword password to use
         * @return updated builder instance
         */
        public Builder explicitPassword(String explicitPassword) {
            this.explicitPassword = explicitPassword;
            return this;
        }
    }
}
