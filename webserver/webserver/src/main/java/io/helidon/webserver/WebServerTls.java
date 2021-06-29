/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Objects;

import javax.net.ssl.SSLContext;

import io.helidon.config.Config;

/**
 * A class wrapping transport layer security (TLS) configuration for
 * WebServer sockets.
 */
public final class WebServerTls {

    private final SSLContext sslContext;
    private final ClientAuthentication clientAuth;

    private WebServerTls(Builder builder) {
        this.sslContext = builder.sslContext;
        this.clientAuth = builder.clientAuth;
    }

    /**
     * A fluent API builder for {@link WebServerTls}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create TLS configuration from config.
     *
     * @param config located on the node of the tls configuration (usually this is {@code ssl})
     * @return a new TLS configuration
     */
    public static WebServerTls create(Config config) {
        return builder().config(config).build();
    }

    SSLContext sslContext() {
        return sslContext;
    }

    ClientAuthentication clientAuth() {
        return clientAuth;
    }

    /**
     * Fluent API builder for {@link WebServerTls}.
     */
    public static class Builder implements io.helidon.common.Builder<WebServerTls> {

        private SSLContext sslContext;
        private ClientAuthentication clientAuth;

        private Builder() {
            clientAuth = ClientAuthentication.NONE;
        }

        @Override
        public WebServerTls build() {
            return new WebServerTls(this);
        }

        /**
         * Update this builder from configuration.
         *
         * @param config config on the node of SSL configuration
         * @return this builder
         */
        public Builder config(Config config) {
            config.get("client-auth").asString().ifPresent(this::clientAuth);
            sslContext = SSLContextBuilder.create(config);
            return this;
        }

        private void clientAuth(String it) {
            clientAuth(ClientAuthentication.valueOf(it.toUpperCase()));
        }

        /**
         * Configures whether client authentication will be required or not.
         *
         * @param clientAuth client authentication
         * @return this builder
         */
        public Builder clientAuth(ClientAuthentication clientAuth) {
            this.clientAuth = Objects.requireNonNull(clientAuth);
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param context a SSL context to use
         * @return this builder
         */
        public Builder sslContext(SSLContext context) {
            this.sslContext = context;
            return this;
        }

    }
}
