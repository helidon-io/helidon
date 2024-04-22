/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Abstract factory for all services provided by a single Helidon Injection provider implementation.
 * An implementation of this interface must minimally supply a "services registry" - see {@link #services()}.
 * <p>
 * The global singleton instance is accessed via {@link #injectionServices()}. Note that optionally one can provide a
 * primordial bootstrap configuration to the {@code Injection} services provider. One must establish any bootstrap instance
 * prior to the first call to {@link #injectionServices()} as it will use a default configuration if not explicitly set. Once
 * the bootstrap has been set it cannot be changed for the lifespan of the JVM.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface InjectionServices {
    /**
     * Tag for putting Injection tooling, processing, and runtime into debug mode.
     * @see InjectionServices#config()
     * @see InjectionServicesConfig#shouldDebug()
     */
    String TAG_DEBUG = "inject.debug";


    /**
     * Empty criteria will match anything and everything.
     */
    ServiceInfoCriteria EMPTY_CRITERIA = ServiceInfoCriteria.builder().build();

    /**
     * Denotes a match to any (default) service, but required to be matched to at least one.
     */
    ContextualServiceQuery SERVICE_QUERY_REQUIRED = ContextualServiceQuery.builder()
            .serviceInfoCriteria(EMPTY_CRITERIA)
            .expected(true)
            .build();

    /**
     * Whether debug is enabled.
     *
     * @return whether to debug
     */
    static boolean isDebugEnabled() {
        return injectionServices()
                .map(InjectionServices::config)
                .map(InjectionServicesConfig::shouldDebug)
                .orElseGet(() -> Boolean.getBoolean(InjectionServices.TAG_DEBUG));
    }

    /**
     * Returns the {@link Bootstrap} configuration instance that was used to initialize this instance.
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
        return InjectionServicesHolder.bootstrap(false);
    }

    /**
     * First attempts to locate and return the {@link #globalBootstrap()} and if not found will create a new bootstrap instance.
     *
     * @return a bootstrap
     */
    static Bootstrap realizedGlobalBootStrap() {
        Optional<Bootstrap> bootstrap = globalBootstrap();
        return bootstrap.orElseGet(() -> InjectionServicesHolder.bootstrap(true).orElseThrow());
    }

    /**
     * Sets the primordial bootstrap configuration that will supply {@link #injectionServices()} during global
     * singleton initialization.
     *
     * @param bootstrap the primordial global bootstrap configuration
     * @see #globalBootstrap()
     */
    static void globalBootstrap(Bootstrap bootstrap) {
        Objects.requireNonNull(bootstrap);
        InjectionServicesHolder.bootstrap(bootstrap);
    }

    /**
     * Get {@link InjectionServices} instance if available. The highest {@link io.helidon.common.Weighted} service will be loaded
     * and returned. Remember to optionally configure any primordial {@link Bootstrap} configuration prior to the
     * first call to get {@code InjectionServices}.
     *
     * @return the services instance
     */
    static Optional<InjectionServices> injectionServices() {
        return InjectionServicesHolder.injectionServices();
    }

    /**
     * Short-cut for the following code block. During the first invocation the {@link Services} registry
     * will be initialized.
     *
     * <pre>
     * {@code
     *   return injectionServices().orElseThrow().services();
     * }
     * </pre>
     *
     * @return the services instance
     */
    static Services realizedServices() {
        return injectionServices().orElseThrow().services();
    }

    /**
     * Similar to {@link #services()}, but here if Injection is not available or the services registry has not yet been initialized
     * then this method will return {@code Optional.empty()}. This is convenience for users who conditionally want to use Injection's
     * service registry if it is currently available and in active use, but if not do alternative processing or allocations
     * directly, etc.
     *
     * @return the services instance if it has already been activated and initialized, empty otherwise
     */
    static Optional<Services> unrealizedServices() {
        return injectionServices()
                .flatMap(it -> it.services(false));
    }

    /**
     * The service registry. The first call typically loads and initializes the service registry. To avoid automatic loading
     * and initialization on any first request then consider using {@link #unrealizedServices()} or {@link #services(boolean)}.
     *
     * @return the services registry
     */
    default Services services() {
        return services(true).orElseThrow();
    }

    /**
     * The service registry. The first call typically loads and initializes the service registry.
     *
     * @param initialize true to allow initialization applicable for the 1st request, false to prevent 1st call initialization
     * @return the services registry if it is available and already has been initialized, empty if not yet initialized
     */
    Optional<? extends Services> services(boolean initialize);

    /**
     * The governing configuration.
     *
     * @return the config
     */
    InjectionServicesConfig config();

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
     * The default reference implementation will return a map of all service types that were deactivated to any
     * throwable that was observed during that services shutdown sequence.
     * <p>
     * The order in which services are deactivated is dependent upon whether the {@link #activationLog()} is available.
     * If the activation log is available, then services will be shutdown in reverse chronological order as how they
     * were started. If the activation log is not enabled or found to be empty then the deactivation will be in reverse
     * order of {@link RunLevel} from the highest value down to the lowest value. If two services share
     * the same {@link RunLevel} value then the ordering will be based upon the implementation's comparator.
     * <p>
     * When shutdown returns, it is guaranteed that all services were shutdown, or failed to achieve shutdown.
     * <p>
     * The shutdown timeout from {@link InjectionServicesConfigBlueprint#shutdownTimeout()} will be applied as the default.
     *
     * @return a map of all managed service types deactivated to results of deactivation, or empty if shutdown is not supported
     */
    Optional<Map<TypeName, ActivationResult>> shutdown();

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
     * Optionally, the set of {@link Services} lookup criteria that were recorded. This is only available if
     * {@link InjectionServicesConfig#serviceLookupCaching()} is enabled.
     *
     * @return the lookup criteria recorded, or empty if not available
     */
    Optional<Set<ServiceInfoCriteria>> lookups();

    /**
     * Will create an activation request either to {@link Phase#ACTIVE} or limited to any
     * {@link Bootstrap#limitRuntimePhase()} specified.
     *
     * @return the activation request
     */
    static ActivationRequest createActivationRequestDefault() {
        return ActivationRequest.builder().targetPhase(terminalActivationPhase()).build();
    }

    /**
     * The terminal phase for activation that we should not cross.
     *
     * @return the terminal phase for activation
     */
    static Phase terminalActivationPhase() {
        Optional<Bootstrap> globalBootstrap = InjectionServices.globalBootstrap();
        if (globalBootstrap.isPresent()) {
            Optional<Phase> limitPhase = globalBootstrap.get().limitRuntimePhase();
            return limitPhase.orElse(Phase.ACTIVE);
        }
        return Phase.ACTIVE;
    }

}
