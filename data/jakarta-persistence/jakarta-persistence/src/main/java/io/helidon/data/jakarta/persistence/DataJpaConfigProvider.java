/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.jakarta.persistence;

import io.helidon.common.config.Config;
import io.helidon.data.ProviderConfig;
import io.helidon.data.spi.ProviderConfigProvider;

/**
 * Implementation of a service provider interface to create Jakarta provider configuration.
 * This type is loaded through {@link java.util.ServiceLoader}.
 *
 * @see io.helidon.data.DataConfig.Builder#provider(io.helidon.data.ProviderConfig)
 */
public class DataJpaConfigProvider implements ProviderConfigProvider {
    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     */
    public DataJpaConfigProvider() {
    }

    @Override
    public String configKey() {
        return DataJpaSupportProviderService.PROVIDER_TYPE;
    }

    @Override
    public ProviderConfig create(Config config, String name) {
        return DataJpaConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}
