/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.runtime;

import java.util.Objects;

import io.helidon.pico.api.ActivationResult;
import io.helidon.pico.api.Activator;
import io.helidon.pico.api.DeActivationRequest;
import io.helidon.pico.api.DeActivator;
import io.helidon.pico.api.Injector;
import io.helidon.pico.api.InjectorOptions;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.ServiceProvider;

/**
 * Default reference implementation for the {@link Injector}.
 */
class DefaultInjector implements Injector {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ActivationResult activateInject(T serviceOrServiceProvider,
                                               InjectorOptions opts) throws PicoServiceProviderException {
        Objects.requireNonNull(serviceOrServiceProvider);
        Objects.requireNonNull(opts);

        ActivationResult.Builder resultBuilder = ActivationResult.builder();

        if (opts.strategy() != Strategy.ANY && opts.strategy() != Strategy.ACTIVATOR) {
            return handleError(resultBuilder, opts, "only " + Strategy.ACTIVATOR + " strategy is supported", null);
        }

        if (!(serviceOrServiceProvider instanceof AbstractServiceProvider)) {
            return handleError(resultBuilder, opts, "unsupported service type: " + serviceOrServiceProvider, null);
        }

        AbstractServiceProvider<T> instance = (AbstractServiceProvider<T>) serviceOrServiceProvider;
        resultBuilder.serviceProvider(instance);

        Activator activator = instance.activator().orElse(null);
        if (activator == null) {
            return handleError(resultBuilder, opts, "the service provider does not have an activator", instance);
        }

        return activator.activate(opts.activationRequest());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ActivationResult deactivate(T serviceOrServiceProvider,
                                           InjectorOptions opts) throws PicoServiceProviderException {
        Objects.requireNonNull(serviceOrServiceProvider);
        Objects.requireNonNull(opts);

        ActivationResult.Builder resultBuilder = ActivationResult.builder();

        if (opts.strategy() != Strategy.ANY && opts.strategy() != Strategy.ACTIVATOR) {
            return handleError(resultBuilder, opts, "only " + Strategy.ACTIVATOR + " strategy is supported", null);
        }

        if (!(serviceOrServiceProvider instanceof AbstractServiceProvider)) {
            return handleError(resultBuilder, opts, "unsupported service type: " + serviceOrServiceProvider, null);
        }

        AbstractServiceProvider<T> instance = (AbstractServiceProvider<T>) serviceOrServiceProvider;
        resultBuilder.serviceProvider(instance);

        DeActivator deactivator = instance.deActivator().orElse(null);
        if (deactivator == null) {
            return handleError(resultBuilder, opts, "the service provider does not have a deactivator", instance);
        }

        DeActivationRequest request = DeActivationRequest.builder()
                .throwIfError(opts.activationRequest().throwIfError())
                .build();
        return deactivator.deactivate(request);
    }

    private ActivationResult handleError(ActivationResult.Builder resultBuilder,
                                         InjectorOptions opts,
                                         String message,
                                         ServiceProvider<?> serviceProvider) {
        PicoException e = (serviceProvider == null)
                ? new PicoException(message) : new PicoServiceProviderException(message, serviceProvider);
        resultBuilder.error(e);
        if (opts.activationRequest().throwIfError()) {
            throw e;
        }
        return resultBuilder.build();
    }

}
