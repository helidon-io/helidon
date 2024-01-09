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

package io.helidon.inject;

import java.util.Map;
import java.util.Objects;

import io.helidon.common.types.TypeName;

/**
 * A factory for service registry. The global services can be accessed through {@link #instance()}.
 * <p>
 * Always use {@link #create()} or {@link #create(InjectionConfig)} for testing, to prevent lock-in of
 * the global registry.
 */
public interface InjectionServices {
    /**
     * Configure the bootstrap of injection. This method must be called before obtaining an instance
     * of {@link io.helidon.inject.Services}, otherwise it will be ignored.
     *
     * @param config injection configuration
     */
    static void configure(InjectionConfig config) {
        Objects.requireNonNull(config);
        InjectionServicesImpl.configure(config);
    }

    /**
     * The singleton instance of injection services.
     *
     * @return injection services
     */
    static InjectionServices instance() {
        return InjectionServicesImpl.instance();
    }

    /**
     * Create injection services for a specific configuration.
     * The instance obtained will not interact with {@link #instance()} and will provide a stand-alone service registry.
     *
     * @param injectionConfig configuration of the services
     * @return a new injection services instance
     */
    static InjectionServices create(InjectionConfig injectionConfig) {
        return InjectionServicesImpl.create(injectionConfig);
    }

    /**
     * Create injection services with default configuration.
     * The instance obtained will not interact with {@link #instance()} and will provide a stand-alone service registry.
     *
     * @return a new injection services instance
     */
    static InjectionServices create() {
        return create(InjectionConfig.create());
    }

    /**
     * The service registry.
     *
     * @return the services registry
     */
    Services services();

    /**
     * The governing configuration.
     *
     * @return the config
     */
    InjectionConfig config();

    /**
     * Attempts to perform a graceful {@link ManagedService#deactivate(DeActivationRequest)} on all managed
     * service instances in the {@link Services} registry.
     * Deactivation is handled within the current thread.
     * <p>
     * This method will return a map of all service types that were deactivated to
     * its deactivation result.
     * <p>
     * The deactivation will be in reverse
     * order of {@link io.helidon.inject.service.Injection.RunLevel} from the highest value down to the lowest value.
     * If two services share the same {@link io.helidon.inject.service.Injection.RunLevel} value then the ordering will be
     * based on service {@link io.helidon.common.Weight} (in ascending order), and then on service name.
     * <p>
     * When shutdown returns, it is guaranteed that all services were shutdown, or failed to achieve shutdown.
     *
     * @return a map of all managed service types deactivated to results of deactivation, or empty if shutdown is not supported;
     *         note that the response will only contain services that were activated and that have a
     *         {@link io.helidon.inject.service.Injection.Singleton} scope
     */
    Map<TypeName, ActivationResult> shutdown();
}
