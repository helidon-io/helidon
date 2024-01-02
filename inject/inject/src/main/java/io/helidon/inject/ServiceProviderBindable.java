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

import java.util.Optional;

/**
 * An extension to {@link RegistryServiceProvider} that allows for startup binding from a
 * {@code Injection$$Application},
 * and thereby works in conjunction with the {@link io.helidon.inject.service.ServiceBinder} during injection service registry
 * initialization.
 * <p>
 * The only guarantee the provider implementation has is ensuring that {@link io.helidon.inject.service.ModuleComponent}
 * instances
 * are bound to the <i>Services</i> instances, as well as informed on the module name.
 * <p>
 * Generally this class should be called internally by the framework, and typically occurs only during initialization sequences.
 *
 * @param <T> the type that this service provider manages
 * @see io.helidon.inject.Application
 * @see RegistryServiceProvider#serviceProviderBindable()
 */
public interface ServiceProviderBindable<T> extends RegistryServiceProvider<T> {
    /**
     * Returns true if this service provider instance is an {@link io.helidon.inject.service.Interception.Interceptor}.
     *
     * @return true if this service provider is an interceptor
     */
    default boolean isInterceptor() {
        return false;
    }

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
    Optional<RegistryServiceProvider<?>> interceptor();

    /**
     * Sets the interceptor for this service provider.
     *
     * @param interceptor the interceptor for this provider
     */
    default void interceptor(RegistryServiceProvider<?> interceptor) {
        // NOP; intended to be overridden if applicable
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the root/parent provider for this service. A root/parent provider is intended to manage it's underlying
     * providers. Note that "root" and "parent" are interchangeable here since there is at most one level of depth that occurs
     * when {@link RegistryServiceProvider}'s are wrapped by other providers.
     *
     * @return the root/parent provider or empty if this instance is the root provider
     */
    default Optional<RegistryServiceProvider<?>> rootProvider() {
        return Optional.empty();
    }

    /**
     * Sets the root/parent provider for this instance.
     *
     * @param rootProvider sets the root provider
     */
    default void rootProvider(RegistryServiceProvider<T> rootProvider) {
        // NOP; intended to be overridden if applicable
        throw new UnsupportedOperationException();
    }

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
