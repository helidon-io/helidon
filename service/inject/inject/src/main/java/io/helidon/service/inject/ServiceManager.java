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

import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ServiceInstance;
import io.helidon.service.registry.ServiceInfo;

/*
Manager of a single service. There is one instance per service provider (and per service descriptor).
 */
class ServiceManager<T> {
    private final ServiceProvider<T> provider;
    private final Supplier<Activator<T>> activatorSupplier;
    private final Supplier<Scope> scopeSupplier;

    ServiceManager(Supplier<Scope> scopeSupplier,
                   ServiceProvider<T> provider,
                   Supplier<Activator<T>> activatorSupplier) {
        this.scopeSupplier = scopeSupplier;
        this.provider = provider;
        this.activatorSupplier = activatorSupplier;
    }

    @Override
    public String toString() {
        return provider.descriptor().serviceType().classNameWithEnclosingNames();
    }

    public ServiceInstance<T> registryInstance(Lookup lookup, QualifiedInstance<T> instance) {
        return new ServiceInstanceImpl<>(provider.descriptor(),
                                         provider.contracts(lookup),
                                         instance);
    }

    InjectionPlanBinder.Binder servicePlanBinder() {
        return provider.servicePlanBinder();
    }

    ServiceInfo descriptor() {
        return provider.descriptor().coreInfo();
    }

    InjectServiceInfo injectDescriptor() {
        return provider.descriptor();
    }

    /*
    Get service activator for the scope it is in (always works for singleton, may fail for other)
    this provides an instance of an activator that is bound to a scope instance
    */
    Activator<T> activator() {
        return scopeSupplier
                .get()
                .services()
                .activator(provider.descriptor().coreInfo(),
                           activatorSupplier);
    }

    private static final class ServiceInstanceImpl<T> implements ServiceInstance<T> {
        private final Descriptor<T> descriptor;
        private final QualifiedInstance<T> qualifiedInstance;
        private final Set<TypeName> contracts;

        private ServiceInstanceImpl(Descriptor<T> descriptor,
                                    Set<TypeName> contracts,
                                    QualifiedInstance<T> qualifiedInstance) {
            this.descriptor = descriptor;
            this.contracts = contracts;
            this.qualifiedInstance = qualifiedInstance;
        }

        @Override
        public T get() {
            return qualifiedInstance.get();
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiedInstance.qualifiers();
        }

        @Override
        public Set<TypeName> contracts() {
            return contracts;
        }

        @Override
        public TypeName scope() {
            return descriptor.scope();
        }

        @Override
        public double weight() {
            return descriptor.weight();
        }

        @Override
        public TypeName serviceType() {
            return descriptor.serviceType();
        }

        @Override
        public String toString() {
            return "Instance of " + descriptor.serviceType().fqName() + ": " + qualifiedInstance;
        }
    }
}
