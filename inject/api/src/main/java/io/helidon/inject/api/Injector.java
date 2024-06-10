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

/**
 * Used to perform programmatic activation and injection.
 * <p>
 * Note that the reference implementation of Injection only performs non-reflective, compile-time generation of service activators
 * for services that it manages. This Injector contract is mainly provided in order to allow other library extension
 * implementations to extend the model to perform other types of injection point resolution.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface Injector {

    /**
     * The strategy the injector should attempt to apply. The reference implementation for Injection provider only handles
     * {@link Injector.Strategy#ACTIVATOR} type.
     */
    enum Strategy {

        /**
         * Activator based implies compile-time injection strategy. This is the preferred / default strategy.
         */
        ACTIVATOR,

        /**
         * Reflection based implies runtime injection strategy. Note: This is available for other 3rd parties of Injection that
         * choose to use reflection as a strategy.
         */
        REFLECTION,

        /**
         * Any. Defers the strategy to the provider implementation's capabilities and configuration.
         */
        ANY

    }

    /**
     * Called to activate and inject a manage service instance or service provider, putting it into
     * {@link Phase#ACTIVE}.
     *
     * @param serviceOrServiceProvider the target instance or service provider being activated and injected
     * @param opts                     the injector options
     * @param <T>                      the managed service type
     * @return the result of the activation
     * @throws InjectionServiceProviderException if an injection or activation problem occurs
     * @see Activator
     */
    <T> ActivationResult activateInject(T serviceOrServiceProvider,
                                        InjectorOptions opts) throws InjectionServiceProviderException;

    /**
     * Called to deactivate a managed service or service provider, putting it into {@link Phase#DESTROYED}.
     * If a managed service has a {@link jakarta.annotation.PreDestroy} annotated method then it will be called during
     * this lifecycle event.
     *
     * @param serviceOrServiceProvider the service provider or instance registered and being managed
     * @param opts                     the injector options
     * @param <T>                      the managed service type
     * @return the result of the deactivation
     * @throws InjectionServiceProviderException if a problem occurs
     * @see DeActivator
     */
    <T> ActivationResult deactivate(T serviceOrServiceProvider,
                                    InjectorOptions opts) throws InjectionServiceProviderException;

}
