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
 *
 */

package io.helidon.microprofile.messaging;

import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/**
 * Detached configuration of a single connector.
 */
class AdHocConfigBuilder {
    private final Map<String, String> configuration = new HashMap<>();

    private AdHocConfigBuilder() {
    }

    static AdHocConfigBuilder from(Config config) {
        AdHocConfigBuilder result = new AdHocConfigBuilder();
        result.putAll(config);
        return result;
    }

    AdHocConfigBuilder put(String key, String value) {
        configuration.put(key, value);
        return this;
    }

    AdHocConfigBuilder putAll(Config configToPut) {
        configuration.putAll(configToPut.detach().asMap().orElse(Map.of()));
        return this;
    }

    org.eclipse.microprofile.config.Config build() {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(configuration))
                .build();
    }
}
