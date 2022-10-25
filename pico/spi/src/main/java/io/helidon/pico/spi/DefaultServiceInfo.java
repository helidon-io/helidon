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

package io.helidon.pico.spi;

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
    private final Integer runLevel;
    private final Double weight;
    private String moduleName;

    /**
     * Copy ctor.
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
     * Builder ctor.
     *
     * @param b the builder
     * @see #builder()
     */
    @SuppressWarnings("unchecked")
    protected DefaultServiceInfo(Builder b) {
        this.serviceTypeName = b.serviceTypeName;
        this.contractsImplemented = Objects.isNull(b.contractsImplemented)
                ? Collections.emptySet() : Collections.unmodifiableSet(new TreeSet<String>(b.contractsImplemented));
        this.externalContractsImplemented = Objects.isNull(b.externalContractsImplemented)
                ? Collections.emptySet() : Collections.unmodifiableSet(b.externalContractsImplemented);
        this.scopeTypeNames = Objects.isNull(b.scopeTypeNames)
                ? Collections.emptySet() : Collections.unmodifiableSet(b.scopeTypeNames);
        this.qualifiers = Objects.isNull(b.qualifiers)
                ? Collections.emptySet() : Collections.unmodifiableSet(b.qualifiers);
        this.activatorTypeName = b.activatorTypeName;
        this.runLevel = b.runLevel;
        this.moduleName = b.moduleName;
        this.weight = b.weight;
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
    public Integer runLevel() {
        return runLevel;
    }

    @Override
    public Optional<Double> declaredWeight() {
        return Optional.ofNullable(weight);
    }


    @Override
    public int hashCode() {
        if (Objects.isNull(serviceTypeName)) {
            return Objects.hashCode(contractsImplemented());
        }

        return Objects.hashCode(serviceTypeName()) ^ Objects.hashCode(contractsImplemented());
    }

    @Override
    public boolean equals(Object another) {
        if (Objects.isNull(another) || !(another instanceof ServiceInfo)) {
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
     * Set/override the module name for this service info. Generally this is only called internally by the framework.
     *
     * @param moduleName the module name
     */
    public void moduleName(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * Matches is a looser form of equality check than {@link #equals(Object, Object)}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     * @see io.helidon.pico.spi.Services#lookup(ServiceInfo)
     */
    @Override
    public boolean matches(ServiceInfo criteria) {
        return matches(this, criteria);
    }

    /**
     * Matches is a looser form of equality check than {@link #equals(Object, Object)}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param src the target service info to evaluate
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     * @see io.helidon.pico.spi.Services#lookup(ServiceInfo)
     */
    protected static boolean matches(ServiceInfo src, ServiceInfo criteria) {
        if (Objects.isNull(criteria)) {
            return true;
        }

        boolean matches = matches(src.serviceTypeName(), criteria.serviceTypeName());
        if (matches && Objects.isNull(criteria.serviceTypeName())) {
            matches = matches(src.contractsImplemented(), criteria.contractsImplemented())
                    || matches(src.serviceTypeName(), criteria.contractsImplemented());
        }
        return matches
                && matches(src.scopeTypeNames(), criteria.scopeTypeNames())
                && matchesQualifiers(src.qualifiers(), criteria.qualifiers())
                && matches(src.activatorTypeName(), criteria.activatorTypeName())
                && matches(src.runLevel(), criteria.runLevel())
                && matchesWeight(src, criteria.declaredWeight().orElse(null))
                && matches(src.moduleName(), criteria.moduleName());
    }

    /**
     * Matches qualifier collections.
     *
     * @param src the target service info to evaluate
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    public static boolean matchesQualifiers(Set<QualifierAndValue> src, Set<QualifierAndValue> criteria) {
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

    /**
     * Weight matching is always less than any criteria specified.
     *
     * @param src the item being considered
     * @param criteria the criteria
     *
     * @return true if there is a match
     */
    protected static boolean matchesWeight(ServiceInfoBasics src, Double criteria) {
        if (Objects.isNull(criteria)) {
            return matches(src.declaredWeight().orElse(null), criteria);
        }
        Double srcWeight = weightOf(src);
        return (srcWeight.compareTo(criteria) < 0);
    }

    /**
     * Resolves the literal weight of a {@link io.helidon.pico.spi.ServiceInfoBasics} that is passed.
     *
     * @param src the weight to calculate for
     * @return the weight of the service info argument
     */
    public static double weightOf(ServiceInfoBasics src) {
        return Objects.isNull(src) ? DEFAULT_WEIGHT : src.realizedWeight();
    }

    private static boolean matches(Set<?> src, Set<?> criteria) {
        if (Objects.isNull(criteria) || criteria.isEmpty()) {
            return true;
        }

        if (Objects.isNull(src)) {
            return false;
        }

        return src.containsAll(criteria);
    }

    private static boolean matches(String src, Set<String> criteria) {
        if (Objects.isNull(criteria)) {
            return true;
        }

        if (Objects.isNull(src)) {
            return false;
        }

        return criteria.contains(src);
    }

    private static boolean matches(Object src, Object criteria) {
        if (Objects.isNull(criteria)) {
            return true;
        }

        return equals(src, criteria);
    }

    /**
     * Clone a service info and wrap it using {@link DefaultServiceInfo}.
     *
     * @param src the target to clone
     * @return the cloned copy of the provided service info
     */
    public static DefaultServiceInfo cloneCopy(ServiceInfo src) {
        if (Objects.isNull(src)) {
            return null;
        }

        return new DefaultServiceInfo(src);
    }

    /**
     * Constructs an instance of {@link DefaultServiceInfo} with the provided serviceInfo that
     * describes the instance provided.
     *
     * @param instance      the instance provided
     * @param serviceInfo   the service info that describes the instance provided
     * @return the {@link DefaultServiceInfo} instance that identifies the service instance and info
     */
    public static DefaultServiceInfo toServiceInfo(Object instance, ServiceInfo serviceInfo) {
        if (Objects.nonNull(serviceInfo)) {
            return cloneCopy(serviceInfo);
        }
        return DefaultServiceInfo.builder()
                .serviceTypeName(instance.getClass().getName())
                .build();
    }

    /**
     * Constructs an instance of {@link DefaultServiceInfo} given a service type class and some
     * basic information that describes the service type.
     *
     * @param serviceType the service type
     * @param siBasics the basic information that describes the service type
     * @return an instance of {@link DefaultServiceInfo}
     */
    public static DefaultServiceInfo toServiceInfoFromClass(Class<?> serviceType, ServiceInfoBasics siBasics) {
        if (siBasics instanceof DefaultServiceInfo) {
            assert (serviceType.getName().equals(siBasics.serviceTypeName()));
            return (DefaultServiceInfo) siBasics;
        }

        if (Objects.isNull(siBasics)) {
            return DefaultServiceInfo.builder()
                    .serviceTypeName(serviceType.getName())
                    .build();
        }

        return DefaultServiceInfo.builder()
                .serviceTypeName(serviceType.getName())
                .scopeTypeNames(siBasics.scopeTypeNames())
                .contractsImplemented(siBasics.contractsImplemented())
                .qualifiers(siBasics.qualifiers())
                .runLevel(siBasics.runLevel())
                .weight(siBasics.declaredWeight().orElse(null))
                .build();
    }


    /**
     * Creates a fluent builder for this type.
     *
     * @return A builder for {@link io.helidon.pico.spi.DefaultServiceInfo}.
     */
    @SuppressWarnings("unchecked")
    public static Builder<? extends DefaultServiceInfo, ? extends Builder<?, ?>> builder() {
        return new Builder();
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
     * The fluent builder for {@link ServiceInfo}.
     *
     * @param <B> the builder type
     * @param <C> the concrete type being build
     */
    public static class Builder<C extends DefaultServiceInfo, B extends Builder<C, B>> {
        private String serviceTypeName;
        private Set<String> contractsImplemented;
        private Set<String> externalContractsImplemented;
        private Set<String> scopeTypeNames;
        private Set<QualifierAndValue> qualifiers;
        private String activatorTypeName;
        private Integer runLevel;
        private String moduleName;
        private Double weight;

        /**
         * Ctor.
         *
         * @see #builder()
         */
        protected Builder() {
        }

        /**
         * Ctor.
         *
         * @param c the existing value object
         * @see #toBuilder()
         */
        protected Builder(C c) {
            this.serviceTypeName = c.serviceTypeName();
            this.contractsImplemented = new LinkedHashSet<>(c.contractsImplemented());
            this.externalContractsImplemented = new LinkedHashSet<>(c.externalContractsImplemented());
            this.scopeTypeNames = new LinkedHashSet<>(c.scopeTypeNames());
            this.qualifiers = new LinkedHashSet<>(c.qualifiers());
            this.activatorTypeName = c.activatorTypeName();
            this.runLevel = c.runLevel();
            this.moduleName = c.moduleName().orElse(null);
            this.weight = c.declaredWeight().orElse(null);
        }

        /**
         * Builds the {@link io.helidon.pico.spi.DefaultServiceInfo}.
         *
         * @return the fluent builder instance
         */
        @SuppressWarnings("unchecked")
        public C build() {
            return (C) new DefaultServiceInfo(this);
        }

        @SuppressWarnings("unchecked")
        private B identity() {
            return (B) this;
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
            return Objects.isNull(name) ? identity() : qualifier(DefaultQualifierAndValue.createNamed(name));
        }

        /**
         * Adds a singular qualifier for this {@link ServiceInfo}.
         *
         * @param qualifier the qualifier
         * @return this fluent builder
         */
        public B qualifier(QualifierAndValue qualifier) {
            if (Objects.nonNull(qualifier)) {
                if (Objects.isNull(qualifiers)) {
                    qualifiers = new LinkedHashSet<>();
                }
                qualifiers.add(qualifier);
            }
            return identity();
        }

        /**
         * Sets the collection of qualifiers for this {@link ServiceInfo}.
         *
         * @param qualifiers the qualifiers
         * @return this fluent builder
         */
        public B qualifiers(Collection<QualifierAndValue> qualifiers) {
            this.qualifiers = Objects.isNull(qualifiers)
                    ? null : new LinkedHashSet<>(qualifiers);
            return identity();
        }

        /**
         * Adds a singular contract implemented for this {@link ServiceInfo}.
         *
         * @param contractImplemented the contract implemented
         * @return this fluent builder
         */
        public B contractImplemented(String contractImplemented) {
            if (Objects.nonNull(contractImplemented)) {
                if (Objects.isNull(contractsImplemented)) {
                    contractsImplemented = new LinkedHashSet<>();
                }
                contractsImplemented.add(contractImplemented);
            }
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
            this.contractsImplemented = Objects.isNull(contractsImplemented)
                    ? null : new LinkedHashSet<>(contractsImplemented);
            return identity();
        }

        /**
         * Adds a singular external contract implemented for this {@link ServiceInfo}.
         *
         * @param contractImplemented the type name of the external contract implemented
         * @return this fluent builder
         */
        public B externalContractImplemented(String contractImplemented) {
            if (Objects.nonNull(contractImplemented)) {
                if (Objects.isNull(this.externalContractsImplemented)) {
                    this.externalContractsImplemented = new LinkedHashSet<>();
                }
                this.externalContractsImplemented.add(contractImplemented);
            }
            return contractImplemented(contractImplemented);
        }

        /**
         * Adds an external contract implemented.
         *
         * @param contract the external contract type
         * @return this fluent builder
         */
        public B externalContractTypeImplemented(Class<?> contract) {
            return externalContractImplemented(contract.getName());
        }

        /**
         * Sets the collection of contracts implemented for this {@link ServiceInfo}.
         *
         * @param contractsImplemented the external contract names implemented
         * @return this fluent builder
         */
        public B externalContractsImplemented(Collection<String> contractsImplemented) {
            this.externalContractsImplemented = Objects.isNull(contractsImplemented)
                    ? null : new LinkedHashSet<>(contractsImplemented);
            return identity();
        }

        /**
         * Adds a singular scope type name for this {@link ServiceInfo}.
         *
         * @param scopeTypeName the scope type name
         * @return this fluent builder
         */
        public B scopeTypeName(String scopeTypeName) {
            if (Objects.nonNull(scopeTypeName)) {
                if (Objects.isNull(this.scopeTypeNames)) {
                    this.scopeTypeNames = new LinkedHashSet<>();
                }
                this.scopeTypeNames.add(scopeTypeName);
            }
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
            this.scopeTypeNames = Objects.isNull(scopeTypeNames)
                    ? null : new LinkedHashSet<>(scopeTypeNames);
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
