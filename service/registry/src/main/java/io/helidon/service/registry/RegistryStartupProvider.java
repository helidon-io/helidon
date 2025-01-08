/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import io.helidon.Main;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.spi.HelidonShutdownHandler;
import io.helidon.spi.HelidonStartupProvider;

/**
 * {@link java.util.ServiceLoader} implementation of a Helidon startup provider for Helidon Service Registry based
 * applications.
 */
@Weight(Weighted.DEFAULT_WEIGHT) // explicit default weight, this should be the "default" startup class
public class RegistryStartupProvider implements HelidonStartupProvider {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly
     */
    @Deprecated
    public RegistryStartupProvider() {
    }

    /**
     * Register a shutdown handler.
     * The handler is registered, and never de-registered. This method should only be used by main classes of applications.
     * If a custom shutdown is desired, please use
     * {@link io.helidon.Main#addShutdownHandler(io.helidon.spi.HelidonShutdownHandler)} directly.
     *
     * @param registryManager registry manager
     */
    public static void registerShutdownHandler(ServiceRegistryManager registryManager) {
        System.Logger logger = System.getLogger(RegistryStartupProvider.class.getName());
        Main.addShutdownHandler(new RegistryShutdownHandler(logger, registryManager));
    }

    @Override
    public void start(String[] arguments) {
        ServiceRegistryManager.start();
    }

    // higher than default, so we stop server as a service, not through shutdown
    @Weight(Weighted.DEFAULT_WEIGHT + 10)
    private static final class RegistryShutdownHandler implements HelidonShutdownHandler {
        private final System.Logger logger;
        private final ServiceRegistryManager registryManager;

        private RegistryShutdownHandler(System.Logger logger, ServiceRegistryManager registryManager) {
            this.logger = logger;
            this.registryManager = registryManager;
        }

        @Override
        public void shutdown() {
            try {
                registryManager.shutdown();
            } catch (Exception e) {
                logger.log(System.Logger.Level.ERROR,
                           "Failed to shutdown Helidon Service Registry registry",
                           e);
            }
        }

        @Override
        public String toString() {
            return "Helidon Service Registry shutdown handler";
        }
    }
}
