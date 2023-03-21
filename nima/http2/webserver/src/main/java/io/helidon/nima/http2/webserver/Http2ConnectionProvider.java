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
import java.util.ServiceLoader;
import java.util.function.Function;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolProvider;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

/**
 * {@link io.helidon.nima.webserver.spi.ServerConnectionProvider} implementation for HTTP/2 server connection provider.
 */
public class Http2ConnectionProvider implements ServerConnectionProvider {

    /**
     * HTTP/2 server connection provider configuration node name.
     */
    static final String CONFIG_NAME = "http_2";

    private final Http2Config http2Config;
    private final List<Http2SubProtocolProvider> subProtocolProviders;

    private Http2ConnectionProvider(Builder builder) {
        this.subProtocolProviders = builder.subProtocolProviders.build().asList();
        this.http2Config = builder.http2Config;
    }

    /**
     * Creates an instance of HTTP/2 server connection provider.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http2ConnectionProvider() {
        this(builder());
    }

    /**
     * Builder to set up this provider.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<String> configKeys() {
        return List.of(CONFIG_NAME);
    }

    @Override
    public ServerConnectionSelector create(Function<String, Config> configs) {
        Http2Config config;
        if (http2Config == null) {
            config = DefaultHttp2Config.toBuilder(configs.apply(CONFIG_NAME)).build();
        } else {
            config = http2Config;
        }

        var subProviders = subProtocolProviders.stream()
                .map(it -> it.create(configs.apply(it.configKey())))
                .toList();

        return new Http2ConnectionSelector(config, subProviders);
    }

    /**
     * Fluent API builder for {@link Http2ConnectionProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Http2ConnectionProvider.Builder, Http2ConnectionProvider> {
        private final HelidonServiceLoader.Builder<Http2SubProtocolProvider> subProtocolProviders = HelidonServiceLoader.builder(
                ServiceLoader.load(Http2SubProtocolProvider.class));
        private Http2Config http2Config;

        private Builder() {
        }

        @Override
        public Http2ConnectionProvider build() {
            return new Http2ConnectionProvider(this);
        }

        /**
         * Custom configuration of HTTP/2 connection provider.
         * If not defined, it will be configured from config, or defaults would be used.
         *
         * @param http2Config HTTP/2 configuration
         * @return updated builder
         */
        public Builder http2Config(Http2Config http2Config) {
            this.http2Config = http2Config;
            return this;
        }

        /**
         * Add a configured sub-protocol provider. This will replace the instance discovered through service loader (if one
         * exists).
         *
         * @param provider provider to add
         * @return updated builer
         */
        public Builder addSubProtocolProvider(Http2SubProtocolProvider provider) {
            subProtocolProviders.addService(provider);
            return this;
        }
    }

}
