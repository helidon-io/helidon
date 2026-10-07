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

package io.helidon.config.overrides;

import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.service.registry.Service;

/**
 * Automatically discovered provider of immutable overrides filters. This service has no per-runtime state and can be
 * shared. Each call to {@link #create(Config)} captures {@code overrides.expressions} and creates independent sources
 * from the standard source descriptors under {@code overrides.sources}. Those settings remain fixed for that runtime;
 * the contents of its definition sources can change.
 * <p>
 * For programmatic setup use {@code OverrideConfigFilter.builder().buildProvider()} and register that provider with
 * {@link io.helidon.config.Config.Builder#addFilterProvider(io.helidon.config.spi.ConfigFilterProvider)}.
 */
@Service.Singleton
public final class OverrideConfigFilterProvider implements ConfigFilterProvider {
    /**
     * Constructor for Java service loading and the service registry.
     */
    public OverrideConfigFilterProvider() {
    }

    @Override
    public ConfigFilterFactory create(Config initialConfig) {
        Objects.requireNonNull(initialConfig);
        var builder = OverrideConfigFilter.builder().config(initialConfig.get(OverrideConfigFilter.CONFIG_KEY));
        // Resolve descriptors separately for every runtime, rather than sharing source and monitoring instances.
        initialConfig.get("overrides.sources")
                .asNodeList()
                .ifPresent(sources -> sources.forEach(descriptor ->
                                                              MetaConfig.configSource(descriptor)
                                                                      .forEach(builder::addConfigSource)));
        return OverrideFilterFactory.create(builder.buildPrototype());
    }
}
