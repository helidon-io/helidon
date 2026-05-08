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

package io.helidon.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10) // less than default, so it can be easily overridden
class ConfigProvider implements Supplier<Config> {
    private final Config config;

    @Service.Inject
    ConfigProvider(Supplier<Optional<MetaConfig>> metaConfigSupplier,
                   Supplier<List<ConfigSource>> configSources,
                   Supplier<List<ConfigParser>> configParsers,
                   Supplier<List<ConfigFilter>> configFilters,
                   Supplier<List<ConfigMapperProvider>> configMappers) {
        Optional<MetaConfig> metaConfig = metaConfigSupplier.get();
        Config.Builder builder = Config.builder();

        if (metaConfig.isPresent()) {
            builder.config(metaConfig.get().metaConfiguration());
        } else {
            builder.update(it -> configSources.get()
                    .forEach(it::addSource))
                    .update(it -> defaultConfigSources(it, configParsers));
        }
        builder.disableParserServices()
                .update(it -> configParsers.get()
                        .forEach(it::addParser))
                .disableFilterServices()
                .update(it -> configFilters.get()
                        .forEach(it::addFilter))
                //.disableMapperServices()
                // cannot do this for now, removed ConfigMapperProvider from service loaded services, config does it on its
                // own
                // ObjectConfigMapper is before EnumMapper, and both are before essential and built-in
                .update(it -> configMappers.get()
                        .forEach(it::addMapper));

        this.config = builder.build();
    }

    @Override
    public Config get() {
        return config;
    }

    private void defaultConfigSources(io.helidon.config.Config.Builder configBuilder,
                                      Supplier<List<ConfigParser>> configParsers) {

        Set<String> supportedSuffixes = configParsers.get()
                .stream()
                .map(ConfigParser::supportedSuffixes)
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        // profile source(s) before defaults
        MetaConfigFinder.profile()
                .ifPresent(profile -> MetaConfigFinder.configSources(new ArrayList<>(supportedSuffixes),
                                                                     profile)
                        .forEach(configBuilder::addSource));

        // default config source(s)
        MetaConfigFinder.configSources(new ArrayList<>(supportedSuffixes))
                .forEach(configBuilder::addSource);
    }
}
