/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.testing;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.ActivationResult;
import io.helidon.inject.InjectionException;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.service.ServiceInfo;

/**
 * Services with support for testing.
 */
public class InjectionTestingSupport {
    private InjectionTestingSupport() {
    }

    /**
     * Describe the provided instance or provider.
     *
     * @param providerOrInstance the instance to provider
     * @return the description of the instance
     */
    public static String toDescription(Object providerOrInstance) {
        if (providerOrInstance instanceof Optional<?> optional) {
            providerOrInstance = optional.orElse(null);
        }

        if (providerOrInstance instanceof RegistryServiceProvider<?> sp) {
            return sp.description();
        }
        return String.valueOf(providerOrInstance);
    }

    /**
     * Describe the provided instance or provider collection.
     *
     * @param coll the instance to provider collection
     * @return the description of the instance
     */
    public static List<String> toDescriptions(Collection<?> coll) {
        return coll.stream().map(InjectionTestingSupport::toDescription).collect(Collectors.toList());
    }

    /**
     * Creates a list of service types simple type names of the service descriptors in the collection.
     * If the class is an inner class, the returned collection contains the outer class name as well, separated by a dot.
     *
     * @param coll service descriptor collection to go through
     * @return a list where each entry matches the simple class name of the entry in the provided collection
     */
    public static List<String> toSimpleTypes(List<ServiceInfo> coll) {
        return coll.stream()
                .map(ServiceInfo::serviceType)
                .map(TypeName::classNameWithEnclosingNames)
                .toList();
    }

    /**
     * Creates a list of service types simple type names of the service descriptors in the collection.
     * If the class is an inner class, the returned collection contains the outer class name as well, separated by a dot.
     *
     * @param coll service descriptor collection to go through
     * @return a list where each entry matches the simple class name of the entry in the provided collection
     */
    public static List<String> toTypes(List<ServiceInfo> coll) {
        return coll.stream()
                .map(ServiceInfo::serviceType)
                .map(TypeName::fqName)
                .toList();
    }

    /**
     * Creates a list of simple type names of the elements in the collection.
     *
     * @param coll collection to go through
     * @return a list where each entry matches the simple class name of the entry in the provided collection
     */
    public static List<String> toSimpleTypes(Collection<?> coll) {
        return coll.stream()
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .toList();
    }

    /**
     * A shutdown method that fails on deactivation errors.
     *
     * @param injectionServices services to shut down, this may be {@code null} to prevent unexpected errors when registry
     *                          fails to initialize in a test and is null at time of shutdown
     */
    public static void shutdown(InjectionServices injectionServices) {
        if (injectionServices == null) {
            return;
        }
        Map<TypeName, ActivationResult> shutdown = injectionServices.shutdown();
        shutdown.values()
                .forEach(it -> {
                    if (it.failure()) {
                        throw new InjectionException("Failed to shutdown injection services: " + it, it.error().orElse(null));
                    }
                });
    }
}
