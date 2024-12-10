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

import java.util.Objects;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

/**
 * A virtual descriptor is not backed by a generated descriptor.
 */
public class VirtualDescriptor implements ServiceDescriptor<Object> {
    private static final TypeName TYPE = TypeName.create(VirtualDescriptor.class);
    private final Set<ResolvedType> contracts;
    private final TypeName serviceType;
    private final TypeName descriptorType;
    private final double weight;
    private final Object instance;

    VirtualDescriptor(TypeName contract) {
        // explicit instances configured through config have a very high weight, to be used first
        this(contract, Weighted.DEFAULT_WEIGHT + 1000, TYPE);
    }

    VirtualDescriptor(TypeName contract, double weight, Object instance) {
        this.contracts = Set.of(ResolvedType.create(contract));
        this.serviceType = contract;
        this.descriptorType = TypeName.builder(TYPE)
                .className(TYPE.className() + "_" + contract.className() + "__VirtualDescriptor")
                .build();
        this.weight = weight;
        this.instance = instance;
    }

    @Override
    public TypeName serviceType() {
        return serviceType;
    }

    @Override
    public TypeName descriptorType() {
        return descriptorType;
    }

    @Override
    public Set<ResolvedType> contracts() {
        return contracts;
    }

    @Override
    public double weight() {
        return weight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VirtualDescriptor that)) {
            return false;
        }
        // if this represents the same instance, it is equal, otherwise not
        return Objects.equals(serviceType, that.serviceType)
                && (instance == that.instance);
    }

    @Override
    public TypeName scope() {
        return Service.Singleton.TYPE;
    }

    @Override
    public String toString() {
        return "VirtualDescriptor for contract " + serviceType + " (" + weight + ")";
    }
}
