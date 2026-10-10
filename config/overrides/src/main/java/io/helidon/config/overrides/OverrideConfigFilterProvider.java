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
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigFilterProvider;

/**
 * Provider of immutable override filters, configured using a generated builder. The provider retains its immutable
 * configuration and can be shared. Each call to {@link #create(Config)} creates independent definition sources and
 * monitoring resources, shared only by the filter generations of that configuration runtime.
 * <p>
 * For programmatic setup, register {@code builder().build()} with
 * {@link io.helidon.config.Config.Builder#addFilterProvider(io.helidon.config.spi.ConfigFilterProvider)}. The builder's
 * {@code config(Config)} method accepts the component node containing {@code expressions}, {@code patterns}, and
 * {@code sources}. Automatic discovery reads that node from {@code overrides} in the initial application configuration.
 */
public final class OverrideConfigFilterProvider implements ConfigFilterProvider, RuntimeType.Api<OverrideConfig> {
    private final OverrideConfig config;

    private OverrideConfigFilterProvider(OverrideConfig config) {
        this.config = config;
    }

    /**
     * Create a provider from its immutable configuration.
     *
     * @param config provider configuration
     * @return provider
     */
    public static OverrideConfigFilterProvider create(OverrideConfig config) {
        return new OverrideConfigFilterProvider(Objects.requireNonNull(config));
    }

    /**
     * Create a provider by customizing its generated builder.
     *
     * @param consumer builder customization
     * @return provider
     */
    public static OverrideConfigFilterProvider create(Consumer<OverrideConfig.Builder> consumer) {
        return builder().update(Objects.requireNonNull(consumer)).build();
    }

    /**
     * Create a builder for an override filter provider.
     *
     * @return provider builder
     */
    public static OverrideConfig.Builder builder() {
        return OverrideConfig.builder();
    }

    /**
     * Create the definition machinery for one configuration runtime. Settings come from this provider's prototype;
     * they are not reread from the supplied target configuration.
     *
     * @param initialConfig initial unfiltered target configuration
     * @return per-runtime factory creating immutable filters
     */
    @Override
    public ConfigFilterFactory create(Config initialConfig) {
        Objects.requireNonNull(initialConfig);
        return OverrideFilterFactory.create(config);
    }

    @Override
    public OverrideConfig prototype() {
        return config;
    }
}
