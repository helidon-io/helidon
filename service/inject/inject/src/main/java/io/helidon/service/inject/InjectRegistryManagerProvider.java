/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject;

import java.util.function.Supplier;

import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.spi.ServiceRegistryManagerProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for
 * {@link io.helidon.service.registry.spi.ServiceRegistryManagerProvider} to provide a service registry
 * with injection and interception support.
 */
public class InjectRegistryManagerProvider implements ServiceRegistryManagerProvider {
    /**
     * Required public constructor.
     *
     * @deprecated required for Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public InjectRegistryManagerProvider() {
    }

    @Override
    public ServiceRegistryManager create(ServiceRegistryConfig config,
                                         ServiceDiscovery serviceDiscovery,
                                         Supplier<ServiceRegistryManager> coreRegistryManager) {
        InjectConfig injectConfig;
        if (config instanceof InjectConfig ic) {
            injectConfig = ic;
        } else {
            injectConfig = InjectConfig.builder()
                    // we need to add appropriate configured options from config (if present)
                    .update(it -> config.config().ifPresent(it::config))
                    .from(config)
                    .build();
        }
        return new InjectRegistryManager(injectConfig, serviceDiscovery);
    }
}
