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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.Ip;

/**
 * A context for obtaining injection point values in a {@link io.helidon.inject.service.ServiceDescriptor}.
 * This context is pre-filled with the correct providers either based on an {@link io.helidon.inject.Application},
 * or based on analysis during activation of a service provider.
 *
 * @see io.helidon.inject.service.InjectionContext
 */
class InjectionContextImpl implements InjectionContext {
    private final Map<Ip, Supplier<?>> injectionPlans;

    private InjectionContextImpl(Map<Ip, Supplier<?>> injectionPlans) {
        this.injectionPlans = injectionPlans;
    }

    /**
     * Create an injection context based on a map of providers.
     * The type guarantee must be ensured by caller of this method (that each supplier matches the exact type
     * expected by the injection point). No further checks are done. Any invalid supplier would result in a
     * runtime {@link java.lang.ClassCastException}!
     *
     * @param injectionPlan map of injection points to a provider that satisfies that injection point
     * @return a new injection context
     */
    static InjectionContext create(Map<Ip, Supplier<?>> injectionPlan) {
        Objects.requireNonNull(injectionPlan);

        return new InjectionContextImpl(injectionPlan);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T param(Ip injectionPoint) {
        Supplier<?> injectionSupplier = injectionPlans.get(injectionPoint);
        if (injectionSupplier == null) {
            throw new NoSuchElementException("Cannot resolve injection id " + injectionPoint + " for service "
                                                     + injectionPoint.service().fqName()
                                                     + ", this dependency was not declared in "
                                                     + "the service descriptor");
        }

        return (T) injectionSupplier.get();
    }
}
