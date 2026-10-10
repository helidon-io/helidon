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
package io.helidon.config.spi;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;

/**
 * Creates an independent filter factory for each built configuration runtime.
 * Providers are discovered using {@link java.util.ServiceLoader} or the service registry, and may be shared
 * between configuration runtimes. Mutable state belonging to a configuration runtime must be kept in its factory.
 * A shared provider may receive concurrent {@link #create(Config)} calls and must support that use.
 * Provider filters run after legacy override sources and before filters registered through the legacy {@code addFilter}
 * methods, including value resolution.
 *
 * @see io.helidon.config.Config.Builder#addFilterProvider(ConfigFilterProvider)
 */
@FunctionalInterface
@Service.Contract
public interface ConfigFilterProvider {
    /**
     * Creates a factory, invoked once for an independently built configuration runtime.
     * The supplied configuration contains merged source values before value filtering. Navigation, detachment,
     * and context access remain within this view; context reload and change support operations are inert.
     * Lazy source values may still be read from their source, so capture required values during creation.
     * If creation fails before returning a factory, the provider must release any resources it allocated.
     *
     * @param initialConfig unfiltered initial source configuration
     * @return a new factory owned by this configuration runtime
     */
    ConfigFilterFactory create(Config initialConfig);
}
