/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.config;

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;

/**
 * {@link java.util.ServiceLoader} provider implementation for config observe provider.
 */
@Service.Singleton
public class ConfigObserveProvider implements ObserveProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public ConfigObserveProvider() {
    }

    @Override
    public String configKey() {
        return "config";
    }

    @Override
    public Observer create(Config config, String name) {
        return ConfigObserver.builder()
                .config(config)
                .name(name)
                .build();
    }

    @Override
    public Observer create(Config config, String name, ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);
        Objects.requireNonNull(serviceRegistry);
        var observerConfig = ConfigObserver.builder()
                .config(config)
                .name(name)
                .buildPrototype();
        return ConfigObserver.create(observerConfig, serviceRegistry.supply(Config.class));
    }
}
