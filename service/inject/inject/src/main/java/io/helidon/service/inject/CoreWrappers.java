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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.ProviderType;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInfo;

class CoreWrappers {
    private CoreWrappers() {
    }

    static InjectServiceInfo create(ServiceInfo serviceInfo) {
        if (serviceInfo instanceof InjectServiceInfo inj) {
            return inj;
        }
        return new CoreServiceInfo(serviceInfo);
    }

    static <T> InjectServiceDescriptor<T> create(ServiceDescriptor<T> serviceInfo) {
        if (serviceInfo instanceof InjectServiceDescriptor<T> inj) {
            return inj;
        }
        return new CoreDescriptor<>(serviceInfo);
    }

    static class CoreServiceInfo implements InjectServiceInfo {
        private final ServiceInfo delegate;
        private final TypeName scope;
        private final ProviderType providerType;

        private CoreServiceInfo(ServiceInfo delegate) {
            this.delegate = delegate;
            this.scope = scope(delegate);
            this.providerType = delegate.factoryContracts().isEmpty()
                    ? ProviderType.SERVICE
                    : ProviderType.SUPPLIER;
        }

        @Override
        public double weight() {
            return delegate.weight();
        }

        @Override
        public TypeName scope() {
            return scope;
        }

        @Override
        public TypeName serviceType() {
            return delegate.serviceType();
        }

        @Override
        public TypeName descriptorType() {
            return delegate.descriptorType();
        }

        @Override
        public Set<ResolvedType> contracts() {
            return delegate.contracts();
        }

        @Override
        public boolean isAbstract() {
            return delegate.isAbstract();
        }

        @Override
        public ProviderType providerType() {
            return providerType;
        }

        private static TypeName scope(ServiceInfo delegate) {
            // if the core service is a supplier, we expect to get a new instance each time
            // otherwise it is a de-facto singleton
            return delegate.factoryContracts().isEmpty()
                    ? Injection.Singleton.TYPE
                    : Injection.PerLookup.TYPE;
        }
    }

    static final class CoreDescriptor<T> extends CoreServiceInfo implements InjectServiceDescriptor<T> {
        private final ServiceDescriptor<?> delegate;
        private final List<Ip> injectionPoints;

        CoreDescriptor(ServiceDescriptor<T> delegate) {
            super(delegate);

            this.delegate = delegate;

            this.injectionPoints = delegate.dependencies()
                    .stream()
                    .map(it -> Ip.builder()
                            .from(it)
                            .elementKind(ElementKind.CONSTRUCTOR)
                            .build())
                    .collect(Collectors.toList());
        }

        @Override
        public List<Ip> dependencies() {
            return injectionPoints;
        }

        @Override
        public Object instantiate(DependencyContext ctx) {
            return delegate.instantiate(ctx);
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return delegate.instantiate(ctx);
        }

        @Override
        public io.helidon.service.registry.ServiceInfo coreInfo() {
            return delegate;
        }
    }
}
