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

package io.helidon.webserver.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.webserver.TlsManager;

/**
 * {@link java.util.ServiceLoader} service provider for {@link TlsManager}.
 */
public interface TlsManagerProvider {

    /**
     * Key this service implementation is stored under. This is also considered the service "type" when used
     * in a list in configuration, to allow the same service defined more than once.
     *
     * @return key of this implementation
     */
    String configKey();

    /**
     * Create a new instance from the configuration located
     * on the provided node.
     *
     * @param config located at {@link #configKey()} node
     * @param name name of the configured implementation
     *
     * @return a new instance created from this config node
     */
    TlsManager create(Config config, String name);

    /**
     * Takes a configuration and looks for a suitable {@link TlsManager} instance based upon that configuration.
     *
     * @param config the configuration
     * @return a TLS manager instance
     * @throws IllegalStateException if the configuration is invalid or if the named provider is not found
     */
    static TlsManager create(Config config) {
        List<Config> namedManagers = config.asNodeList().orElse(List.of());
        if (namedManagers.size() != 1) {
            throw new IllegalStateException("Expected to have one manager defined for config: '" + config.name()
                                                    + "'; but instead found: " + namedManagers.size());
        }

        String theConfigKey = namedManagers.get(0).key().name();
        Map<String, TlsManagerProvider> providerMap = availableProviders();
        TlsManagerProvider provider = providerMap.get(theConfigKey);
        if (provider == null) {
            throw new IllegalStateException("Expected to find a provider named '" + theConfigKey
                                                    + "' but did not find it in: " + providerMap.keySet());
        }
        return provider.create(config.get(theConfigKey), theConfigKey);
    }

    private static Map<String, TlsManagerProvider> availableProviders() {
        HelidonServiceLoader<TlsManagerProvider> loader =
                HelidonServiceLoader.create(ServiceLoader.load(TlsManagerProvider.class));
        Map<String, TlsManagerProvider> providers = new LinkedHashMap<>();
        loader.forEach(provider -> {
            String configKey = provider.configKey();
            if (null != configKey) {
                providers.put(configKey, provider);
            }
        });
        return providers;
    }

}
