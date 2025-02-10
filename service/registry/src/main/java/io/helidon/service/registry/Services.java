/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.types.TypeName;

/**
 * Static access to the service registry.
 * <p>
 * <b>Note: </b> Using any methods on this class makes the service registry throw away optimization created via
 * the Helidon Service Registry Maven Plugin (code generated binding) for services that use the contracts configured.
 * <p>
 * There is always a single instance of a registry available within an application that can be access through
 * methods on this class.
 * <p>
 * This instance can be explicitly configured using {@link #registry(ServiceRegistry)} in case you want to setup a
 * registry instance yourself.
 * <p>
 * We support "late binding" of services using this class, such as by using {@link #set(Class, Object[])}.
 * These methods allow setting an explicit instance for a service normally built by the registry.
 * <p>
 * The set methods have the following limitations:
 * <ul>
 *     <li>The {@code contract} parameter must be of one of the supported contracts by the service registry</li>
 *     <li>The method MUST be called before the contract is used by any other service, to achieve consistency (i.e. calling
 *     {@link io.helidon.service.registry.ServiceRegistry#get(Class)} must yield the same instance if the service is a
 *     singleton</li>
 * </ul>
 */
public final class Services {
    private Services() {
    }

    /**
     * Configure the application wide registry to be used by components that require static lookup of required
     * services.
     * <p>
     * Note that this method MUST be called as one of the first things in your application, as all Helidon components
     * that use other services require it (except for Helidon Logging and Common Config).
     *
     * @param registry registry instance to use
     * @throws java.lang.NullPointerException in case the registry is null
     */
    public static void registry(ServiceRegistry registry) {
        GlobalServiceRegistry.registry(registry);
    }

    /**
     * Configure an explicit instance (explicit instances) for the specified service contract. This method
     * replaces all existing service providers and the registry will only use the provided instances.
     * <p>
     * This method must be called before the contract is requested from the registry for the first time.
     * <p>
     * This method only accepts contracts that are provided by one of the services in this registry.
     * <p>
     * This method will only work if the underlying implementation of the service registry is provided by Helidon
     * (i.e. it will not work on mocked types, and custom implementations).
     *
     * @param contract  contract to bind the instance under
     * @param instances instances to use
     * @param <T>       type of the contract
     * @throws io.helidon.service.registry.ServiceRegistryException in case the service contract was already used and cannot be
     *                                                              re-bound
     * @throws java.lang.NullPointerException                       if either of the parameters is null
     */
    @SafeVarargs
    public static <T> void set(Class<T> contract, T... instances) {
        Objects.requireNonNull(contract);
        Objects.requireNonNull(instances);
        for (T instance : instances) {
            Objects.requireNonNull(instance, "All instances must be non-null");
        }
        ServiceRegistry registry = GlobalServiceRegistry.registry();
        if (registry instanceof CoreServiceRegistry csr) {
            csr.set(contract, instances);
        }
    }

    /**
     * Add an explicit instance for the specified service contract.
     * <p>
     * This method has similar contract to {@link #set(Class, Object[])} except it adds the implementation,
     * where the {@code set} method replaces all implementations.
     *
     * @param contract  contract to bind the instance under
     * @param weight weight of the instance (use {@link io.helidon.common.Weighted#DEFAULT_WEIGHT} for default)
     * @param instance instance to add
     * @param <T>       type of the contract
     * @throws io.helidon.service.registry.ServiceRegistryException in case the service contract was already used and cannot be
     *                                                              re-bound
     * @throws java.lang.NullPointerException                       if either of the parameters is null
     */
    public static <T> void add(Class<T> contract, double weight, T instance) {
        Objects.requireNonNull(contract);
        Objects.requireNonNull(instance);

        ServiceRegistry registry = GlobalServiceRegistry.registry();
        if (registry instanceof CoreServiceRegistry csr) {
            csr.add(contract, weight, instance);
        }
    }

    /**
     * Add a custom service descriptor.
     * <p>
     * Note: it is recommended to use
     * {@link io.helidon.service.registry.ServiceRegistryConfig.Builder#addServiceDescriptor(ServiceDescriptor)} as
     * that service descriptor will be available when constructing the service registry.
     * This method is only to be used when the service registry must be created prior to configuring the binding.
     *
     * @param descriptor descriptor to "late bind" to the service registry
     * @throws io.helidon.service.registry.ServiceRegistryException in case the service contract was already used and cannot be
     *                                                              re-bound
     * @throws java.lang.NullPointerException                       if the parameter is null
     */
    public static void add(ServiceDescriptor<?> descriptor) {
        Objects.requireNonNull(descriptor);

        ServiceRegistry registry = GlobalServiceRegistry.registry();
        if (registry instanceof CoreServiceRegistry csr) {
            csr.add(descriptor);
        }
    }


    /**
     * Get the first instance of the contract, expecting the contract is available.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return an instance of the contract, never {@code null}
     * @throws io.helidon.service.registry.ServiceRegistryException in case the contract is not available in the registry
     * @throws java.lang.NullPointerException                       if either of the parameters is null
     * @see #first(Class)
     */
    public static <T> T get(Class<T> contract) {
        Objects.requireNonNull(contract);
        return GlobalServiceRegistry.registry().get(contract);
    }

    /**
     * Get the first instance of the contract, expecting the contract is available.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return an instance of the contract, never {@code null}
     * @throws io.helidon.service.registry.ServiceRegistryException in case the contract is not available in the registry
     * @throws java.lang.NullPointerException                       if either of the parameters is null
     * @see #first(Class)
     */
    public static <T> T get(TypeName contract) {
        Objects.requireNonNull(contract);
        return GlobalServiceRegistry.registry().get(contract);
    }


    /**
     * Get all instances of the contract.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return all instances in the registry, may be empty
     * @throws java.lang.NullPointerException if either of the parameters is null
     */
    public static <T> List<T> all(Class<T> contract) {
        Objects.requireNonNull(contract);
        return GlobalServiceRegistry.registry().all(contract);
    }

    /**
     * Get all instances of the contract.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return all instances in the registry, may be empty
     * @throws java.lang.NullPointerException if either of the parameters is null
     */
    public static <T> List<T> all(TypeName contract) {
        Objects.requireNonNull(contract);
        return GlobalServiceRegistry.registry().all(contract);
    }

    /**
     * Get first instance of the contract from the registry, all an empty optional if none exist.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return first instance, or an empty optional
     * @throws java.lang.NullPointerException if either of the parameters is null
     */
    public static <T> Optional<T> first(Class<T> contract) {
        Objects.requireNonNull(contract);
        return GlobalServiceRegistry.registry().first(contract);
    }

    /**
     * Get first instance of the contract from the registry, all an empty optional if none exist.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return first instance, or an empty optional
     * @throws java.lang.NullPointerException if either of the parameters is null
     */
    public static <T> Optional<T> first(TypeName contract) {
        Objects.requireNonNull(contract);
        return GlobalServiceRegistry.registry().first(contract);
    }
}
