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

import java.util.List;

/**
 * Access to discovered service metadata.
 */
public interface ServiceDiscovery {
    /**
     * A file that contains all service provider interfaces that expose a service for
     * Java {@link java.util.ServiceLoader} that is used to tell Helidon Service Registry to add implementations to
     * the registry to be discoverable in addition to services defined in
     * {@link io.helidon.service.metadata.Descriptors#SERVICE_REGISTRY_LOCATION}.
     */
    String SERVICES_LOADER_RESOURCE = "META-INF/helidon/service.loader";

    /**
     * Create a new instance that discovers service descriptors from classpath.
     *
     * @return service discovery based on classpath
     */
    static ServiceDiscovery create() {
        return create(ServiceRegistryConfig.create());
    }

    /**
     * Create a new instance that discovers service descriptors based on the configuration.
     *
     * @param config registry configuration to control discovery
     * @return service discovery based on classpath
     */
    static ServiceDiscovery create(ServiceRegistryConfig config) {
        return CoreServiceDiscovery.create(config);
    }

    /**
     * Service discovery that does not discover anything.
     *
     * @return a no-op service discovery
     */
    static ServiceDiscovery noop() {
        return CoreServiceDiscovery.noop();
    }

    /**
     * All discovered metadata of this service discovery.
     *
     * @return all discovered metadata
     */
    List<DescriptorHandler> allMetadata();
}
