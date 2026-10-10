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
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.service.registry.Service;

/**
 * Discovers override filter settings under {@code overrides} in the initial application configuration.
 * This stateless service creates a configured provider for each configuration runtime. For manual configuration use
 * {@link OverrideConfigFilterProvider#builder()}.
 */
@Service.Singleton
public final class OverrideConfigFilterService implements ConfigFilterProvider {
    /**
     * Constructor required by Java service loading and the service registry.
     */
    public OverrideConfigFilterService() {
    }

    @Override
    public ConfigFilterFactory create(Config initialConfig) {
        Objects.requireNonNull(initialConfig);
        return OverrideConfigFilterProvider.builder()
                .config(initialConfig.get("overrides"))
                .build()
                .create(initialConfig);
    }
}
