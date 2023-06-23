/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.spi.ConfigSource;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceProvider;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class ConfigProducer implements Provider<Config> {
    private final Config config;

    @Inject
    ConfigProducer() {
        // this should be moved to constructor injection when we support zero length lists for injection
        List<ServiceProvider<?>> serviceProviders = PicoServices.realizedServices()
                .lookupAll(ServiceInfoCriteria
                                   .builder()
                                   .addContractImplemented(ConfigSource.class).build(), false);

        if (GlobalConfig.configured()) {
            config = GlobalConfig.config();
        } else {
            config = io.helidon.config.Config.builder()
                    .metaConfig()
                    .update(it -> serviceProviders.stream()
                            .map(Provider::get)
                            .map(ConfigSource.class::cast)
                            .forEach(it::addSource))
                    .build();
        }
    }

    @Override
    public Config get() {
        return config;
    }
}
