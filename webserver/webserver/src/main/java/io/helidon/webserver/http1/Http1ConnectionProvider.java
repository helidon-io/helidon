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

package io.helidon.webserver.http1;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.webserver.http1.spi.Http1Upgrader;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;

/**
 * {@link io.helidon.webserver.spi.ServerConnectionSelectorProvider} implementation for HTTP/1.1 server connection provider.
 */
public class Http1ConnectionProvider implements ServerConnectionSelectorProvider<Http1Config> {

    /**
     * HTTP/1.1 server connection provider configuration node name.
     */
    static final String CONFIG_NAME = "http_1_1";

    /**
     * Create a new instance with default configuration.
     * To customize instance programmatically, use {@link Http1ConnectionSelector}
     * instead.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http1ConnectionProvider() {
    }

    @Override
    public Class<Http1Config> protocolConfigType() {
        return Http1Config.class;
    }

    @Override
    public String protocolType() {
        return CONFIG_NAME;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public ServerConnectionSelector create(String socketName, Http1Config config, ProtocolConfigs configs) {
        Http1ConnectionSelectorConfig.Builder builder = Http1ConnectionSelector.builder()
                .config(config);

        List<Http1UpgradeProvider> providers = HelidonServiceLoader.create(
                        ServiceLoader.load(Http1UpgradeProvider.class))
                .asList();

        var upgraders = new LinkedHashMap<String, Http1Upgrader>();
        for (Http1UpgradeProvider upgradeProvider : providers) {
            List<ProtocolConfig> upgradeConfigs = configs.config(upgradeProvider.protocolType(),
                                                                 upgradeProvider.protocolConfigType());
            for (ProtocolConfig upgradeConfig : upgradeConfigs) {
                // do not overwrite the same protocol with lower weight types
                Http1Upgrader http1Upgrader = upgradeProvider.create(upgradeConfig, configs);
                upgraders.putIfAbsent(http1Upgrader.supportedProtocol(), http1Upgrader);
            }
        }

        // now create an upgrader for each upgrade provider
        return builder
                .addUpgraders(upgraders)
                .build();
    }
}
