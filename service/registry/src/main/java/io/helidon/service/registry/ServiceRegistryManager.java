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

package io.helidon.service.registry;

/**
 * Manager is responsible for managing the state of a {@link io.helidon.service.registry.ServiceRegistry}.
 * Each manager instances owns a single service registry.
 * <p>
 * To use a singleton service across application, either pass it through parameters, or use
 * {@link io.helidon.service.registry.GlobalServiceRegistry}.
 */
public interface ServiceRegistryManager {
    /**
     * Create a new service registry manager with default configuration.
     *
     * @return a new service registry manager
     */
    static ServiceRegistryManager create() {
        return create(ServiceRegistryConfig.create());
    }

    /**
     * Create a new service registry manager with custom configuration.
     *
     * @param config configuration of this registry manager
     * @return a new configured service registry manager
     */
    static ServiceRegistryManager create(ServiceRegistryConfig config) {
        return ServiceRegistryManagerDiscovery.create(config);
    }

    /**
     * Get (or initialize and get) the service registry managed by this manager.
     *
     * @return service registry ready to be used
     */
    ServiceRegistry registry();

    /**
     * Shutdown the managed service registry.
     */
    void shutdown();
}
