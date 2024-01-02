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

package io.helidon.inject.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Lookup criteria to discover services, mostly used when interacting with a service registry.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(LookupSupport.CustomMethods.class)
interface LookupBlueprint {

    /**
     * The managed service implementation type name.
     *
     * @return the service type name
     */
    Optional<TypeName> serviceType();

    /**
     * The managed service assigned Scope's.
     *
     * @return the service scope type name
     */
    @Option.Singular
    Set<TypeName> scopes();

    /**
     * The managed service assigned Qualifier's.
     *
     * @return the service qualifiers
     */
    @Option.Singular
    Set<Qualifier> qualifiers();

    /**
     * The managed services advertised types (i.e., typically its interfaces, can be through
     * {@link io.helidon.inject.service.Injection.ExternalContracts}).
     *
     * @return the service contracts implemented
     */
    @Option.Singular
    Set<TypeName> contracts();

    /**
     * The optional {@link io.helidon.inject.service.Injection.RunLevel} ascribed to the service.
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
     * Whether to include abstract type service providers.
     *
     * @return whether to include abstract classes and interfaces
     */
    @Option.DefaultBoolean(false)
    boolean includeAbstract();

    /**
     * Determines whether this service info matches the criteria for injection.
     * Matches is a looser form of equality check than {@code equals()}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    default boolean matches(Lookup criteria) {
        return matchesContracts(criteria)
                && matchesAbstract(includeAbstract(), criteria.includeAbstract())
                && scopes().containsAll(criteria.scopes())
                && Qualifiers.matchesQualifiers(qualifiers(), criteria.qualifiers())
                && matches(runLevel(), criteria.runLevel());
        //                && matchesWeight(this, criteria) -- intentionally not checking weight here!
    }

    /**
     * Determines whether this service info criteria matches the service descriptor.
     * Matches is a looser form of equality check than {@code equals()}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param serviceInfo to compare with
     * @return true if this criteria matches the service descriptor
     */
    default boolean matches(ServiceInfo serviceInfo) {
        if (this == LookupSupport.CustomMethods.EMPTY) {
            return !serviceInfo.isAbstract();
        }

        boolean matches = matches(serviceInfo.serviceType(), this.serviceType());
        if (matches && this.serviceType().isEmpty()) {
            matches = serviceInfo.contracts().containsAll(this.contracts())
                    || this.contracts().contains(serviceInfo.serviceType());
        }
        return matches
                && matchesAbstract(includeAbstract(), serviceInfo.isAbstract())
                && serviceInfo.scopes().containsAll(this.scopes())
                && Qualifiers.matchesQualifiers(serviceInfo.qualifiers(), this.qualifiers())
                && matchesWeight(serviceInfo, this)
                && matches(serviceInfo.runLevel(), this.runLevel());
    }

    /**
     * Determines whether the provided criteria match just the contracts portion of the provided criteria. Note that
     * it is expected any external contracts have been consolidated into the regular contract section.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance from only the contracts point of view
     */
    default boolean matchesContracts(Lookup criteria) {
        if (criteria == LookupSupport.CustomMethods.EMPTY) {
            return true;
        }

        boolean matches = matches(serviceType(), criteria.serviceType());
        if (matches && criteria.serviceType().isEmpty()) {
            matches = contracts().containsAll(criteria.contracts());
        }
        return matches;
    }

    private static boolean matchesWeight(ServiceInfo src,
                                         LookupBlueprint criteria) {
        if (criteria.weight().isEmpty()) {
            return true;
        }

        Double srcWeight = src.weight();
        return (srcWeight.compareTo(criteria.weight().get()) <= 0);
    }

    private static boolean matches(Object src,
                                   Optional<?> criteria) {

        return criteria.map(o -> Objects.equals(src, o)).orElse(true);

    }

    private boolean matchesAbstract(boolean criteriaAbstract, boolean isAbstract) {
        if (criteriaAbstract) {
            return true;
        }
        return !isAbstract;
    }
}
