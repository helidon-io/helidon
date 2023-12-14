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

package io.helidon.inject;

import java.util.List;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.inject.service.Ip;

/**
 * A service provider bound to another service provider for an injection point.
 *
 * @param <T> type of the provided service
 */
class BoundServiceProvider<T> extends DescribedServiceProvider<T> implements ServiceProvider<T> {
    private final ServiceProvider<T> binding;
    private final LazyValue<T> instance;
    private final LazyValue<List<T>> instances;

    private BoundServiceProvider(ServiceProvider<T> binding, Ip injectionPoint) {
        super(binding.serviceInfo());

        this.binding = binding;
        ContextualServiceQuery query = ContextualServiceQuery.builder()
                .from(Lookup.create(injectionPoint))
                .injectionPoint(injectionPoint)
                .expected(false)
                .build();
        this.instance = LazyValue.create(() -> binding.first(query).orElse(null));
        this.instances = LazyValue.create(() -> binding.list(query));
    }

    /**
     * Creates a bound service provider to a specific binding.
     *
     * @param binding the bound service provider
     * @param ipId    the binding context
     * @return the service provider created, wrapping the binding delegate provider
     */
    static <V> ServiceProvider<V> create(ServiceProvider<V> binding,
                                         Ip ipId) {

        if (binding instanceof ServiceProviderBase<V> base) {
            if (!base.isProvider()) {
                return binding;
            }
        }
        return new BoundServiceProvider<>(binding, ipId);
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
    public boolean equals(Object another) {
        return (another instanceof ServiceProvider && binding.equals(another));
    }

    @Override
    public Optional<T> first(ContextualServiceQuery query) {
        return Optional.ofNullable(instance.get());
    }

    @Override
    public List<T> list(ContextualServiceQuery query) {
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
    public Phase currentActivationPhase() {
        return binding.currentActivationPhase();
    }

    @Override
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return Optional.of((ServiceProviderBindable<T>) binding);
    }
}