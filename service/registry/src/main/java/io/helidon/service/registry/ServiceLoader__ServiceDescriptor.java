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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LazyValue;
import io.helidon.common.types.TypeName;

/**
 * Service descriptor to enable dependency on services loaded via {@link java.util.ServiceLoader}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
public abstract class ServiceLoader__ServiceDescriptor implements GeneratedService.Descriptor<Object> {
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(ServiceLoader__ServiceDescriptor.class);

    // we must use instance comparison, so we must make sure we give the same instance for the same combination
    private static final Map<ProviderKey, ServiceLoader__ServiceDescriptor> INSTANCE_CACHE = new HashMap<>();
    private static final ReentrantLock LOCK = new ReentrantLock();

    private ServiceLoader__ServiceDescriptor() {
    }

    /**
     * Create a new instance for a specific service provider interface and its implementation.
     *
     * @param providerInterface provider interface type
     * @param providerImpl      provider implementation type
     * @param weight            weight of the provider
     * @return new descriptor
     */
    public static ServiceLoader__ServiceDescriptor create(TypeName providerInterface,
                                                          TypeName providerImpl,
                                                          double weight) {
        LOCK.lock();
        try {
            ProviderKey key = new ProviderKey(providerInterface, providerImpl);
            ServiceLoader__ServiceDescriptor descriptor = INSTANCE_CACHE.get(key);
            if (descriptor == null) {
                descriptor = new ServiceProviderDescriptor(providerInterface, providerImpl, weight);
                INSTANCE_CACHE.put(key, descriptor);
            }

            return descriptor;
        } finally {
            LOCK.unlock();
        }
    }

    @Override
    public TypeName descriptorType() {
        return DESCRIPTOR_TYPE;
    }

    /**
     * The interface of the service loader provider.
     *
     * @return provider interface type
     */
    public abstract TypeName providerInterface();

    private static class ServiceProviderDescriptor extends ServiceLoader__ServiceDescriptor {
        private final TypeName providerInterface;
        private final Set<TypeName> contracts;
        private final TypeName providerImpl;
        private final double weight;
        private final LazyValue<Object> instance;

        private ServiceProviderDescriptor(TypeName providerInterface,
                                          TypeName providerImpl,
                                          double weight) {

            this.providerInterface = providerInterface;
            this.contracts = Set.of(providerInterface);
            this.providerImpl = providerImpl;
            this.weight = weight;
            this.instance = LazyValue.create(this::newInstance);
        }

        @Override
        public String toString() {
            return providerInterface + "/"
                    + providerImpl
                    + "(" + weight + ")";
        }

        @Override
        public TypeName serviceType() {
            return providerImpl;
        }

        @Override
        public Set<TypeName> contracts() {
            return contracts;
        }

        @Override
        public Object instantiate(DependencyContext ctx) {
            return instance.get();
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public TypeName providerInterface() {
            return providerInterface;
        }

        private Object newInstance() {
            return CoreServiceDiscovery.instantiateServiceLoaded(serviceType());
        }
    }

    private record ProviderKey(TypeName providerInterface, TypeName providerImpl) {
    }
}
