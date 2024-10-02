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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.types.TypeName;

/**
 * Service descriptor to enable dependency on services loaded via {@link java.util.ServiceLoader}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
public abstract class ServiceLoader__ServiceDescriptor implements ServiceDescriptor<Object> {
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
     * @param provider          Java Service Loader provider instance
     * @param weight            weight of the provider
     * @return new descriptor
     */
    public static ServiceDescriptor<Object> create(TypeName providerInterface,
                                                   ServiceLoader.Provider<Object> provider,
                                                   double weight) {
        LOCK.lock();
        try {
            TypeName providerImpl = TypeName.create(provider.type());
            ProviderKey key = new ProviderKey(providerInterface, providerImpl);
            ServiceLoader__ServiceDescriptor descriptor = INSTANCE_CACHE.get(key);
            if (descriptor == null) {
                descriptor = new ServiceProviderDescriptor(providerInterface, providerImpl, provider, weight);
                INSTANCE_CACHE.put(key, descriptor);
            }

            return descriptor;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Create a new instance for a specific provider interface and implementation.
     * This method is used from generated code.
     *
     * @param providerInterface provider interface type
     * @param implType          implementation class
     * @param instanceSupplier  supplier of a new instance (so we do not use reflection)
     * @param weight            weight of the provider implementation
     * @param <T>               type of the implementation
     * @return a new service descriptor
     */
    public static <T> ServiceDescriptor<Object> create(TypeName providerInterface,
                                                       Class<T> implType,
                                                       Supplier<T> instanceSupplier,
                                                       double weight) {
        return ServiceLoader__ServiceDescriptor.create(providerInterface, new ProviderImpl(implType, instanceSupplier), weight);
    }

    @Override
    public TypeName descriptorType() {
        return DESCRIPTOR_TYPE;
    }

    /**
     * Type name of the provider interface this service implementation implements.
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
                                          ServiceLoader.Provider<Object> provider,
                                          double weight) {
            this.providerInterface = providerInterface;
            this.contracts = Set.of(providerInterface);
            this.providerImpl = providerImpl;
            this.weight = weight;
            this.instance = LazyValue.create(provider);
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
    }

    private record ProviderKey(TypeName providerInterface, TypeName providerImpl) {
    }

    private static class ProviderImpl implements ServiceLoader.Provider<Object> {
        private final Class<?> implType;
        private final Supplier<?> instanceSupplier;

        private ProviderImpl(Class<?> implType, Supplier<?> instanceSupplier) {
            this.implType = implType;
            this.instanceSupplier = instanceSupplier;
        }

        @Override
        public Class<?> type() {
            return implType;
        }

        @Override
        public Object get() {
            return instanceSupplier.get();
        }
    }
}
