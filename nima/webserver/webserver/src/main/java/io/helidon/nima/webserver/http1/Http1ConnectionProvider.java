/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.nima.webserver.ServerConnectionSelector;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;

/**
 * {@link io.helidon.nima.webserver.spi.ServerConnectionProvider} implementation for HTTP/1.1 server connection provider.
 */
public class Http1ConnectionProvider implements ServerConnectionProvider {

    /** HTTP/1.1 server connection provider configuration node name. */
    private static final String CONFIG_NAME = "http_1_1";

    // Config key to HTTP/1.1 connection upgrade providers mapping
    private final Map<String, List<Http1UpgradeProvider>> providersConfigMap;

    private Http1Config config;

    private Http1ConnectionProvider(Http1Config config, List<Http1UpgradeProvider> providers) {
        this.config = config;
        this.providersConfigMap = initUpgradeProvidersConfigMap(providers);
    }

    /**
     * Create a new instance with default configuration.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http1ConnectionProvider() {
        this.config = Http1Config.builder().build();
        this.providersConfigMap = initUpgradeProvidersConfigMap(upgradeProviderBuilder().build());
    }


    // Constructor helper: Build config key to HTTP/1.1 connection upgrading providers mapping
    private static Map<String, List<Http1UpgradeProvider>> initUpgradeProvidersConfigMap(
            List<Http1UpgradeProvider> factories) {
        Map<String, List<Http1UpgradeProvider>> map = new HashMap<>(factories.size());
        factories.forEach(factory -> addProviderConfigMapping(map, factory));
        return map;
    }

    // Constructor helper: Add config key to HTTP/1.1 connection upgrading providers mapping into provided map
    private static void addProviderConfigMapping(Map<String, List<Http1UpgradeProvider>> map, Http1UpgradeProvider provider) {
        List<Http1UpgradeProvider> providers;
        if (map.containsKey(provider.configKey())) {
            providers = map.get(provider.configKey());
        } else {
            providers = new LinkedList<>();
            map.put(provider.configKey(), providers);
        }
        providers.add(provider);
    }

    // Returns all config keys of Http1UpgradeProvider instances and this provider.
    // This method is called just once from WebServer.Builder so let's build the list on the fly.
    @Override
    public Iterable<String> configKeys() {
        List<String> keys = new ArrayList<>(providersConfigMap.keySet().size() + 1);
        keys.addAll(providersConfigMap.keySet());
        keys.add(CONFIG_NAME);
        return keys;
    }

    @Override
    public void config(Config config) {
        if (CONFIG_NAME.equals(config.name())) {
            // Empty node can't overwrite existing local configuration.
            if (config.exists()) {
                // Initialize builder with existing configuration
                this.config = Http1Config.builder(this.config)
                        // Overwrite values from config node
                        .config(config)
                        .build();
            }
        // Send configuration nodes to HTTP/1.1 connection upgrading providers including possible empty nodes.
        } else {
            List<Http1UpgradeProvider> providers = providersConfigMap.get(config.name());
            if (providers != null) {
                providers.forEach(provider -> provider.config(config));
            }
        }
    }

    @Override
    public ServerConnectionSelector create() {
        // Calculate providers count to properly scale HashMap instance
        int size = 0;
        for (List<Http1UpgradeProvider> providers : providersConfigMap.values()) {
            size += providers.size();
        }
        // Build HTTP/1.1 connection upgrade providers map.
        Map<String, Http1Upgrader> selectors = new HashMap<>(size);
        for (List<Http1UpgradeProvider> providers : providersConfigMap.values()) {
            for (Http1UpgradeProvider provider : providers) {
                Http1Upgrader selector = provider.create();
                selectors.putIfAbsent(selector.supportedProtocol(), selector);
            }
        }
        // Map passed to connection provider instance must be immutable
        return new Http1ConnectionSelector(config, Map.copyOf(selectors));
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
     * Fluent API builder for {@link Http1ConnectionProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Http1ConnectionProvider.Builder, Http1ConnectionProvider> {

        private final Http1Config.Builder configBuilder;
        private final UpgradeProviderBuilder upgradeProviderBuilder;

        private Builder() {
            configBuilder = Http1Config.builder();
            upgradeProviderBuilder = upgradeProviderBuilder();
        }

        /**
         * Maximal size of received HTTP prologue (GET /path HTTP/1.1).
         *
         * @param maxPrologueLength maximal size in bytes
         * @return updated builder
         */
        public Builder maxPrologueLength(int maxPrologueLength) {
            configBuilder.maxPrologueLength(maxPrologueLength);
            return this;
        }

        /**
         * Maximal size of received headers in bytes.
         *
         * @param maxHeadersSize maximal header size
         * @return updated builder
         */
        public Builder maxHeadersSize(int maxHeadersSize) {
            configBuilder.maxHeaderSize(maxHeadersSize);
            return this;
        }

        /**
         * Whether to validate headers.
         * If set to false, any value is accepted, otherwise validates headers + known headers
         * are validated by format
         * (content length is always validated as it is part of protocol processing (other headers may be validated if
         * features use them)).
         *
         * @param validateHeaders whether to validate headers
         * @return updated builder
         */
        public Builder validateHeaders(boolean validateHeaders) {
            configBuilder.validateHeaders(validateHeaders);
            return this;
        }

        /**
         * If set to false, any path is accepted (even containing illegal characters).
         *
         * @param validatePath whether to validate path
         * @return updated builder
         */
        public Builder validatePath(boolean validatePath) {
            configBuilder.validatePath(validatePath);
            return this;
        }

        /**
         * Add a configured upgrade provider. This will replace the instance discovered through service loader (if one exists).
         *
         * @param provider add a provider
         * @return updated builder
         */
        public Builder addUpgradeProvider(Http1UpgradeProvider provider) {
            upgradeProviderBuilder.addUpgradeProvider(provider);
            return this;
        }

        @Override
        public Http1ConnectionProvider build() {
            return new Http1ConnectionProvider(configBuilder.build(), upgradeFactories());
        }

        private List<Http1UpgradeProvider> upgradeFactories() {
            return upgradeProviderBuilder.build();
        }

    }

    private static UpgradeProviderBuilder upgradeProviderBuilder() {
        return new UpgradeProviderBuilder();
    }

    static class UpgradeProviderBuilder {

        private final HelidonServiceLoader.Builder<Http1UpgradeProvider> builder;

        private UpgradeProviderBuilder() {
            builder = HelidonServiceLoader.builder(ServiceLoader.load(Http1UpgradeProvider.class));
        }

        UpgradeProviderBuilder addUpgradeProvider(Http1UpgradeProvider provider) {
            builder.addService(provider);
            return this;
        }

        List<Http1UpgradeProvider> build() {
            return builder.build().asList();
        }

    }

}
