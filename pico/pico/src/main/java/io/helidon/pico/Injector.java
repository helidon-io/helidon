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

/**
 * Used to perform programmatic activation and injection.
 * <p>
 * Note that the reference implementation of Pico only performs non-reflective, compile-time generation of service activators
 * for services that it manages. This Injector contract is mainly provided in order to allow other library extension
 * implementations to extend the model to perform other types of injection point resolution.
 */
public interface Injector {

    /**
     * Empty options is the same as passing no options, taking all the default values.
     */
    InjectorOptions EMPTY_OPTIONS = DefaultInjectorOptions.builder().build();

    /**
     * The strategy the injector should attempt to apply. The reference implementation for Pico provider only handles
     * {@link Injector.Strategy#ACTIVATOR} type.
     */
    enum Strategy {

        /**
         * Activator based implies compile-time injection strategy. This is the preferred / default strategy.
         */
        ACTIVATOR,

        /**
         * Reflection based implies runtime injection strategy. Note: This is available for other 3rd parties of Pico that choose
         * to use reflection as a strategy.
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
     * <p>
     * Note that if a {@link ServiceProvider} is passed in then the {@link Activator}
     * will be used instead.  In this case, then any {@link InjectorOptions#startAtPhase()} and
     * {@link InjectorOptions#finishAtPhase()} arguments will be ignored.
     *
     * @param serviceOrServiceProvider the target instance or service provider being activated and injected
     * @param opts                     the injector options, or use {@link #EMPTY_OPTIONS}
     * @param <T>                      the managed service instance type
     * @return the result of the activation
     * @throws io.helidon.pico.PicoServiceProviderException if an injection or activation problem occurs
     * @see Activator
     */
    <T> ActivationResult activateInject(
            T serviceOrServiceProvider,
            InjectorOptions opts) throws PicoServiceProviderException;

    /**
     * Called to deactivate a managed service or service provider, putting it into {@link Phase#DESTROYED}.
     * If a managed service has a {@link jakarta.annotation.PreDestroy} annotated method then it will be called during
     * this lifecycle event.
     * <p>
     * Note that if a {@link ServiceProvider} is passed in then the {@link DeActivator}
     * will be used instead. In this case, then any {@link InjectorOptions#startAtPhase()} and
     * {@link InjectorOptions#finishAtPhase()} arguments will be ignored.
     *
     * @param serviceOrServiceProvider the service provider or instance registered and being managed
     * @param opts                     the injector options, or use {@link #EMPTY_OPTIONS}
     * @param <T>                      the managed service instance type
     * @return the result of the deactivation
     * @throws io.helidon.pico.PicoServiceProviderException if a problem occurs
     * @see DeActivator
     */
    <T> ActivationResult deactivate(
            T serviceOrServiceProvider,
            InjectorOptions opts) throws PicoServiceProviderException;

}
