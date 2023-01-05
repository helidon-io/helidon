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

package io.helidon.pico.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.pico.Activator;
import io.helidon.pico.ContextualServiceQuery;
import io.helidon.pico.DeActivator;
import io.helidon.pico.DefaultContextualServiceQuery;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.Phase;
import io.helidon.pico.PostConstructMethod;
import io.helidon.pico.PreDestroyMethod;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;

/**
 * A service provider that is bound to a particular injection point context.
 *
 * @param <T> the type of the bound service provider
 */
class BoundedServiceProvider<T> implements ServiceProvider<T> {

    private final ServiceProvider<T> binding;
    private final InjectionPointInfo ipInfoCtx;
    private final LazyValue<T> instance;
    private final LazyValue<List<T>> instances;

    private BoundedServiceProvider(
            ServiceProvider<T> binding,
            InjectionPointInfo ipInfoCtx) {
        this.binding = Objects.requireNonNull(binding);
        this.ipInfoCtx = Objects.requireNonNull(ipInfoCtx);
        ContextualServiceQuery query = DefaultContextualServiceQuery.builder()
                .injectionPointInfo(ipInfoCtx)
                .serviceInfoCriteria(ipInfoCtx.dependencyToServiceInfo())
                .expected(true).build();
        this.instance = LazyValue.create(() -> binding.first(query).orElse(null));
        this.instances = LazyValue.create(() -> binding.list(query));
    }

    /**
     * Creates a bound service provider to a specific binding.
     *
     * @param binding   the bound service provider
     * @param ipInfoCtx the binding context
     * @return the service provider created, wrapping the binding delegate provider
     */
    static <V> ServiceProvider<V> create(
            ServiceProvider<V> binding,
            InjectionPointInfo ipInfoCtx) {
        assert (binding != null);
        assert (!(binding instanceof BoundedServiceProvider));
        if (binding instanceof AbstractServiceProvider) {
            AbstractServiceProvider<?> sp = (AbstractServiceProvider<?>) binding;
            if (!sp.isProvider()) {
                return binding;
            }
        }
        return new BoundedServiceProvider<>(binding, ipInfoCtx);
    }

    @Override
    public String toString() {
        return binding.toString();
    }

    @Override
    public int hashCode() {
        return binding.hashCode();
    }

    @Override
    public boolean equals(
            Object another) {
        return binding.equals(another);
    }

    @Override
    public Optional<T> first(
            ContextualServiceQuery query) {
        assert (query.injectionPointInfo().isEmpty() || ipInfoCtx.equals(query.injectionPointInfo().get()))
                : query.injectionPointInfo() + " was not equal to " + this.ipInfoCtx;
        assert (ipInfoCtx.dependencyToServiceInfo().matches(query.serviceInfoCriteria()))
                : query.serviceInfoCriteria() + " did not match " + this.ipInfoCtx.dependencyToServiceInfo();
        return Optional.ofNullable(instance.get());
    }

    @Override
    public List<T> list(
            ContextualServiceQuery query) {
        assert (query.injectionPointInfo().isEmpty() || ipInfoCtx.equals(query.injectionPointInfo().get()))
                : query.injectionPointInfo() + " was not equal to " + this.ipInfoCtx;
        assert (ipInfoCtx.dependencyToServiceInfo().matches(query.serviceInfoCriteria()))
                : query.serviceInfoCriteria() + " did not match " + this.ipInfoCtx.dependencyToServiceInfo();
        return instances.get();
    }

    @Override
    public String id() {
        return binding.id();
    }

    @Override
    public String description() {
        return binding.description();
    }

    @Override
    public boolean isProvider() {
        return binding.isProvider();
    }

    @Override
    public ServiceInfo serviceInfo() {
        return binding.serviceInfo();
    }

    @Override
    public DependenciesInfo dependencies() {
        return binding.dependencies();
    }

    @Override
    public Phase currentActivationPhase() {
        return binding.currentActivationPhase();
    }

    @Override
    public Optional<Activator<T>> activator() {
        return binding.activator();
    }

    @Override
    public Optional<DeActivator<T>> deActivator() {
        return binding.deActivator();
    }

    @Override
    public Optional<PostConstructMethod> postConstructMethod() {
        return binding.postConstructMethod();
    }

    @Override
    public Optional<PreDestroyMethod> preDestroyMethod() {
        return binding.preDestroyMethod();
    }

    @Override
    public ServiceProvider<T> serviceProviderBindable() {
        return binding;
    }

}
