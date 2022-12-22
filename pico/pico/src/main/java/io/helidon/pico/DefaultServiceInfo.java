/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.helidon.pico.types.AnnotationAndValue;

/**
 * The default/reference implementation for {@link ServiceInfo}.
 */
public class DefaultServiceInfo implements ServiceInfo {

    private final String serviceTypeName;
    private final Set<String> contractsImplemented;
    private final Set<String> externalContractsImplemented;
    private final Set<String> scopeTypeNames;
    private final Set<QualifierAndValue> qualifiers;
    private final String activatorTypeName;
    private final int runLevel;
    private final Double weight;
    private final String moduleName;

    /**
     * Copy constructor.
     *
     * @param src the source to copy
     */
    protected DefaultServiceInfo(ServiceInfo src) {
        this.serviceTypeName = src.serviceTypeName();
        this.contractsImplemented = new TreeSet<>(src.contractsImplemented());
        this.externalContractsImplemented = new LinkedHashSet<>(src.externalContractsImplemented());
        this.scopeTypeNames = new LinkedHashSet<>(src.scopeTypeNames());
        this.qualifiers = new LinkedHashSet<>(src.qualifiers());
        this.activatorTypeName = src.activatorTypeName();
        this.runLevel = src.runLevel();
        this.moduleName = src.moduleName().orElse(null);
        this.weight = src.declaredWeight().orElse(null);
    }

    /**
     * Constructor using the builder result.
     *
     * @param b the builder
     * @see #builder()
     */
    @SuppressWarnings("unchecked")
    protected DefaultServiceInfo(Builder b) {
        this.serviceTypeName = b.serviceTypeName;
        this.contractsImplemented = Collections.unmodifiableSet(new TreeSet<String>(b.contractsImplemented));
        this.externalContractsImplemented = Collections.unmodifiableSet(b.externalContractsImplemented);
        this.scopeTypeNames = Collections.unmodifiableSet(b.scopeTypeNames);
        this.qualifiers = Collections.unmodifiableSet(b.qualifiers);
        this.activatorTypeName = b.activatorTypeName;
        this.runLevel = b.runLevel;
        this.moduleName = b.moduleName;
        this.weight = b.weight;
    }

    /**
     * Provides a facade over {@link java.util.Objects#equals(Object, Object)}.
     *
     * @param o1 an object
     * @param o2 an object to compare with a1 for equality
     * @return true if a1 equals a2
     */
    public static boolean equals(Object o1, Object o2) {
        return Objects.equals(o1, o2);
    }

    /**
     * Creates a fluent builder for this type.
     *
     * @return A builder for {@link DefaultServiceInfo}.
     */
    @SuppressWarnings("unchecked")
    public static Builder<? extends DefaultServiceInfo, ? extends Builder<?, ?>> builder() {
        return new Builder();
    }

    @Override
    public Set<String> externalContractsImplemented() {
        return externalContractsImplemented;
    }

    @Override
    public String activatorTypeName() {
        return activatorTypeName;
    }

    @Override
    public Optional<String> moduleName() {
        return Optional.ofNullable(moduleName);
    }

    /**
     * Matches is a looser form of equality check than {@link #equals(Object, Object)}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     * @see Services#lookup(ServiceInfo)
     */
    @Override
    public boolean matches(ServiceInfoCriteria criteria) {
        if (criteria == PicoServices.EMPTY_CRITERIA) {
            return true;
        }

        boolean matches = matches(this.serviceTypeName(), criteria.serviceTypeName());
        if (matches && criteria.serviceTypeName().isEmpty()) {
            matches = this.contractsImplemented().containsAll(criteria.contractsImplemented())
                    || criteria.contractsImplemented().contains(this.serviceTypeName());
        }
        return matches
                && this.scopeTypeNames().containsAll(criteria.scopeTypeNames())
                && matchesQualifiers(this.qualifiers(), criteria.qualifiers())
                && matches(this.activatorTypeName(), criteria.activatorTypeName())
                && matches(this.runLevel(), criteria.runLevel())
                && matchesWeight(this, criteria)
                && matches(this.moduleName(), criteria.moduleName());
    }

    @Override
    public String serviceTypeName() {
        return serviceTypeName;
    }

    @Override
    public Set<String> scopeTypeNames() {
        return scopeTypeNames;
    }

    @Override
    public Set<QualifierAndValue> qualifiers() {
        return qualifiers;
    }

    @Override
    public Set<String> contractsImplemented() {
        return contractsImplemented;
    }

    @Override
    public int runLevel() {
        return runLevel;
    }

    @Override
    public Optional<Double> declaredWeight() {
        return Optional.ofNullable(weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceTypeName(), contractsImplemented());
    }

    @Override
    public boolean equals(Object another) {
        if (!(another instanceof ServiceInfo)) {
            return false;
        }

        return equals(serviceTypeName(), ((ServiceInfo) another).serviceTypeName())
                && equals(contractsImplemented(), ((ServiceInfo) another).contractsImplemented())
                && equals(qualifiers(), ((ServiceInfo) another).qualifiers())
                && equals(activatorTypeName(), ((ServiceInfo) another).activatorTypeName())
                && equals(runLevel(), ((ServiceInfo) another).runLevel())
                && equals(realizedWeight(), ((ServiceInfo) another).realizedWeight())
                && equals(moduleName(), ((ServiceInfo) another).moduleName());
    }

