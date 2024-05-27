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

package io.helidon.service.registry.spi;

import java.util.function.Supplier;

import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

/**
 * A {@link java.util.ServiceLoader} provider that enables a different type of service registry.
 * In Helidon this could be a service registry with full injection support.
 */
public interface ServiceRegistryManagerProvider {
    /**
     * Create a new registry manager.
     *
     * @param config              configuration as provided to {@link io.helidon.service.registry.ServiceRegistryManager}
     * @param serviceDiscovery    service discovery to load service instances
     * @param coreRegistryManager core service registry manager, if it would be used as a backing one for the one provided by this
     *                            service (lazy loaded)
     * @return a new service registry manager
     */
    ServiceRegistryManager create(ServiceRegistryConfig config,
                                  ServiceDiscovery serviceDiscovery,
                                  Supplier<ServiceRegistryManager> coreRegistryManager);
}
