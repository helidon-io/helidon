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

package io.helidon.security.providers.header;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.util.TokenHandler;

import static java.util.Objects.requireNonNull;

/**
 * Header assertion security provider configuration for outbound.
 */
public class HeaderAtnOutboundConfig {
    private final Optional<TokenHandler> tokenHandler;
    private final Optional<String> explicitUser;

    private HeaderAtnOutboundConfig(Builder builder) {
        this.tokenHandler = Optional.ofNullable(builder.tokenHandler);
        this.explicitUser = Optional.ofNullable(builder.explicitUser);
    }

    /**
     * Fluent API builder to create header outbound configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a default header outbound configuration.
     * This configuration is to propagate current identity.
     *
     * @return a new configuration
     */
    public static HeaderAtnOutboundConfig create() {
        return builder().build();
    }

    /**
     * Create header outbound configuration from config.
     *
     * @param config configuration for outbound config
     * @return a new configuration
     */
    public static HeaderAtnOutboundConfig create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create header outbound configuration from a token handler and username.
     *
     * @param tokenHandler token handler to update outbound headers
     * @param user username to propagate
     * @return a new header outbound config
     */
    public static HeaderAtnOutboundConfig create(TokenHandler tokenHandler, String user) {
        return builder()
                .tokenHandler(tokenHandler)
                .explicitUser(user)
                .build();
    }

    /**
     * Create header outbound configuration from an outbound target.
     *
     * @param outboundTarget outbound target
     * @return a new header outbound config from custom object, configuration, or the default one
     */
    public static HeaderAtnOutboundConfig create(OutboundTarget outboundTarget) {
        return outboundTarget.customObject(HeaderAtnOutboundConfig.class)
                .map(HeaderAtnOutboundConfig.class::cast)
                .or(() -> outboundTarget.getConfig().map(HeaderAtnOutboundConfig::create))
                .orElseGet(HeaderAtnOutboundConfig::create);
    }

    Optional<TokenHandler> tokenHandler() {
        return tokenHandler;
    }

    Optional<String> explicitUser() {
        return explicitUser;
    }

    /**
     * Fluent API builder for {@link HeaderAtnOutboundConfig}.
     */
    public static class Builder implements io.helidon.common.Builder<HeaderAtnOutboundConfig> {
        private TokenHandler tokenHandler;
        private String explicitUser;

        private Builder() {
        }

        @Override
        public HeaderAtnOutboundConfig build() {
            return new HeaderAtnOutboundConfig(this);
        }

        /**
         * Update this builder from configuration.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("outbound-token").as(TokenHandler::create)
                    .ifPresent(this::tokenHandler);
            config.get("username").asString().ifPresent(this::explicitUser);

            return this;
        }

        /**
         * Configuration of the outbound header the identity will be propagated.
         *
         * @param tokenHandler handler to update outbound headers
         * @return updated builder instance
         */
        public Builder tokenHandler(TokenHandler tokenHandler) {
            this.tokenHandler = requireNonNull(tokenHandler);
            return this;
        }

        /**
         * Username to propagate. If not configured, the current identity is propagated.
         *
         * @param explicitUser username to propagate
         * @return updated builder instance
         */
        public Builder explicitUser(String explicitUser) {
            this.explicitUser = requireNonNull(explicitUser);
            return this;
        }
    }
}
