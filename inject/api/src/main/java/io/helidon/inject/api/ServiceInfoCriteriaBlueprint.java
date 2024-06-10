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
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Criteria to discover services.
 *
 * @see Services
 * @see ServiceInfo
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
@Prototype.CustomMethods(ServiceInfoCriteriaBlueprint.CustomMethods.class)
interface ServiceInfoCriteriaBlueprint {

    /**
     * The managed service implementation type name.
     *
     * @return the service type name
     */
    Optional<TypeName> serviceTypeName();

    /**
     * The managed service assigned Scope's.
     *
     * @return the service scope type name
     */
    @Option.Singular
    Set<TypeName> scopeTypeNames();

    /**
     * The managed service assigned Qualifier's.
     *
     * @return the service qualifiers
     */
    @Option.Singular
    Set<Qualifier> qualifiers();

    /**
     * The managed services advertised types (i.e., typically its interfaces).
     *
     * @see ExternalContracts
     * @return the service contracts implemented
     */
    @Option.Singular("contractImplemented")
    Set<TypeName> contractsImplemented();

    /**
     * The optional {@link RunLevel} ascribed to the service.
     *
     * @return the service's run level
     */
    Optional<Integer> runLevel();

    /**
     * Weight that was declared on the type itself.
     *
     * @return the declared weight
     */
    Optional<Double> weight();

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
     * Determines whether the non-proxied, {@link Intercepted} services should be returned in any lookup operation. If this
     * option is disabled then only the {@link Interceptor}-generated service will be eligible to be returned and not the service
     * being intercepted.
     * The default value is {@code false}.
     *
     * @return true if the non-proxied type intercepted services should be eligible
     */
    @ConfiguredOption("false")
    boolean includeIntercepted();

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
    default boolean matches(ServiceInfoCriteriaBlueprint criteria) {
        return matchesContracts(criteria)
                && scopeTypeNames().containsAll(criteria.scopeTypeNames())
                && Qualifiers.matchesQualifiers(qualifiers(), criteria.qualifiers())
                && matches(activatorTypeName(), criteria.activatorTypeName())
                && matches(runLevel(), criteria.runLevel())
                //                && matchesWeight(this, criteria) -- intentionally not checking weight here!
                && matches(moduleName(), criteria.moduleName());
    }

    /**
     * Determines whether the provided criteria match just the contracts portion of the provided criteria. Note that
     * it is expected any external contracts have been consolidated into the regular contract section.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance from only the contracts point of view
     */
    default boolean matchesContracts(ServiceInfoCriteriaBlueprint criteria) {
        if (criteria == InjectionServices.EMPTY_CRITERIA) {
            return true;
        }

        boolean matches = matches(serviceTypeName(), criteria.serviceTypeName());
        if (matches && criteria.serviceTypeName().isEmpty()) {
            matches = contractsImplemented().containsAll(criteria.contractsImplemented());
        }
        return matches;
    }

    private static boolean matches(Object src,
                                   Optional<?> criteria) {

        return criteria.map(o -> Objects.equals(src, o)).orElse(true);

    }

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * The managed services advertised types (i.e., typically its interfaces).
         *
         * @param builder builder instance
         * @param contract the service contracts implemented
         * @see #contractsImplemented()
         */
        @Prototype.BuilderMethod
        static void addContractImplemented(ServiceInfoCriteria.BuilderBase<?, ?> builder, Class<?> contract) {
            builder.addContractImplemented(TypeName.create(contract));
        }
    }
}
