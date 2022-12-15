/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.wls;

import java.util.HashMap;
import java.util.Map;

import io.helidon.config.ConfigSources;
import io.helidon.config.mp.MpConfig;
import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

class WlsConnectorConfigAliases {

    private WlsConnectorConfigAliases() {
    }

    private static final Map<String, String> ALIASES = Map.of(
            WeblogicConnector.JMS_FACTORY_ATTRIBUTE, "jndi.jms-factory",
            "url", "jndi.env-properties.java.naming.provider.url",
            "principal", "jndi.env-properties.java.naming.security.principal",
            "credentials", "jndi.env-properties.java.naming.security.credentials"
    );

    static Config map(Config connConfig) {
        Map<String, String> mapped = new HashMap<>();

        mapped.put("jndi.env-properties.java.naming.factory.initial", IsolatedContextFactory.class.getName());

        ALIASES.forEach((key, value) -> connConfig.getOptionalValue(key, String.class)
                .ifPresent(s -> mapped.put(value, s)));

        io.helidon.config.Config cfg = io.helidon.config.Config.builder()
                .addSource(ConfigSources.create(MpConfig.toHelidonConfig(connConfig)))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableCaching()
                .disableValueResolving()
                .build();

        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(mapped), MpConfigSources.create(cfg))
                .build();
    }
}
