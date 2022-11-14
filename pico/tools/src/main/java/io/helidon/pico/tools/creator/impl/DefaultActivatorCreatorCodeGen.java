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

package io.helidon.pico.tools.creator.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.pico.Application;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.tools.creator.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.processor.ServicesToProcess;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

import static io.helidon.pico.types.DefaultTypeName.create;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ActivatorCreatorCodeGen}.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultActivatorCreatorCodeGen implements ActivatorCreatorCodeGen {
    /*@Singular("serviceTypeParent")*/ private final Map<TypeName, TypeName> serviceTypeToParentServiceTypes;
    /*@Singular("serviceTypeParent")*/ private final Map<TypeName, String> serviceTypeToActivatorGenericDecl;
    /*@Singular("serviceTypeAccessLevel")*/ private final Map<TypeName, InjectionPointInfo.Access> serviceTypeAccessLevels;
    /*@Singular("serviceTypeIsAbstractType")*/ private final Map<TypeName, Boolean> serviceTypeIsAbstractTypes;
    /*@Singular("serviceTypeContract")*/ private final Map<TypeName, Set<TypeName>> serviceTypeContracts;
    /*@Singular("serviceTypeExternalContract")*/ private final Map<TypeName, Set<TypeName>> serviceTypeExternalContracts;
    /*@Singular("serviceTypeInjectionPointDependency")*/ private final Map<TypeName, Dependencies>
            serviceTypeInjectionPointDependencies;
    /*@Singular("serviceTypePreDestroyMethodName")*/ private final Map<TypeName, String> serviceTypePreDestroyMethodNames;
    /*@Singular("serviceTypePostConstructMethodName")*/ private final Map<TypeName, String> serviceTypePostConstructMethodNames;
    /*@Singular("serviceTypeWeightedPriority")*/ private final Map<TypeName, Double> serviceTypeWeightedPriorities;
    /*@Singular("serviceTypeRunLevel")*/ private final Map<TypeName, Integer> serviceTypeRunLevels;
    /*@Singular("serviceTypeScopeName")*/ private final Map<TypeName, Set<String>> serviceTypeScopeNames;
    /*@Singular("serviceTypeQualifier")*/ private final Map<TypeName, Set<QualifierAndValue>> serviceTypeQualifiers;
    /*@Singular("serviceTypeProviderForType")*/ private final Map<TypeName, Set<TypeName>> serviceTypeToProviderForTypes;
    private final Map<TypeName, List<TypeName>> serviceTypeHierarchy;
    private final Map<TypeName, InterceptionPlan> serviceTypeInterceptionPlan;
    private final Map<TypeName, List<String>> extraCodeGen;
    /*@Singular("moduleRequired")*/ private final Set<String> modulesRequired;
    private final String classPrefixName;

    protected DefaultActivatorCreatorCodeGen(DefaultActivatorCreatorCodeGenBuilder builder) {
        this.serviceTypeToParentServiceTypes = Objects.isNull(builder.serviceTypeToParentServiceTypes)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeToParentServiceTypes);
        this.serviceTypeToActivatorGenericDecl = Objects.isNull(builder.serviceTypeToActivatorGenericDecl)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeToActivatorGenericDecl);
        this.serviceTypeAccessLevels = Objects.isNull(builder.serviceTypeAccessLevels)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeAccessLevels);
        this.serviceTypeIsAbstractTypes = Objects.isNull(builder.serviceTypeIsAbstractTypes)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeIsAbstractTypes);
        this.serviceTypeContracts = Objects.isNull(builder.serviceTypeContracts)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeContracts);
        this.serviceTypeExternalContracts = Objects.isNull(builder.serviceTypeExternalContracts)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeExternalContracts);
        this.serviceTypeInjectionPointDependencies = Objects.isNull(builder.serviceTypeInjectionPointDependencies)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeInjectionPointDependencies);
        this.serviceTypePreDestroyMethodNames = Objects.isNull(builder.serviceTypePreDestroyMethodNames)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypePreDestroyMethodNames);
        this.serviceTypePostConstructMethodNames = Objects.isNull(builder.serviceTypePostConstructMethodNames)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypePostConstructMethodNames);
        this.serviceTypeWeightedPriorities = Objects.isNull(builder.serviceTypeWeightedPriorities)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeWeightedPriorities);
        this.serviceTypeRunLevels = Objects.isNull(builder.serviceTypeRunLevels)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeRunLevels);
        this.serviceTypeScopeNames = Objects.isNull(builder.serviceTypeScopeNames)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeScopeNames);
        this.serviceTypeQualifiers = Objects.isNull(builder.serviceTypeQualifiers)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeQualifiers);
        this.serviceTypeToProviderForTypes = Objects.isNull(builder.serviceTypeToProviderForTypes)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeToProviderForTypes);
        this.serviceTypeHierarchy = Objects.isNull(builder.serviceTypeHierarchy)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeHierarchy);
        this.serviceTypeInterceptionPlan = Objects.isNull(builder.serviceTypeInterceptionPlan)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeInterceptionPlan);
        this.extraCodeGen = Objects.isNull(builder.extraCodeGen)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.extraCodeGen);
        this.modulesRequired = Objects.isNull(builder.modulesRequired)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.modulesRequired);
        this.classPrefixName = builder.classPrefixName;
    }

    /**
     * @return A builder for {@link io.helidon.pico.spi.impl.DefaultInjectionPointInfo}.
     */
    public static DefaultActivatorCreatorCodeGenBuilder<? extends DefaultActivatorCreatorCodeGen,
                                                                ? extends DefaultActivatorCreatorCodeGenBuilder<?, ?>>
    builder() {
        return new DefaultActivatorCreatorCodeGenBuilder() { };
    }

    /**
     * Creates a payload given the batch of services to process.
     *
     * @param services the services to process.
     * @return the payload, or null if unable or nothing to process
     */
    public static DefaultActivatorCreatorCodeGen toActivatorCreatorCodeGen(ServicesToProcess services) {
        // do not generate activators for modules or applications...
        List<TypeName> serviceTypeNames = services.getServiceTypeNames();
        if (!serviceTypeNames.isEmpty()) {
            TypeName applicationTypeName = create(Application.class);
            TypeName moduleTypeName = create(Application.class);
            serviceTypeNames = serviceTypeNames.stream()
                    .filter(typeName -> {
                        Set<TypeName> contracts = services.getServicesToContracts().get(typeName);
                        if (Objects.isNull(contracts)) {
                            return true;
                        }
                        return !contracts.contains(applicationTypeName) && !contracts.contains(moduleTypeName);
                    })
                    .collect(Collectors.toList());
        }
        if (serviceTypeNames.isEmpty()) {
            return null;
        }

        return DefaultActivatorCreatorCodeGen.builder()
                .serviceTypeToParentServiceTypes(toFilteredParentServiceTypes(services))
                .serviceTypeToActivatorGenericDecl(services.getServiceTypeToActivatorGenericDecl())
                .serviceTypeHierarchy(toFilteredHierarchy(services))
                .serviceTypeAccessLevels(services.getServiceTypeToAccessLevel())
                .serviceTypeIsAbstractTypes(services.getServiceTypeToIsAbstract())
                .serviceTypeContracts(toFilteredContracts(services))
                .serviceTypeExternalContracts(services.getServicesToExternalContracts())
                .serviceTypeInjectionPointDependencies(services.getServicesToInjectionPointDependencies())
                .serviceTypePostConstructMethodNames(services.getServicesToPostConstructMethodNames())
                .serviceTypePreDestroyMethodNames(services.getServicesToPreDestroyMethodNames())
                .serviceTypeWeightedPriorities(services.getServicesToWeightedPriorities())
                .serviceTypeRunLevels(services.getServicesToRunLevels())
                .serviceTypeScopeNames(services.getServicesToScopeTypeNames())
                .serviceTypeToProviderForTypes(services.getServicesToProviderFor())
                .serviceTypeQualifiers(services.getQualifiers())
                .modulesRequired(services.getRequiredModules())
                .classPrefixName(services.getLastKnownTypeSuffix())
                .serviceTypeInterceptionPlan(services.getInterceptorPlans())
                .extraCodeGen(services.getExtraCodeGen())
                .build();
    }

    private static Map<? extends TypeName, ? extends TypeName> toFilteredParentServiceTypes(ServicesToProcess services) {
        Map<TypeName, TypeName> parents = services.getServiceTypeToParentServiceTypes();
        Map<TypeName, TypeName> filteredParents = new LinkedHashMap<>(parents);
        for (Map.Entry<TypeName, TypeName> e : parents.entrySet()) {
            if (Objects.nonNull(e.getValue())
                    && !services.getServiceTypeNames().contains(e.getValue())
                    // if the caller is declaring a parent with generics, then assume they know what they are doing
                    && !e.getValue().fqName().contains("<")) {
                TypeName serviceTypeName = e.getKey();
                if (Objects.isNull(services.getServiceTypeToActivatorGenericDecl().get(serviceTypeName))) {
                    filteredParents.put(e.getKey(), null);
                }
            }
        }
        return filteredParents;
    }

    private static Map<TypeName, List<TypeName>> toFilteredHierarchy(ServicesToProcess services) {
        Map<TypeName, List<TypeName>> hierarchy = services.getServiceTypeToHierarchy();
        Map<TypeName, List<TypeName>> filteredHierarchy = new LinkedHashMap<>();
        for (Map.Entry<TypeName, List<TypeName>> e : hierarchy.entrySet()) {
            List<TypeName> filtered = e.getValue().stream()
                    .filter((typeName) -> services.getServiceTypeNames().contains(typeName))
                    .collect(Collectors.toList());
            assert (!filtered.isEmpty()) : e;
            filteredHierarchy.put(e.getKey(), filtered);
        }
        return filteredHierarchy;
    }

    private static Map<TypeName, Set<TypeName>> toFilteredContracts(ServicesToProcess services) {
        Map<TypeName, Set<TypeName>> contracts = services.getServicesToContracts();
        Map<TypeName, Set<TypeName>> filteredContracts = new LinkedHashMap<>();
        for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
            Set<TypeName> contractsForThisService = e.getValue();
            Set<TypeName> externalContractsForThisService = services.getServicesToExternalContracts().get(e.getKey());
            if (Objects.isNull(externalContractsForThisService) || externalContractsForThisService.isEmpty()) {
                filteredContracts.put(e.getKey(), e.getValue());
            } else {
                Set<TypeName> filteredContractsForThisService = new LinkedHashSet<>(contractsForThisService);
                filteredContractsForThisService.removeAll(externalContractsForThisService);
                filteredContracts.put(e.getKey(), filteredContractsForThisService);
            }
        }
        return filteredContracts;
    }


    /**
     * Builder.
     *
     * @param <B> the builder type
     * @param <C> the concrete type being build
     */
    @SuppressWarnings("unchecked")
    public abstract static class DefaultActivatorCreatorCodeGenBuilder<C extends DefaultActivatorCreatorCodeGen,
            B extends DefaultActivatorCreatorCodeGenBuilder<C, B>> {
        private Map<TypeName, TypeName> serviceTypeToParentServiceTypes;
        private Map<TypeName, String> serviceTypeToActivatorGenericDecl;
        private Map<TypeName, InjectionPointInfo.Access> serviceTypeAccessLevels;
        private Map<TypeName, Boolean> serviceTypeIsAbstractTypes;
        private Map<TypeName, Set<TypeName>> serviceTypeContracts;
        private Map<TypeName, Set<TypeName>> serviceTypeExternalContracts;
        private Map<TypeName, Dependencies> serviceTypeInjectionPointDependencies;
        private Map<TypeName, String> serviceTypePreDestroyMethodNames;
        private Map<TypeName, String> serviceTypePostConstructMethodNames;
        private Map<TypeName, Double> serviceTypeWeightedPriorities;
        private Map<TypeName, Integer> serviceTypeRunLevels;
        private Map<TypeName, Set<String>> serviceTypeScopeNames;
        private Map<TypeName, Set<QualifierAndValue>> serviceTypeQualifiers;
        private Map<TypeName, Set<TypeName>> serviceTypeToProviderForTypes;
        private Map<TypeName, List<TypeName>> serviceTypeHierarchy;
        private Map<TypeName, InterceptionPlan> serviceTypeInterceptionPlan;
        private Map<TypeName, List<String>> extraCodeGen;
        private Set<String> modulesRequired;
        private String classPrefixName;

        protected DefaultActivatorCreatorCodeGenBuilder() {
        }

        /**
         * Builds the {@link io.helidon.pico.spi.impl.DefaultElementInfo}.
         *
         * @return the fluent builder instance
         */
        public C build() {
            return (C) new DefaultActivatorCreatorCodeGen(this);
        }

        public B serviceTypeToParentServiceTypes(Map<? extends TypeName, ? extends TypeName> val) {
            this.serviceTypeToParentServiceTypes = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeToActivatorGenericDecl(Map<? extends TypeName, String> val) {
            this.serviceTypeToActivatorGenericDecl = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeAccessLevels(Map<TypeName, InjectionPointInfo.Access> val) {
            this.serviceTypeAccessLevels = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeIsAbstractTypes(Map<TypeName, Boolean> val) {
            this.serviceTypeIsAbstractTypes = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeContracts(Map<TypeName, Set<TypeName>> val) {
            this.serviceTypeContracts = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeExternalContracts(Map<TypeName, Set<TypeName>> val) {
            this.serviceTypeExternalContracts = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeInjectionPointDependencies(Map<TypeName, Dependencies> val) {
            this.serviceTypeInjectionPointDependencies = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypePreDestroyMethodNames(Map<TypeName, String> val) {
            this.serviceTypePreDestroyMethodNames = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypePostConstructMethodNames(Map<TypeName, String> val) {
            this.serviceTypePostConstructMethodNames = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeWeightedPriorities(Map<TypeName, Double> val) {
            this.serviceTypeWeightedPriorities = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeRunLevels(Map<TypeName, Integer> val) {
            this.serviceTypeRunLevels = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeScopeNames(Map<TypeName, Set<String>> val) {
            this.serviceTypeScopeNames = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeQualifiers(Map<TypeName, Set<QualifierAndValue>> val) {
            this.serviceTypeQualifiers = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeToProviderForTypes(Map<TypeName, Set<TypeName>> val) {
            this.serviceTypeToProviderForTypes = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeHierarchy(Map<TypeName, List<TypeName>> val) {
            this.serviceTypeHierarchy = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeInterceptionPlan(Map<TypeName, InterceptionPlan> val) {
            this.serviceTypeInterceptionPlan = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B extraCodeGen(Map<TypeName, List<String>> val) {
            this.extraCodeGen = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B modulesRequired(Set<String> val) {
            this.modulesRequired = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return (B) this;
        }

        public B classPrefixName(String val) {
            this.classPrefixName = val;
            return (B) this;
        }
    }

}
