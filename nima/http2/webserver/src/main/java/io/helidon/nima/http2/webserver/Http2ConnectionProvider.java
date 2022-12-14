/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webserver;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.nima.webserver.ServerConnectionSelector;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;

/**
 * {@link io.helidon.nima.webserver.spi.ServerConnectionProvider} implementation for HTTP/2 server connection provider.
 */
public class Http2ConnectionProvider implements ServerConnectionProvider {

    /** HTTP/2 server connection provider configuration node name. */
    static final String CONFIG_NAME = "http_2";

    private Http2Config config;

    /**
     * Creates an instance of HTTP/2 server connection provider.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http2ConnectionProvider() {
        this.config = Http2Config.builder().build();
    }

    private Http2ConnectionProvider(Http2Config config) {
        this.config = config;
    }

    @Override
    public List<String> configKeys() {
        return List.of(CONFIG_NAME);
    }

    @Override
    public void config(Config config) {
        // Empty node can't overwrite existing configuration.
        if (config.exists()) {
            // Initialize builder with existing configuration
            this.config = Http2Config.builder(this.config)
                    // Overwrite values from config node
                    .config(config)
                    .build();
        }
    }

    @Override
    public ServerConnectionSelector create() {
        return new Http2ConnectionSelector(config);
    }

    /**
     * Builder to set up this provider.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link Http2ConnectionProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Http2ConnectionProvider.Builder, Http2ConnectionProvider> {

        private final Http2Config.Builder configBuilder;

        private Builder() {
            configBuilder = Http2Config.builder();
        }

        /**
         * The size of the largest frame payload that the sender is willing to receive in bytes.
         * See RFC 9113 section 6.5.2 for details.
         *
         * @param maxFrameSize maximum length of the frame payload
         * @return updated builder
         */
        public Builder maxFrameSize(long maxFrameSize) {
            configBuilder.maxFrameSize(maxFrameSize);
            return this;
        }

        /**
         * The maximum field section size that the sender is prepared to accept in bytes.
         * See RFC 9113 section 6.5.2 for details.
         *
         * @param maxHeaderListSize maximum field section size
         * @return updated builder
         */
        public Builder maxHeaderListSize(long maxHeaderListSize) {
            configBuilder.maxHeaderListSize(maxHeaderListSize);
            return this;
        }

        @Override
        public Http2ConnectionProvider build() {
            return new Http2ConnectionProvider(configBuilder.build());
        }

    }

}
