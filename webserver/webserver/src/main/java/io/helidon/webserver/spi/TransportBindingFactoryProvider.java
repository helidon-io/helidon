/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfiguredProvider;

/**
 * Provider of configured transport binding factories.
 */
@Api.Internal
public interface TransportBindingFactoryProvider extends ConfiguredProvider<TransportBindingFactory> {
    /**
     * Create a transport binding factory from provider-specific configuration.
     *
     * @param config provider-specific configuration
     * @return configured transport binding factory
     */
    TransportBindingFactory create(Config config);

    @Override
    default TransportBindingFactory create(Config config, String name) {
        String providerKey = Objects.requireNonNull(configKey(), "Transport binding provider key must not be null");
        if (!providerKey.equals(name)) {
            throw new ConfigException("Transport binding provider key \"" + providerKey
                                              + "\" does not match configured binding type \"" + name + "\"");
        }
        TransportBindingFactory factory = Objects.requireNonNull(create(config),
                                                                 "Transport binding provider returned null factory");
        String factoryType = Objects.requireNonNull(factory.type(), "Transport binding factory type must not be null");
        if (!providerKey.equals(factoryType)) {
            throw new ConfigException("Transport binding provider key \"" + providerKey
                                              + "\" does not match factory type \"" + factoryType + "\"");
        }
        return factory;
    }
}
