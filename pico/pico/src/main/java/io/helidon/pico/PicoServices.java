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

package io.helidon.pico;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract factory for all services provided by a single Helidon Pico provider implementation.
 * An implementation of this interface must minimally supply a "services registry" - see {@link #services()}.
 * <p>
 * The global singleton instance is accessed via {@link #picoServices()}. Note that optionally one can provide a
 * primordial bootstrap configuration to the {@code Pico} services provider. One must establish any bootstrap instance
 * prior to the first call to {@link #picoServices()} as it will use a default configuration if not explicitly set. Once
 * the bootstrap has been set it cannot be changed for the lifespan of the JVM.
 */
public interface PicoServices {

    /**
     * Empty criteria will match anything and everything.
     */
    ServiceInfoCriteria EMPTY_CRITERIA = DefaultServiceInfoCriteria.builder().build();

    /**
     * Denotes a match to any (default) service, but required to be matched to at least one.
     */
    ContextualServiceQuery SERVICE_QUERY_REQUIRED = DefaultContextualServiceQuery.builder()
            .serviceInfoCriteria(EMPTY_CRITERIA)
            .expected(true)
            .build();

    /**
     * Returns the {@link io.helidon.pico.Bootstrap} configuration instance that was used to initialize this instance.
     *
     * @return the bootstrap configuration instance
     */
    Bootstrap bootstrap();

    /**
     * Retrieves any primordial bootstrap configuration that previously set.
     *
     * @return the bootstrap primordial configuration already assigned
     * @see #globalBootstrap(Bootstrap)
     */
    static Optional<Bootstrap> globalBootstrap() {
        return PicoServicesHolder.bootstrap(false);
    }

    /**
     * First attempts to locate and return the {@link #globalBootstrap()} and if not found will create a new bootstrap instance.
     *
     * @return a bootstrap
     */
    static Bootstrap realizedGlobalBootStrap() {
        Optional<Bootstrap> bootstrap = globalBootstrap();
        return bootstrap.orElseGet(() -> PicoServicesHolder.bootstrap(true).orElseThrow());
    }

    /**
     * Sets the primordial bootstrap configuration that will supply {@link #picoServices()} during global
     * singleton initialization.
     *
     * @param bootstrap the primordial global bootstrap configuration
     * @see #globalBootstrap()
     */
    static void globalBootstrap(Bootstrap bootstrap) {
        PicoServicesHolder.bootstrap(bootstrap);
    }

    /**
     * Get {@link PicoServices} instance if available. The highest {@link io.helidon.common.Weighted} service will be loaded
     * and returned. Remember to optionally configure any primordial {@link Bootstrap} configuration prior to the
     * first call to get {@code PicoServices}.
     *
     * @return the Pico services instance
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
     * The governing configuration.
     *
     * @return the config
     */
    PicoServicesConfig config();

    /**
     * Creates a service binder instance for a specified module.
     *
     * @param module the module to offer binding to dynamically, and typically only at early startup initialization
     *
     * @return the service binder capable of binding, or empty if not permitted/available
     */
    Optional<ServiceBinder> createServiceBinder(Module module);

    /**
     * Optionally, the injector.
     *
     * @return the injector, or empty if not available
     */
    Optional<Injector> injector();

    /**
     * Attempts to perform a graceful {@link Injector#deactivate(Object, InjectorOptions)} on all managed
     * service instances in the {@link Services} registry.
     * Deactivation is handled within the current thread.
     * <p>
     * If the service provider does not support shutdown an empty is returned.
     * <p>
     * The default reference implementation for Pico will return a map of all service types that were deactivated to any
     * throwable that was observed during that services shutdown sequence.
     * <p>
     * The order in which services are deactivated is dependent upon whether the {@link #activationLog()} is available.
     * If the activation log is available, then services will be shutdown in reverse chronological order as how they
     * were started. If the activation log is not enabled or found to be empty then the deactivation will be in reverse
     * order of {@link RunLevel} from the highest value down to the lowest value. If two services share
     * the same {@link RunLevel} value then the ordering will be based upon the implementation's comparator.
     * <p>
     * When shutdown returns, it is guaranteed that all services were shutdown, or failed to achieve shutdown.
     *
     * @return a map of all managed service types deactivated to results of deactivation, or empty if shutdown is not supported
     */
    Optional<Map<String, ActivationResult>> shutdown();

    /**
     * Optionally, the service provider activation log.
     *
     * @return the injector, or empty if not available
     */
    Optional<ActivationLog> activationLog();

    /**
     * Optionally, the metrics that are exposed by the provider implementation.
     *
     * @return the metrics, or empty if not available
     */
    Optional<Metrics> metrics();

    /**
     * Optionally, the set of {@link io.helidon.pico.Services} lookup criteria that were recorded. This is only available if
     * {@link PicoServicesConfig#serviceLookupCaching()} is enabled.
     *
     * @return the lookup criteria recorded, or empty if not available
     */
    Optional<Set<ServiceInfoCriteria>> lookups();

    /**
     * Will create an activation request either to {@link io.helidon.pico.Phase#ACTIVE} or limited to any
     * {@link io.helidon.pico.Bootstrap#limitRuntimePhase()} specified.
     *
     * @return the activation request
     */
    static ActivationRequest createDefaultActivationRequest() {
        return DefaultActivationRequest.builder().targetPhase(terminalActivationPhase()).build();
    }

    /**
     * The terminal phase for activation that we should not cross.
     *
     * @return the terminal phase for activation
     */
    static Phase terminalActivationPhase() {
        Optional<Bootstrap> globalBootstrap = PicoServices.globalBootstrap();
        if (globalBootstrap.isPresent()) {
            Optional<Phase> limitPhase = globalBootstrap.get().limitRuntimePhase();
            return limitPhase.orElse(Phase.ACTIVE);
        }
        return Phase.ACTIVE;
    }

}
