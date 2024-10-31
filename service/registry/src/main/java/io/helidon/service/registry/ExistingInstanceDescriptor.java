/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

/**
 * A special case service descriptor allowing registration of service instances that do not have
 * a code generated service descriptor, such as for testing.
 * <p>
 * Note that these instances cannot be used for creating code generated binding, as they do not exist as classes.
 *
 * @param <T> type of the instance
 */
public final class ExistingInstanceDescriptor<T> implements ServiceDescriptor<T> {
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(ExistingInstanceDescriptor.class);
    private final T instance;
    private final TypeName serviceType;
    private final Set<ResolvedType> contracts;
    private final double weight;

    private ExistingInstanceDescriptor(T instance,
                                       TypeName serviceType,
                                       Set<ResolvedType> contracts,
                                       double weight) {
        this.instance = instance;
        this.serviceType = serviceType;
        this.contracts = contracts;
        this.weight = weight;
    }

    /**
     * Create a new instance.
     * The only place this can be used at is with
     * {@link
     * io.helidon.service.registry.ServiceRegistryConfig.Builder#addServiceDescriptor(io.helidon.service.registry.ServiceDescriptor)}.
     *
     * @param instance  service instance to use
     * @param contracts contracts of the service (the ones we want service registry to use)
     * @param weight    weight of the service
     * @param <T>       type of the service
     * @return a new service descriptor for the provided information
     */
    public static <T> ExistingInstanceDescriptor<T> create(T instance,
                                                           Collection<Class<? super T>> contracts,
                                                           double weight) {
        TypeName serviceType = TypeName.create(instance.getClass());
        Set<ResolvedType> contractSet = contracts.stream()
                .map(ResolvedType::create)
                .collect(Collectors.toSet());

        return new ExistingInstanceDescriptor<>(instance, serviceType, contractSet, weight);
    }

    @Override
    public TypeName serviceType() {
        return serviceType;
    }

    @Override
    public TypeName descriptorType() {
        return DESCRIPTOR_TYPE;
    }

    @Override
    public Set<ResolvedType> contracts() {
        return contracts;
    }

    @Override
    public Object instantiate(DependencyContext ctx) {
        return instance;
    }

    @Override
    public double weight() {
        return weight;
    }

    @Override
    public String toString() {
        return contracts + " (" + weight + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExistingInstanceDescriptor<?> that)) {
            return false;
        }
        return Double.compare(weight, that.weight) == 0
                && instance == that.instance
                && Objects.equals(contracts, that.contracts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, contracts, weight);
    }
}
