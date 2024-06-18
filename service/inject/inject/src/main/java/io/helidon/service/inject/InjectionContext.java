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

package io.helidon.service.inject;

import java.util.Map;
import java.util.NoSuchElementException;

import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;

class InjectionContext implements DependencyContext {
    private final Map<Dependency, IpPlan<?>> injectionPlan;

    InjectionContext(Map<Dependency, IpPlan<?>> injectionPlan) {
        this.injectionPlan = injectionPlan;
    }

    static DependencyContext create(Map<Dependency, IpPlan<?>> injectionPlan) {
        return new InjectionContext(injectionPlan);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T dependency(Dependency dependency) {
        IpPlan<?> ipPlan = injectionPlan.get(dependency);
        if (ipPlan == null) {
            throw new NoSuchElementException("Cannot resolve injection id " + dependency + " for service "
                                                     + dependency.service().fqName()
                                                     + ", this dependency was not declared in "
                                                     + "the service descriptor");
        }
        return (T) ipPlan.get();
    }
}
