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

package io.helidon.webserver;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.SSLContext;

public final class TlsConfig {
    private final Set<String> enabledTlsProtocols;
    private final SSLContext sslContext;
    private boolean enabled;

    private TlsConfig(Builder builder) {
        this.enabledTlsProtocols = Set.copyOf(builder.enabledTlsProtocols);
        this.sslContext = builder.sslContext;
        this.enabled = (null != sslContext);
    }

    public static Builder builder() {
        return new Builder();
    }

    Collection<String> enabledTlsProtocols() {
        return enabledTlsProtocols;
    }

    SSLContext sslContext() {
        return sslContext;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Fluent API builder for {@link io.helidon.webserver.TlsConfig}.
     */
    public static class Builder implements io.helidon.common.Builder<TlsConfig> {
        private final Set<String> enabledTlsProtocols = new HashSet<>();
        private SSLContext sslContext;
        private boolean enabled;

        private Builder() {
        }

        @Override
        public TlsConfig build() {
            return new TlsConfig(this);
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

        /**
         * Configures the TLS protocols to enable with the server socket.
         * @param protocols protocols to enable, if empty, enables defaults
         *
         * @return this builder
         * @throws java.lang.NullPointerException in case the protocols is null
         */
        public Builder enabledTlsProtocols(String... protocols) {
            return enabledTlsProtocols(Arrays.asList(Objects.requireNonNull(protocols)));
        }

        /**
         * Configures the TLS protocols to enable with the server socket.
         *
         * @param protocols protocols to enable, if empty enables
         *  the default protocols
         * @return this builder
         * @throws java.lang.NullPointerException in case the protocols is null
         */
        public Builder enabledTlsProtocols(Collection<String> protocols) {
            this.enabledTlsProtocols.clear();
            this.enabledTlsProtocols.addAll(protocols);
            return this;
        }
    }
}
