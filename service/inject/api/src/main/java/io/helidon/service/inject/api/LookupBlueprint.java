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

package io.helidon.service.inject.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

/**
 * Lookup criteria to discover services, mostly used when interacting with a service registry.
 */
@Prototype.Blueprint(createEmptyPublic = false)
@Prototype.CustomMethods(LookupSupport.CustomMethods.class)
interface LookupBlueprint {

    /**
     * The managed service implementation type name.
     *
     * @return the service type name
     */
    Optional<TypeName> serviceType();

    /**
     * The managed service assigned Scope. If empty, any scope is matched.
     * If more than one value, any service in one of these scopes is matched.
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
     * {@link io.helidon.service.registry.Service.ExternalContracts}).
     *
     * @return the service contracts implemented
     */
    @Option.Singular
    Set<ResolvedType> contracts();

    /**
     * A single {@link io.helidon.common.GenericType} can be defined if the lookup should also honor
     * {@link io.helidon.service.inject.api.Injection.QualifiedFactory} services that can handle any type.
     * This would be the target type to convert to. If not specified, Object will be used.
     *
     * @return generic type of the contract, if only one contract is desired
     */
    @Option.Decorator(LookupSupport.GenericTypeDecorator.class)
    Optional<GenericType<?>> contractType();

    /**
     * The optional {@link Injection.RunLevel} ascribed to the service.
     *
     * @return the service's run level
     */
    Optional<Double> runLevel();

    /**
     * Weight that was declared on the type itself.
     *
     * @return the declared weight
     */
    Optional<Double> weight();

    /**
     * Whether to include abstract type service descriptors.
     *
     * @return whether to include abstract classes and interfaces
     */
    @Option.DefaultBoolean(false)
    boolean includeAbstract();

    /**
     * Optionally, the injection point search applies to.
     * There are some service factories (such as
     * {@link io.helidon.service.inject.api.Injection.InjectionPointFactory}) that
     * provide instances for a specific injection point.
     * Such factories may require an injection point to be present, and may fail otherwise.
     * <p>
     * Injection points of each service are generated as public constants on their respective service descriptors.
     *
     * @return the optional injection point context info
     */
    @Option.Decorator(LookupSupport.IpDecorator.class)
    Optional<Ip> injectionPoint();

    /**
     * If configured, the lookup will return service factories of the
     * chosen types.
     * If no factory types are defined, service instances are returned.
     * <p>
     * Otherwise only service factories of the chosen types are returned, as follows:
     * <ul>
     *     <li>{@link FactoryType#SERVICE} - only services that directly implement the
     *     contract</li>
     *     <li>{@link FactoryType#SUPPLIER} - only that are {@link java.util.function.Supplier}
     *     of the contract</li>
     *     <li>{@link FactoryType#QUALIFIED} - services that are
     *     {@link io.helidon.service.inject.api.Injection.QualifiedFactory} of the contract</li>
     *     <li>{@link FactoryType#SERVICES} - services that are
     *     {@link io.helidon.service.inject.api.Injection.ServicesFactory} of the contract</li>
     *     <li>{@link FactoryType#INJECTION_POINT} - services that are
     *     {@link io.helidon.service.inject.api.Injection.InjectionPointFactory} of the contract</li>
     *     <li>{@link FactoryType#NONE} - this has no effect and will not modify the lookup</li>
     * </ul>
     *
     * @return desired factory types
     */
    @Option.Singular
    Set<FactoryType> factoryTypes();

    /**
     * Determines whether this lookup matches the criteria for injection.
     * Matches is a looser form of equality check than {@code equals()}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    default boolean matches(Lookup criteria) {
        return matchesContracts(criteria)
                && matchesAbstract(includeAbstract(), criteria.includeAbstract())
                && matchesTypes(scopes(), criteria.scopes())
                && Qualifiers.matchesQualifiers(qualifiers(), criteria.qualifiers())
                && matchesOptionals(runLevel(), criteria.runLevel());
    }

    /**
     * Determines whether this service info criteria matches the service descriptor.
     * Matches is a looser form of equality check than {@code equals()}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param serviceInfo to compare with
     * @return true if this criteria matches the service descriptor
     */
    default boolean matches(InjectServiceInfo serviceInfo) {
        if (this == LookupSupport.CustomMethods.EMPTY) {
            return !serviceInfo.isAbstract();
        }

        boolean matches = matches(serviceInfo.serviceType(), this.serviceType());
        if (matches && this.serviceType().isEmpty()) {
            matches = serviceInfo.contracts().containsAll(this.contracts())
                    || this.contracts().contains(ResolvedType.create(serviceInfo.serviceType()))
                    || serviceInfo.factoryContracts().containsAll(this.contracts());
        }
        return matches
                && matchesProviderTypes(factoryTypes(), serviceInfo.factoryType())
                && matchesAbstract(includeAbstract(), serviceInfo.isAbstract())
                && (this.scopes().isEmpty() || this.scopes().contains(serviceInfo.scope()))
                && Qualifiers.matchesQualifiers(serviceInfo.qualifiers(), this.qualifiers())
                && matchesWeight(serviceInfo, this)
                && matchesOptionals(serviceInfo.runLevel(), this.runLevel());
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

        boolean matches = matchesOptionals(serviceType(), criteria.serviceType());
        if (matches && criteria.serviceType().isEmpty()) {
            matches = contracts().containsAll(criteria.contracts());
        }
        return matches;
    }

    /**
     * Determines whether the provided qualifiers are matched by this lookup criteria.
     *
     * @param qualifiers qualifiers of a service
     * @return whether this lookup matches those qualifiers
     */
    default boolean matchesQualifiers(Set<Qualifier> qualifiers) {
        return Qualifiers.matchesQualifiers(qualifiers, qualifiers());
    }

    private static boolean matchesWeight(InjectServiceInfo src,
                                         LookupBlueprint criteria) {
        if (criteria.weight().isEmpty()) {
            return true;
        }

        Double srcWeight = src.weight();
        return (srcWeight.compareTo(criteria.weight().get()) <= 0);
    }

    private static boolean matches(Object src,
                                   Optional<?> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }
        return Objects.equals(src, criteria.get());
    }

    private boolean matchesProviderTypes(Set<FactoryType> providerTypes, FactoryType providerType) {
        if (providerTypes.isEmpty() || (providerTypes.size() == 1 && providerTypes.contains(FactoryType.NONE))) {
            return true;
        }
        return providerTypes.contains(providerType);
    }

    private boolean matchesTypes(Set<TypeName> scopes, Set<TypeName> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }
        for (TypeName scope : scopes) {
            if (criteria.contains(scope)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOptionals(Optional<?> src, Optional<?> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }
        return src.map(it -> Objects.equals(it, criteria.get()))
                .orElse(false);
    }

    private boolean matchesAbstract(boolean criteriaAbstract, boolean isAbstract) {
        if (criteriaAbstract) {
            return true;
        }
        return !isAbstract;
    }
}
