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

import java.util.Optional;

/**
 * An extension to {@link ServiceProvider} that allows for startup binding from a picoApplication,
 * and thereby works in conjunction with the {@link ServiceBinder} during pico service registry
 * initialization.
 * <p>
 * The only guarantee the provider implementation has is ensuring that {@link Module} instances
 * are bound to the pico services instances, as well as informed on the module name.
 * <p>
 * Generally this class should be called internally by the framework, and typically occurs only during initialization sequences.
 *
 * @param <T> the type that this service provider manages
 * @see Application
 * @see ServiceProvider#serviceProviderBindable()
 */
public interface ServiceProviderBindable<T> extends ServiceProvider<T> {

    /**
     * Called to inform a service provider the module name it is bound to. Will only be called when there is a non-null
     * module name associated for the given {@link Module}. A service provider can be associated with
     * 0..1 modules.
     *
     * @param moduleName the non-null module name
     */
    void moduleName(String moduleName);

    /**
     * Returns {@code true} if this service provider is intercepted.
     *
     * @return flag indicating whether this service provider is intercepted
     */
    default boolean isIntercepted() {
        return interceptor().isPresent();
    }

    /**
     * Returns the service provider that intercepts this provider.
     *
     * @return the service provider that intercepts this provider
     */
    Optional<ServiceProvider<?>> interceptor();

    /**
     * Sets the interceptor for this service provider.
     *
     * @param interceptor the interceptor for this provider
     */
    default void interceptor(
            ServiceProvider<?> interceptor) {
        // NOP; intended to be overridden if applicable
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the root/parent provider for this service. A root/parent provider is intended to manage it's underlying
     * providers. Note that "root" and "parent" are interchangeable here since there is at most one level of depth that occurs
     * when {@link ServiceProvider}'s are wrapped by other providers.
     *
     * @return the root/parent provider or empty if this instance is the root provider
     */
    default Optional<ServiceProvider<?>> rootProvider() {
        return Optional.empty();
    }

    /**
     * Returns true if this provider is the root provider.
     *
     * @return indicates whether this provider is a root provider - the default is true.
     */
    default boolean isRootProvider() {
        return rootProvider().isEmpty();
    }

    /**
     * Sets the root/parent provider for this instance.
     *
     * @param rootProvider  sets the root provider
     */
    default void rootProvider(
            ServiceProvider<T> rootProvider) {
        // NOP; intended to be overridden if applicable
        throw new UnsupportedOperationException();
    }

    /**
     * Assigns the services instance this provider is bound to. A service provider can be associated with 0..1 services instance.
     * If not set, the service provider should use {@link PicoServices#picoServices()} to ascertain the instance.
     *
     * @param picoServices the pico services instance, or empty to clear any active binding
     */
    void picoServices(
            Optional<PicoServices> picoServices);

    /**
     * The binder can be provided by the service provider to deterministically set the injection plan at compile-time, and
     * subsequently loaded at early startup initialization.
     *
     * @return binder used for this service provider, or empty if not capable or ineligible of being bound
     */
    default Optional<ServiceInjectionPlanBinder.Binder> injectionPlanBinder() {
        return Optional.empty();
    }

}
