/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

import io.helidon.common.Weighted;

/**
 * Abstract factory for all services provided by a single Helidon Pico provider implementation.
 * An implementation of this interface must minimally supply a "services registry" - see {@link #services()}.
 */
public interface PicoServices extends Weighted {

    /**
     * Get {@link PicoServices} instance if available. The highest {@link io.helidon.common.Weighted} service will be loaded
     * and returned.
     *
     * @return Pico services instance
     */
    static Optional<PicoServices> picoServices() {
        return PicoServicesHolder.picoServices();
    }

    /**
     * The service registry.
     *
     * @return the services registry
     */
    Services services();

    /**
     * Creates a service binder instance for a specified module.
     *
     * @param module the module to offer binding to dynamically, and typically only at early startup initialization
     *
     * @return the service binder capable of binding, or empty if not permitted/available
     */
    default Optional<ServiceBinder> createServiceBinder(Module module) {
        return Optional.empty();
    }

    /**
     * Optionally, the injector.
     *
     * @return the injector, or empty if not available
     */
    default Optional<? extends Injector> injector() {
        return Optional.empty();
    }

    /**
     * Optionally, the service providers' configuration.
     *
     * @return the config, or empty if not available
     */
    default Optional<? extends PicoServicesConfig> config() {
        return Optional.empty();
    }

    /**
     * Attempts to perform a graceful {@link io.helidon.pico.spi.Injector#deactivate(Object, java.util.Optional)} on all managed
     * service instances in the {@link io.helidon.pico.spi.Services} registry. Since deactivation can take some time to
     * complete, a future is returned that can be used for tracking purposes. A dedicated thread is started to manage the
     * deactivation/shutdown procedure for all active services in the registry.
     * <p>
     * If the service provider does not support shutdown an empty is returned.
     * <p>
     * The default reference implementation for Pico will return a map of all service types that were deactivated to any
     * throwable that was observed during that services shutdown sequence.
     * <p>
     * The order in which services are deactivated is dependent upon whether the {@link #activationLog()} is available.
     * If the activation log is available, then services will be shutdown in reverse chronological order as how they
     * were started. If the activation log is not enabled or found to be empty then the deactivation will be in reverse
     * order of {@link io.helidon.pico.api.RunLevel} from the highest value down to the lowest value. If two services share
     * the same {@link io.helidon.pico.api.RunLevel} value then the ordering will be based upon the implementation's comparator.
     * <p>
     * Note that the service registry is NOT prevented from usage during or after shutdown. This means that it is possible
     * for services to still be in an active state in the service registry even after shutdown is completed. If this is
     * of concern then the recommendation is for the caller to repeatedly call shutdown() until the map contents are empty.
     *
     * @return a future of a map of all managed service types deactivated to any throwable observed during deactivation
     */
    default Optional<Future<Map<String, ActivationResult<?>>>> shutdown() {
        return Optional.empty();
    }

    /**
     * Optionally, the service provider activation log.
     *
     * @return the injector, or empty if not available
     */
    default Optional<ActivationLog> activationLog() {
        return Optional.empty();
    }

}