    /**
     * Creates a fluent builder initialized with the current values of this instance.
     *
     * @return A builder initialized with the current attributes.
     */
    @SuppressWarnings("unchecked")
    public Builder<? extends DefaultServiceInfo, ? extends Builder<?, ?>> toBuilder() {
        return new Builder(this);
    }

    /**
     * Weight matching is always less or equal to criteria specified.
     *
     * @param src      the item being considered
     * @param criteria the criteria
     * @return true if there is a match
     */
    protected static boolean matchesWeight(ServiceInfoBasics src, ServiceInfoCriteria criteria) {
        if (criteria.weight().isEmpty()) {
            return true;
        }

        Double srcWeight = src.realizedWeight();
        return (srcWeight.compareTo(criteria.weight().get()) <= 0);
    }

    /**
     * Matches qualifier collections.
     *
     * @param src      the target service info to evaluate
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    private static boolean matchesQualifiers(Set<QualifierAndValue> src, Set<QualifierAndValue> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }

        if (src.isEmpty()) {
            return false;
        }

        if (src.contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
            return true;
        }

        for (QualifierAndValue criteriaQualifier : criteria) {
            if (src.contains(criteriaQualifier)) {
                // NOP;
                continue;
            } else if (criteriaQualifier.typeName().equals(DefaultQualifierAndValue.NAMED)) {
                if (criteriaQualifier.equals(DefaultQualifierAndValue.WILDCARD_NAMED)
                        || criteriaQualifier.value().isEmpty()) {
                    // any Named qualifier will match ...
                    boolean hasSameTypeAsCriteria = src.stream()
                            .anyMatch(q -> q.typeName().equals(criteriaQualifier.typeName()));
                    if (hasSameTypeAsCriteria) {
                        continue;
                    }
                } else if (src.contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
                    continue;
                }
                return false;
            } else if (criteriaQualifier.value().isEmpty()) {
                Set<AnnotationAndValue> sameTypeAsCriteriaSet = src.stream()
                        .filter(q -> q.typeName().equals(criteriaQualifier.typeName()))
                        .collect(Collectors.toSet());
                if (sameTypeAsCriteriaSet.isEmpty()) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    private static boolean matches(Object src, Optional<?> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }

        return equals(src, criteria.get());
    }

    /**
     * The fluent builder for {@link ServiceInfo}.
     *
     * @param <B> the builder type
     * @param <C> the concrete type being build
     */
    public static class Builder<C extends DefaultServiceInfo, B extends Builder<C, B>>
            implements io.helidon.common.Builder<B, C> {
        private final Set<String> contractsImplemented = new LinkedHashSet<>();
        private final Set<String> externalContractsImplemented = new LinkedHashSet<>();
        private final Set<String> scopeTypeNames = new LinkedHashSet<>();
        private final Set<QualifierAndValue> qualifiers = new LinkedHashSet<>();

        private String serviceTypeName;
        private String activatorTypeName;
        private Integer runLevel;
        private String moduleName;
        private Double weight;

        /**
         * Builder Constructor.
         *
         * @see #builder()
         */
        protected Builder() {
        }

        /**
         * Builder Copy Constructor.
         *
         * @param c the existing value object
         * @see #toBuilder()
         */
        protected Builder(C c) {
            this.serviceTypeName = c.serviceTypeName();
            this.contractsImplemented.addAll(c.contractsImplemented());
            this.externalContractsImplemented.addAll(c.externalContractsImplemented());
            this.scopeTypeNames.addAll(c.scopeTypeNames());
            this.qualifiers.addAll(c.qualifiers());
            this.activatorTypeName = c.activatorTypeName();
            this.runLevel = c.runLevel();
            this.moduleName = c.moduleName().orElse(null);
            this.weight = c.declaredWeight().orElse(null);
        }

        /**
         * Builds the {@link DefaultServiceInfo}.
         *
         * @return the fluent builder instance
         */
        @SuppressWarnings("unchecked")
        public C build() {
            Objects.requireNonNull(serviceTypeName);

            return (C) new DefaultServiceInfo(this);
        }

        /**
         * Sets the mandatory serviceTypeName for this {@link ServiceInfo}.
         *
         * @param serviceTypeName the service type name
         * @return this fluent builder
         */
        public B serviceTypeName(String serviceTypeName) {
            this.serviceTypeName = serviceTypeName;
            return identity();
        }

        /**
         * Sets the mandatory serviceTypeName for this {@link ServiceInfo}.
         *
         * @param serviceType the service type
         * @return this fluent builder
         */
        public B serviceType(Class<?> serviceType) {
            return serviceTypeName(serviceType.getName());
        }

        /**
         * Sets the optional name for this {@link ServiceInfo}.
         *
         * @param name the name
         * @return this fluent builder
         */
        public B named(String name) {
            return addQualifier(DefaultQualifierAndValue.createNamed(name));
        }

        /**
         * Adds a singular qualifier for this {@link ServiceInfo}.
         *
         * @param qualifier the qualifier
         * @return this fluent builder
         */
        public B addQualifier(QualifierAndValue qualifier) {
            Objects.requireNonNull(qualifier);
            qualifiers.add(qualifier);
            return identity();
        }

        /**
         * Sets the collection of qualifiers for this {@link ServiceInfo}.
         *
         * @param qualifiers the qualifiers
         * @return this fluent builder
         */
        public B qualifiers(Collection<QualifierAndValue> qualifiers) {
            Objects.requireNonNull(qualifiers);
            qualifiers.clear();
            this.qualifiers.addAll(qualifiers);
            return identity();
        }

        /**
         * Adds a singular contract implemented for this {@link ServiceInfo}.
         *
         * @param contractImplemented the contract implemented
         * @return this fluent builder
         */
        public B contractImplemented(String contractImplemented) {
            Objects.requireNonNull(contractImplemented);
            contractsImplemented.add(contractImplemented);
            return identity();
        }

        /**
         * Adds a contract implemented.
         *
         * @param contract the contract type
         * @return this fluent builder
         */
        public B contractTypeImplemented(Class<?> contract) {
            return contractImplemented(contract.getName());
        }

        /**
         * Sets the collection of contracts implemented for this {@link ServiceInfo}.
         *
         * @param contractsImplemented the contract names implemented
         * @return this fluent builder
         */
        public B contractsImplemented(Collection<String> contractsImplemented) {
            Objects.requireNonNull(contractsImplemented);
            this.contractsImplemented.clear();
            this.contractsImplemented.addAll(contractsImplemented);
            return identity();
        }

        /**
         * Adds a singular external contract implemented for this {@link ServiceInfo}.
         *
         * @param contractImplemented the type name of the external contract implemented
         * @return this fluent builder
         */
        public B addExternalContractImplemented(String contractImplemented) {
            Objects.requireNonNull(contractImplemented);
            this.externalContractsImplemented.add(contractImplemented);
            return contractImplemented(contractImplemented);
        }

        /**
         * Adds an external contract implemented.
         *
         * @param contract the external contract type
         * @return this fluent builder
         */
        public B externalContractTypeImplemented(Class<?> contract) {
            return addExternalContractImplemented(contract.getName());
        }

        /**
         * Sets the collection of contracts implemented for this {@link ServiceInfo}.
         *
         * @param contractsImplemented the external contract names implemented
         * @return this fluent builder
         */
        public B externalContractsImplemented(Collection<String> contractsImplemented) {
            Objects.requireNonNull(contractsImplemented);
            this.externalContractsImplemented.clear();
            this.externalContractsImplemented.addAll(contractsImplemented);
            return identity();
        }

        /**
         * Adds a singular scope type name for this {@link ServiceInfo}.
         *
         * @param scopeTypeName the scope type name
         * @return this fluent builder
         */
        public B scopeTypeName(String scopeTypeName) {
            Objects.requireNonNull(scopeTypeName);
            this.scopeTypeNames.add(scopeTypeName);
            return identity();
        }

        /**
         * Sets the scope type.
         *
         * @param scopeType the scope type
         * @return this fluent builder
         */
        public B scopeType(Class<?> scopeType) {
            return scopeTypeName(scopeType.getName());
        }

        /**
         * sets the collection of scope type names declared for this {@link ServiceInfo}.
         *
         * @param scopeTypeNames the contract names implemented
         * @return this fluent builder
         */
        public B scopeTypeNames(Collection<String> scopeTypeNames) {
            Objects.requireNonNull(scopeTypeNames);
            this.scopeTypeNames.clear();
            this.scopeTypeNames.addAll(scopeTypeNames);
            return identity();
        }

        /**
         * Sets the activator type name.
         *
         * @param activatorTypeName the activator type name
         * @return this fluent builder
         */
        public B activatorTypeName(String activatorTypeName) {
            this.activatorTypeName = activatorTypeName;
            return identity();
        }

        /**
         * Sets the activator type.
         *
         * @param activatorType the activator type
         * @return this fluent builder
         */
        public B activatorType(Class<?> activatorType) {
            return activatorTypeName(activatorType.getName());
        }

        /**
         * Sets the run level value.
         *
         * @param runLevel the run level
         * @return this fluent builder
         */
        public B runLevel(Integer runLevel) {
            this.runLevel = runLevel;
            return identity();
        }

        /**
         * Sets the module name value.
         *
         * @param moduleName the module name
         * @return this fluent builder
         */
        public B moduleName(String moduleName) {
            this.moduleName = moduleName;
            return identity();
        }

        /**
         * Sets the weight value.
         *
         * @param weight the weight (aka priority)
         * @return this fluent builder
         */
        public B weight(Double weight) {
            this.weight = weight;
            return identity();
        }
    }

}
