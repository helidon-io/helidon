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

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

/**
 * Common ancestor for generated main classes.
 */
public class ApplicationMain {
    /**
     * A new instance should never be created.
     */
    protected ApplicationMain() {
    }

    /**
     * Create a config builder from command line arguments.
     * This method will create default configuration (but not register it as a global config, so we still use the registry
     * to create a config instance), configure the {@link io.helidon.service.inject.InjectConfig.Builder} from it,
     * then disable discovery of services.
     *
     * @param arguments command line arguments
     * @return a new config builder
     */
    protected static InjectConfig.Builder configBuilder(String[] arguments) {
        boolean configured = GlobalConfig.configured();
        Config config = GlobalConfig.config();
        if (!configured) {
            // reset to empty
            GlobalConfig.config(() -> null, true);
        }
        return InjectConfig.builder()
                .config(config.get("registry"))
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false);
    }

    /**
     * Create a service descriptor for a Java {@link java.util.ServiceLoader} based service.
     *
     * @param providerInterface provider interface of the service
     * @param providerImpl      provider implementation
     * @param weight            weight assigned to the service
     * @return a new service descriptor of the service
     */
    protected static GeneratedService.Descriptor<?> serviceLoader(TypeName providerInterface,
                                                                  TypeName providerImpl,
                                                                  double weight) {
        return ServiceLoader__ServiceDescriptor.create(providerInterface, providerImpl, weight);
    }

    /**
     * Initialize the service registry, register it for shutdown during process shutdown, and lookup all
     * startup services.
     *
     * @param config configuration of the Inject service registry
     */
    protected static void init(InjectConfig config) {
        InjectRegistryManager manager = InjectRegistryManager.create(config);
        InjectRegistry registry = manager.registry();
        InjectStartupProvider.registerShutdownHandler(manager);
        registry.all(Lookup.builder()
                             .runLevel(Injection.RunLevel.STARTUP)
                             .build());
    }
}
