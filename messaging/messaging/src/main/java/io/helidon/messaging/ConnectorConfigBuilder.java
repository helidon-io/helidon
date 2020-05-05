/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging;

import java.util.HashMap;
import java.util.Map;

import io.helidon.common.Builder;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * Detached configuration of a single connector.
 */
public class ConnectorConfigBuilder implements Builder<Config> {
    private final Map<String, String> configuration = new HashMap<>();

    protected ConnectorConfigBuilder() {
    }

    static ConnectorConfigBuilder from(Config config) {
        ConnectorConfigBuilder result = new ConnectorConfigBuilder();
        result.putAll(config);
        return result;
    }

    protected ConnectorConfigBuilder put(String key, String value) {
        configuration.put(key, value);
        return this;
    }

    ConnectorConfigBuilder putAll(Config configToPut) {
        configuration.putAll(configToPut.detach().asMap().orElse(Map.of()));
        return this;
    }

    @Override
    public Config build() {
        Config newConfig = Config.builder(ConfigSources.create(configuration))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .disableSourceServices()
                .disableParserServices()
                .build();
        return newConfig;
    }
}
