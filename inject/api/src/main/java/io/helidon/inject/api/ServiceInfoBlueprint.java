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

package io.helidon.inject.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Describes a managed service or injection point.
 *
 * @see Services
 * @see ServiceInfoCriteria
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint(decorator = ServiceInfoBuildDecorator.class)
@Prototype.CustomMethods(ServiceInfoBlueprint.CustomMethods.class)
interface ServiceInfoBlueprint extends ServiceInfoBasicsBlueprint, ServiceInfoBasics {

    /**
     * The managed services external contracts / interfaces. These should also be contained within
     * {@link #contractsImplemented()}. External contracts are from other modules other than the module containing
     * the implementation typically.
     *
     * @see ExternalContracts
     * @return the service external contracts implemented
     */
    @Option.Singular("externalContractImplemented")
    Set<TypeName> externalContractsImplemented();

    /**
     * The management agent (i.e., the activator) that is responsible for creating and activating - typically build-time created.
     *
     * @return the activator type name
     */
    Optional<TypeName> activatorTypeName();

    /**
     * The name of the ascribed module, if known.
     *
     * @return the module name
     */
    Optional<String> moduleName();

    /**
     * Determines whether this service info matches the criteria for injection.
     * Matches is a looser form of equality check than {@code equals()}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    // internal note: it is unfortunate that we have a matches() here as well as in ServiceInfo. This is what happened
    // when we split ServiceInfo into ServiceInfoCriteria.  Sometimes we need ServiceInfo.matches(criteria), and other times
    // ServiceInfoCriteria.matches(criteria).
    default boolean matches(ServiceInfoCriteria criteria) {
        if (criteria == InjectionServices.EMPTY_CRITERIA) {
            return true;
        }

        boolean matches = matches(serviceTypeName(), criteria.serviceTypeName());
        if (matches && criteria.serviceTypeName().isEmpty()) {
            matches = contractsImplemented().containsAll(criteria.contractsImplemented())
                    || criteria.contractsImplemented().contains(serviceTypeName());
        }
        return matches
                && scopeTypeNames().containsAll(criteria.scopeTypeNames())
                && Qualifiers.matchesQualifiers(qualifiers(), criteria.qualifiers())
                && matches(activatorTypeName(), criteria.activatorTypeName())
                && matchesWeight(this, criteria)
                && matches(realizedRunLevel(), criteria.runLevel())
                && matches(moduleName(), criteria.moduleName());
    }

    private static boolean matches(Object src,
                                   Optional<?> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }

        return Objects.equals(src, criteria.get());
    }

    /**
     * Weight matching is always less or equal to criteria specified.
     *
     * @param src      the item being considered
     * @param criteria the criteria
     * @return true if there is a match
     */
    private static boolean matchesWeight(ServiceInfoBasics src,
                                         ServiceInfoCriteria criteria) {
        if (criteria.weight().isEmpty()) {
            return true;
        }

        Double srcWeight = src.realizedWeight();
        return (srcWeight.compareTo(criteria.weight().get()) <= 0);
    }

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Create a builder of service info from its basics counterpart.
         *
         * @param prototype instance to copy
         * @return a new builder with data from prototype
         */
        @Prototype.FactoryMethod
        static ServiceInfo.Builder builder(ServiceInfoBasics prototype) {
            Objects.requireNonNull(prototype);
            if (prototype instanceof ServiceInfo serviceInfo) {
                return ServiceInfo.builder(serviceInfo);
            }
            return ServiceInfo.builder()
                    .serviceTypeName(prototype.serviceTypeName())
                    .scopeTypeNames(prototype.scopeTypeNames())
                    .qualifiers(prototype.qualifiers())
                    .contractsImplemented(prototype.contractsImplemented())
                    .declaredRunLevel(prototype.declaredRunLevel())
                    .declaredWeight(prototype.declaredWeight());
        }

        /**
         * Add external contract implemented.
         *
         * @param builder the builder instance
         * @param type type of the external contract
         */
        @Prototype.BuilderMethod
        static void addExternalContractImplemented(ServiceInfo.BuilderBase<?, ?> builder, Class<?> type) {
            builder.addExternalContractImplemented(TypeName.create(type));
        }

        /**
         * Activator type.
         *
         * @param builder the builder instance
         * @param type type of the activator
         */
        @Prototype.BuilderMethod
        static void activatorTypeName(ServiceInfo.BuilderBase<?, ?> builder, Class<?> type) {
            builder.activatorTypeName(TypeName.create(type));
        }

        /**
         * Add a scope type.
         *
         * @param builder the builder instance
         * @param type type of the scope
         */
        @Prototype.BuilderMethod
        static void addScopeTypeName(ServiceInfo.BuilderBase<?, ?> builder, Class<?> type) {
            builder.addScopeTypeName(TypeName.create(type));
        }
    }
}
