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

package io.helidon.nima.websocket.webserver;

import java.util.HashSet;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.nima.webserver.http1.Http1Upgrader;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for upgrade from HTTP/1.1 to WebSocket.
 */
public class WsUpgradeProvider implements Http1UpgradeProvider {

    private final Set<String> origins;

    /**
     * Create a new instance with default configuration.
     *
     * @deprecated This constructor is only to be used by {@link java.util.ServiceLoader}, use {@link #builder()}
     */
    @Deprecated()
    public WsUpgradeProvider() {
        this(new HashSet<>());
    }

    protected WsUpgradeProvider(Set<String> origins) {
        this.origins = origins;
    }

    /** HTTP/2 server connection provider configuration node name. */
    private static final String CONFIG_NAME = "websocket";

    @Override
    public String configKey() {
        return CONFIG_NAME;
    }

    @Override
    public void config(Config config) {
        // Accept origins as list of String values from config file
        config.get("origins")
                .asList(String.class)
                .ifPresent(origins::addAll);
    }

    @Override
    public Http1Upgrader create() {
        return new WsUpgrader(Set.copyOf(origins));
    }

    protected Set<String> origins() {
        return origins;
    }

    /**
     * New builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Abstract Fluent API builder for {@link WsUpgradeProvider} and child classes.
     *
     * @param <B> Type of the builder
     * @param <T> Type of the built instance
     */
    protected abstract static class AbstractBuilder<B extends AbstractBuilder<B, T>, T>
            implements io.helidon.common.Builder<B, T> {

        private final Set<String> origins = new HashSet<>();

        protected AbstractBuilder() {
        }

        /**
         * Add supported origin.
         *
         * @param origin origin to add
         * @return updated builder
         */
        public B addOrigin(String origin) {
            origins.add(origin);
            return identity();
        }

        protected Set<String> origins() {
            return origins;
        }

    }

    /**
     * Fluent API builder for {@link WsUpgradeProvider}.
     */
    public static final class Builder extends AbstractBuilder<Builder, WsUpgradeProvider> {

        private Builder() {
        }

        @Override
        public WsUpgradeProvider build() {
            return new WsUpgradeProvider(origins());
        }

    }

}
