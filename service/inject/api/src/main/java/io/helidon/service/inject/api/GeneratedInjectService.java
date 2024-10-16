/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.GeneratedService;

/**
 * All types in this class are used from generated code for services.
 */
public final class GeneratedInjectService {
    private GeneratedInjectService() {
    }

    /**
     * Each descriptor for s service that is implements {@link io.helidon.service.inject.api.Injection.QualifiedProvider}
     * implements this interface to provide information about the qualifier it supports.
     */
    public interface QualifiedProviderDescriptor {
        /**
         * Type of qualifier a {@link io.helidon.service.inject.api.Injection.QualifiedProvider} provides.
         *
         * @return type name of the qualifier this qualified provider can provide instances for
         */
        TypeName qualifierType();
    }

    /**
     * Each descriptor for s service that is annotated with {@link io.helidon.service.inject.api.Injection.CreateFor}
     * implements this interface to provide information about the type that drives it.
     */
    public interface CreateForDescriptor {
        /**
         * Service instances may be created for instances of another service.
         * If a type is created for another type, it inherits ALL qualifiers of the type that it is based on.
         *
         * @return create for service type
         */
        TypeName createFor();
    }

    /**
     * Each descriptor for a service that implements {@link io.helidon.service.inject.api.Injection.ScopeHandler}
     * implements this interface to provide information about the scope it handles.
     */
    public interface ScopeHandlerDescriptor {
        /**
         * Scope handled by the scope handler service.
         *
         * @return type of the scope handled (annotation)
         */
        TypeName handledScope();
    }

    /**
     * Utility type to provide method to combine injection point information for inheritance support.
     */
    public static final class IpSupport {
        private IpSupport() {
        }

        /**
         * Combine dependencies from this type with dependencies from supertype.
         * This is a utility for code generated types.
         *
         * @param myType    this type's dependencies
         * @param superType super type's dependencies
         * @return a new list without constructor dependencies from super type
         */
        public static List<Ip> combineIps(List<Ip> myType, List<Ip> superType) {
            List<Ip> result = new ArrayList<>(myType);

            // always inject all fields
            result.addAll(superType.stream()
                                  .filter(it -> it.elementKind() == ElementKind.FIELD)
                                  .toList());
            // ignore constructors, as we only need to inject constructor on the instantiated type

            // and only add methods that are not already injected on existing type
            Set<String> injectedMethods = myType.stream()
                    .filter(it -> it.elementKind() == ElementKind.METHOD)
                    .map(Ip::method)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet());

            result.addAll(superType.stream()
                                  .filter(it -> it.elementKind() == ElementKind.METHOD)
                                  .filter(it -> it.method().isPresent())
                                  .filter(it -> injectedMethods.add(it.method().get())) // we check presence above
                                  .toList());

            return List.copyOf(result);
        }
    }
}
