/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.services.Dependencies;
import io.helidon.pico.spi.Resetable;

/**
 * Tracks the services to process, and ingests them to build the codegen model.
 * <p>
 * The basic flow:
 * 'annotation processor(s)' -> ServicesToProcess -> {@link AbstractCreator} derivative.
 * <p>
 * Note that the flow might be repeated multiple times since annotation processors by definition are recurrent in
 * nature.
 */
public class ServicesToProcess implements Resetable {
    private static final ServicesToProcess SERVICES = new ServicesToProcess();

    private static final AtomicInteger RUNNING_PROCESSORS = new AtomicInteger();

    private final Set<TypeName> servicesTypeNames = new LinkedHashSet<>();
    private final Set<String> requiredModules = new TreeSet<>();
    private final Map<TypeName, Set<TypeName>> servicesToContracts = new LinkedHashMap<>();
    private final Map<TypeName, Set<TypeName>> servicesToExternalContracts = new LinkedHashMap<>();
    private final Map<TypeName, Boolean> servicesToLockParentServiceTypeName = new LinkedHashMap<>();
    private final Map<TypeName, TypeName> servicesToParentServiceTypeNames = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToActivatorGenericDecl = new LinkedHashMap<>();
    private final Map<TypeName, InjectionPointInfo.Access> servicesToAccess = new LinkedHashMap<>();
    private final Map<TypeName, Boolean> servicesToIsAbstract = new LinkedHashMap<>();
    private final Map<TypeName, DependenciesInfo> servicesToDependencies = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToPreDestroyMethod = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToPostConstructMethod = new LinkedHashMap<>();
    private final Map<TypeName, Double> servicesToWeightedPriority = new LinkedHashMap<>();
    private final Map<TypeName, Integer> servicesToRunLevel = new LinkedHashMap<>();
    private final Map<TypeName, Set<String>> servicesToScopeTypeNames = new LinkedHashMap<>();
    private final Map<TypeName, Set<QualifierAndValue>> servicesToQualifiers = new LinkedHashMap<>();
    private final Map<TypeName, Set<TypeName>> servicesToProviderFor = new LinkedHashMap<>();
    private final Map<TypeName, InterceptionPlan> interceptorPlanFor = new LinkedHashMap<>();
    private final Map<TypeName, List<String>> extraCodeGen = new LinkedHashMap<>();
    private final Map<TypeName, List<TypeName>> serviceTypeHierarchy = new LinkedHashMap<>();

    private Path lastKnownSourcePathBeingProcessed;
    private String lastKnownTypeSuffix;
    private String moduleName;
    private String lastKnownModuleName;
    private Path lastKnownModuleInfoFilePath;
    private ModuleInfoDescriptor lastKnownModuleInfoDescriptor;
    private Path lastGeneratedModuleInfoFilePath;
    private ModuleInfoDescriptor lastGeneratedModuleInfoDescriptor;
    private String lastGeneratedPackageName;

    /**
     * The current services to process instance.
     *
     * @return the current services to process instance
     */
    public static ServicesToProcess servicesInstance() {
        return SERVICES;
    }

    private ServicesToProcess() {
    }

    @Override
    public boolean reset(
            boolean ignoredDeep) {
        servicesTypeNames.clear();
        requiredModules.clear();
        servicesToContracts.clear();
        servicesToExternalContracts.clear();
        // we intentionally except parent service type names from being cleared - we need to remember this fact!
//        if (false) {
//            servicesToLockParentServiceTypeName.clear();
//            servicesToParentServiceTypeNames.clear();
//            servicesToActivatorGenericDecl.clear();
//        }
        servicesToAccess.clear();
        servicesToIsAbstract.clear();
        servicesToDependencies.clear();
        servicesToPreDestroyMethod.clear();
        servicesToPostConstructMethod.clear();
        servicesToWeightedPriority.clear();
        servicesToRunLevel.clear();
        servicesToScopeTypeNames.clear();
        servicesToQualifiers.clear();
        servicesToProviderFor.clear();
        interceptorPlanFor.clear();
        serviceTypeHierarchy.clear();
        extraCodeGen.clear();
        return true;
    }

    /**
     * Introduce a new service type name to be added.
     *
     * @param serviceTypeName the service type name
     */
    void addServiceTypeName(
            TypeName serviceTypeName) {
        servicesTypeNames.add(Objects.requireNonNull(serviceTypeName));
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param parentServiceTypeName the parent for this service type name
     * @return flag indicating whether the parent was accepted
     */
    public boolean addParentServiceType(
            TypeName serviceTypeName,
            TypeName parentServiceTypeName) {
        return addParentServiceType(serviceTypeName, parentServiceTypeName, Optional.empty());
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param parentServiceTypeName the parent for this service type name
     * @param lockParent flag indicating whether the parent should be locked
     * @return flag indicating whether the parent was accepted
     */
    public boolean addParentServiceType(
            TypeName serviceTypeName,
            TypeName parentServiceTypeName,
            Optional<Boolean> lockParent) {
        if (parentServiceTypeName == null) {
            return false;
        }

        Boolean locked = servicesToLockParentServiceTypeName.get(serviceTypeName);
        if (locked != null) {
            if (locked) {
                TypeName lockedParentType = servicesToParentServiceTypeNames.get(serviceTypeName);
                if (!parentServiceTypeName.equals(lockedParentType)) {
                    return false;
                }
                return true;
            } else {
                servicesToLockParentServiceTypeName.put(serviceTypeName, lockParent.orElse(null));
            }
        }

        addServiceTypeName(serviceTypeName);
        servicesToParentServiceTypeNames.put(serviceTypeName, parentServiceTypeName);
        if (lockParent.isPresent()) {
            servicesToLockParentServiceTypeName.put(serviceTypeName, lockParent.get());
        }

        return true;
    }

    /**
     * @return the map of service type names to each respective super class / parent types.
     */
    Map<TypeName, TypeName> parentServiceTypes() {
        return Map.copyOf(servicesToParentServiceTypeNames);
    }

    /**
     * Introduce the activator generic portion of the declaration (e.g., the "CB extends MySingletonConfig" portion of
     * {@code MyService$$picoActivator<CB extends MySingletonConfig>)}.
     *
     * @param serviceTypeName the service type name
     * @param activatorGenericDecl the generics portion of the class decl
     */
    public void addActivatorGenericDecl(
            TypeName serviceTypeName,
            String activatorGenericDecl) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToActivatorGenericDecl.put(serviceTypeName, activatorGenericDecl);
        assert (prev == null || Objects.equals(prev, activatorGenericDecl));
    }

    /**
     * @return the map of service type names to activator generic declarations.
     */
    Map<TypeName, String> activatorGenericDecls() {
        return Map.copyOf(servicesToActivatorGenericDecl);
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param access the access level for the service type name
     */
    public void addAccessLevel(
            TypeName serviceTypeName,
            InjectionPointInfo.Access access) {
        addServiceTypeName(serviceTypeName);
        if (access != null) {
            Object prev = servicesToAccess.put(serviceTypeName, access);
            if (prev != null && !access.equals(prev)) {
                throw new ToolsException("can only support one access level for " + serviceTypeName);
            }
        }
    }

    /**
     * @return the map of service type names to each respective access level.
     */
    Map<TypeName, InjectionPointInfo.Access> accessLevels() {
        return servicesToAccess;
    }

    /**
     * Introduce the flag whether the given service type name is abstract (i.e., interface or abstract) and not concrete.
     *
     * @param serviceTypeName the service type name
     * @param isAbstract whether the service type name is abstract (i.e., interface or abstract)
     */
    public void addIsAbstract(
            TypeName serviceTypeName,
            boolean isAbstract) {
        addServiceTypeName(serviceTypeName);
        servicesToIsAbstract.put(serviceTypeName, isAbstract);
    }

    /**
     * @return the map of service type names to whether they are abstract. If not found then assume concrete.
     */
    Map<TypeName, Boolean> isAbstractMap() {
        return servicesToIsAbstract;
    }

    /**
     * @return the map of service type names to the super class hierarchy.
     */
    Map<TypeName, List<TypeName>> serviceTypeToHierarchy() {
        return serviceTypeHierarchy;
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param serviceTypeHierarchy the list of superclasses (where this service type is the last in the list)
     */
    public void addServiceTypeHierarchy(
            TypeName serviceTypeName,
            List<TypeName> serviceTypeHierarchy) {
        addServiceTypeName(serviceTypeName);
        if (serviceTypeHierarchy != null) {
            Object prev = this.serviceTypeHierarchy.put(serviceTypeName, serviceTypeHierarchy);
            if (prev != null && !serviceTypeHierarchy.equals(prev)) {
                throw new ToolsException("can only support one hierarchy for " + serviceTypeName);
            }
        }
    }

    /**
     * Checks whether the service type has an established super class hierarchy set.
     *
     * @param serviceTypeName the service type name
     * @return true if the hierarchy is known for this service type
     */
    public boolean hasHierarchyFor(
            TypeName serviceTypeName) {
        Collection<?> coll = serviceTypeHierarchy.get(serviceTypeName);
        return (coll != null);
    }

    /**
     * Checks whether the service type has an established set of contracts that are known for it.
     *
     * @param serviceTypeName the service type name
     * @return true if contracts are known about this service type
     */
    public boolean hasContractsFor(
            TypeName serviceTypeName) {
        Collection<?> coll = servicesToContracts.get(serviceTypeName);
        if (coll != null) {
            return true;
        }

        coll = servicesToExternalContracts.get(serviceTypeName);
        if (coll != null) {
            return true;
        }

        coll = servicesToProviderFor.get(serviceTypeName);
        return (coll != null);
    }

    /**
     * Checks whether the service type has been evaluated for an interceptor plan.
     *
     * @param serviceTypeName the service type name
     * @return true if this service type has already been considered for interceptors
     */
    public boolean hasVisitedInterceptorPlanFor(
            TypeName serviceTypeName) {
        return interceptorPlanFor.containsKey(serviceTypeName);
    }

    /**
     * Sets the {@link InterceptionPlan} for the given service type.
     *
     * @param serviceTypeName   the service type name
     * @param plan              the interceptor plan
     */
    public void addInterceptorPlanFor(
            TypeName serviceTypeName,
            Optional<InterceptionPlan> plan) {
        Object prev = interceptorPlanFor.put(serviceTypeName, plan.orElse(null));
        if (prev != null && plan.isPresent()) {
            throw new ToolsException("should only set interception plan once for: " + serviceTypeName);
        }
    }

    /**
     * The interception plan for each service type that has a non-null interception plan.
     *
     * @return the interception plan for each service type
     */
    public Map<TypeName, InterceptionPlan> interceptorPlans() {
        return interceptorPlanFor.entrySet().stream()
                .filter(e -> Objects.nonNull(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, TreeMap::new));
    }

    /**
     * Clears out just the interceptor plans.
     */
    public void clearInterceptorPlans() {
        interceptorPlanFor.clear();
    }

    /**
     * @return the extra code gen to add for each service type.
     */
    Map<TypeName, List<String>> extraCodeGen() {
        return extraCodeGen;
    }

    /**
     * Adds extra code gen per service type.
     *
     * @param serviceTypeName the service type name
     * @param codeGen the extra code gen to tack onto the activation implementation
     */
    public void addExtraCodeGen(
            TypeName serviceTypeName,
            String codeGen) {
        Objects.requireNonNull(codeGen);
        extraCodeGen.compute(serviceTypeName, (key, val) -> {
            if (val == null) {
                val = new ArrayList<>();
            }
            val.add(codeGen);
            return val;
        });
    }

    /**
     * Introduces a contract associated with a service type.
     *
     * @param serviceTypeName the service type name
     * @param contractTypeName the contract type name
     * @param isExternal whether the contract is external
     */
    public void addTypeForContract(
            TypeName serviceTypeName,
            TypeName contractTypeName,
            boolean isExternal) {
        if (serviceTypeName.equals(contractTypeName)) {
            return;
        }

        addServiceTypeName(serviceTypeName);

        servicesToContracts.compute(serviceTypeName, (key, val) -> {
            if (val == null) {
                val = new TreeSet<>();
            }
            val.add(contractTypeName);
            return val;
        });

        if (isExternal) {
            servicesToExternalContracts.compute(serviceTypeName, (key, val) -> {
                if (val == null) {
                    val = new TreeSet<>();
                }
                val.add(contractTypeName);
                return val;
            });
        }
    }

    /**
     * Introduces a new set of dependencies to the model.
     *
     * @param dependencies the dependencies
     */
    public void addDependencies(
            DependenciesInfo dependencies) {
        TypeName serviceTypeName =
                DefaultTypeName.createFromTypeName(dependencies.fromServiceTypeName().orElseThrow());
        addServiceTypeName(serviceTypeName);
        DependenciesInfo prevDependencies = servicesToDependencies.get(serviceTypeName);
        if (prevDependencies != null) {
            dependencies = Dependencies.combine(prevDependencies, dependencies);
        }
        servicesToDependencies.put(serviceTypeName, dependencies);
    }

    /**
     * Introduces a {@link jakarta.annotation.PreDestroy} method to the model for a given service type.
     *
     * @param serviceTypeName the service type name
     * @param preDestroyMethodName the method name
     */
    public void addPreDestroyMethod(
            TypeName serviceTypeName,
            String preDestroyMethodName) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToPreDestroyMethod.put(serviceTypeName, preDestroyMethodName);
        if (prev != null) {
            throw new ToolsException("can only support one PreDestroy method for " + serviceTypeName);
        }
    }

    /**
     * Introduces a {@link jakarta.annotation.PostConstruct} method to the model for a given service type.
     *
     * @param serviceTypeName the service type name
     * @param postConstructMethodName the method name
     */
    public void addPostConstructMethod(
            TypeName serviceTypeName,
            String postConstructMethodName) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToPostConstructMethod.put(serviceTypeName, postConstructMethodName);
        if (prev != null) {
            throw new ToolsException("can only support one PostConstruct method for " + serviceTypeName);
        }
    }

    /**
     * Sets the weight of a service type.
     *
     * @param serviceTypeName the service type name
     * @param weight the weight priority
     */
    public void addDeclaredWeight(
            TypeName serviceTypeName,
            Double weight) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToWeightedPriority.put(serviceTypeName, weight);
        if (prev != null) {
            throw new ToolsException("can only support one weighted priority for " + serviceTypeName);
        }
    }

    /**
     * Sets the run level for a service type name.
     *
     * @param serviceTypeName the service type name
     * @param runLevel its run level
     */
    public void addDeclaredRunLevel(
            TypeName serviceTypeName,
            Integer runLevel) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToRunLevel.put(serviceTypeName, runLevel);
        if (prev != null) {
            throw new ToolsException("can only support one RunLevel for " + serviceTypeName);
        }
    }

    /**
     * Adds a scope type name for a service type name.
     *
     * @param serviceTypeName the service type name
     * @param scopeTypeName its scope type name
     */
    public void addScopeTypeName(
            TypeName serviceTypeName,
            String scopeTypeName) {
        Objects.requireNonNull(scopeTypeName);
        addServiceTypeName(serviceTypeName);

        servicesToScopeTypeNames.compute(serviceTypeName, (k, v) -> {
            if (v == null) {
                v = new LinkedHashSet<>();
            }
            v.add(scopeTypeName);
            return v;
        });
    }

    /**
     * Establishes the fact that a given service type is a {@link jakarta.inject.Provider} type for the given provided types.
     *
     * @param serviceTypeName the service type name
     * @param providerFor the types that it provides
     */
    public void addProviderFor(
            TypeName serviceTypeName,
            Set<TypeName> providerFor) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToProviderFor.put(serviceTypeName, providerFor);
        if (prev != null && !prev.equals(providerFor)) {
            throw new ToolsException("can only support setting isProvider once for " + serviceTypeName);
        }
    }

    /**
     * Sets the qualifiers associated with a service type.
     *
     * @param serviceTypeName the service type name
     * @param qualifiers its qualifiers
     */
    public void addQualifiers(
            TypeName serviceTypeName,
            Set<QualifierAndValue> qualifiers) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToQualifiers.put(serviceTypeName, qualifiers);
        if (prev != null) {
            throw new ToolsException("can only support setting qualifiers once for " + serviceTypeName
                    + "; prev = " + prev + " and new = " + qualifiers);
        }
    }

    /**
     * Fetches the set of known service type names being processed in this batch.
     *
     * @return the set of known service type names being processed
     */
    public List<TypeName> serviceTypeNames() {
        ArrayList<TypeName> result = new ArrayList<>(servicesTypeNames);
        Collections.sort(result);
        return result;
    }

    /**
     * @return Fetches the map of service types to their set of contracts for that service type.
     */
    Map<TypeName, Set<TypeName>> contracts() {
        return new TreeMap<>(servicesToContracts);
    }

    /**
     * @return Fetches the map of service types to their set of contracts for that service type.
     */
    Map<TypeName, Set<TypeName>> externalContracts() {
        return new TreeMap<>(servicesToExternalContracts);
    }

    /**
     * @return Fetches the map of service types to their injection point dependencies.
     */
    Map<TypeName, DependenciesInfo> injectionPointDependencies() {
        return new TreeMap<>(servicesToDependencies);
    }

    /**
     * @return Fetches the map of service types to their post construct methods.
     */
    Map<TypeName, String> postConstructMethodNames() {
        return new TreeMap<>(servicesToPostConstructMethod);
    }

    /**
     * @return Fetches the map of service types to their pre destroy methods.
     */
    Map<TypeName, String> preDestroyMethodNames() {
        return new TreeMap<>(servicesToPreDestroyMethod);
    }

    /**
     * Fetches the map of service types to their priorities.
     *
     * @return the map of service types to their priorities
     */
    public Map<TypeName, Double> weightedPriorities() {
        return new TreeMap<>(servicesToWeightedPriority);
    }

    /**
     * Fetches the map of service types to their run levels.
     *
     * @return the map of service types to their run levels
     */
    public Map<TypeName, Integer> runLevels() {
        return new TreeMap<>(servicesToRunLevel);
    }

    /**
     * Fetches the map of service types to their scope type names.
     *
     * @return the map of service types to their scope type names
     */
    public Map<TypeName, Set<String>> scopeTypeNames() {
        return new TreeMap<>(servicesToScopeTypeNames);
    }

    /**
     * @return Fetches the map of service types to the set of services they provide.
     */
    Map<TypeName, Set<TypeName>> providerForTypeNames() {
        return new TreeMap<>(servicesToProviderFor);
    }

    /**
     * @return Fetches the map of service types to the set of qualifiers associated with each.
     */
    Map<TypeName, Set<QualifierAndValue>> qualifiers() {
        return new TreeMap<>(servicesToQualifiers);
    }

    /**
     * Introduces the need for external modules.
     *
     * @param serviceTypeName the service type name
     * @param moduleNames the required module names to support known external contracts
     */
    public void addExternalRequiredModules(
            TypeName serviceTypeName,
            Collection<String> moduleNames) {
        if (moduleNames != null) {
            requiredModules.addAll(moduleNames);
        }
    }

    /**
     * @return the set of required (external) module names.
     */
    Set<String> requiredModules() {
        return new TreeSet<>(requiredModules);
    }

    /**
     * Sets this module name.
     *
     * @param moduleName the module name
     */
    public void moduleName(
            String moduleName) {
        // special note: the compiler uses the same jvm instance for each round, including source and test, so we
        // cannot guard against changes here!!
        //        if (Objects.nonNull(this.moduleName) && !this.moduleName.equals(moduleName)) {
        //            throw new ToolsException("can only support setting module name once: " + this.moduleName + "
        //            and " + moduleName);
        //        }
        this.moduleName = moduleName;
        this.lastKnownModuleName = moduleName;
    }

    /**
     * Clears the module name.
     */
    public void clearModuleName() {
        this.moduleName = null;
    }

    /**
     * This module name.
     *
     * @return this module name
     */
    public String moduleName() {
        return moduleName;
    }

    /**
     * The last known descriptor being processed.
     *
     * @param descriptor the descriptor
     */
    public void lastKnownModuleInfoDescriptor(
            ModuleInfoDescriptor descriptor) {
        this.lastKnownModuleInfoDescriptor = descriptor;
        if (descriptor != null) {
            moduleName(descriptor.name());
        }
    }

    /**
     * @return Fetches the last known module info descriptor.
     */
    ModuleInfoDescriptor lastKnownModuleInfoDescriptor() {
        return lastKnownModuleInfoDescriptor;
    }

    /**
     * The last known file path location for the module-info descriptor being processed.
     *
     * @param lastKnownModuleInfoFile the file path location for the descriptor
     */
    public void lastKnownModuleInfoFilePath(
            Path lastKnownModuleInfoFile) {
        this.lastKnownModuleInfoFilePath = lastKnownModuleInfoFile;
    }

    /**
     * @return Fetches the last known module info file path.
     */
    Path lastKnownModuleInfoFilePath() {
        return lastKnownModuleInfoFilePath;
    }

    /**
     * Sets the last generated module info descriptor and its location.
     *
     * @param descriptor the descriptor
     * @param location the location for the descriptor
     */
    void lastGeneratedModuleInfoDescriptor(
            ModuleInfoDescriptor descriptor,
            Path location) {
        this.lastGeneratedModuleInfoDescriptor = descriptor;
        this.lastGeneratedModuleInfoFilePath = location;
    }

    /**
     * @return Fetches the last generated module descriptor.
     */
    ModuleInfoDescriptor getLastGeneratedModuleInfoDescriptor() {
        return lastGeneratedModuleInfoDescriptor;
    }

    /**
     * @return Fetches the last generated module descriptor location.
     */
    Path lastGeneratedModuleInfoFilePath() {
        return lastGeneratedModuleInfoFilePath;
    }

    /**
     * Sets the last known source path being processed.
     *
     * @param lastKnownSourcePathBeingProcessed the last source path being processed
     */
    public void lastKnownSourcePathBeingProcessed(
            Path lastKnownSourcePathBeingProcessed) {
        this.lastKnownSourcePathBeingProcessed = lastKnownSourcePathBeingProcessed;
    }

    /**
     * @return Fetches the last known source path being processed.
     */
    Path lastKnownSourcePathBeingProcessed() {
        return lastKnownSourcePathBeingProcessed;
    }

    /**
     * Sets the last known type suffix (e.g., "test").
     *
     * @param typeSuffix the optional type suffix
     */
    public void lastKnownTypeSuffix(
            String typeSuffix) {
        this.lastKnownTypeSuffix = typeSuffix;
    }

    /**
     * @return Fetches the last known type suffix.
     */
    String lastKnownTypeSuffix() {
        return lastKnownTypeSuffix;
    }

    /**
     * Sets the last generated package name.
     *
     * @param lastGeneratedPackageName the package name
     */
    public void lastGeneratedPackageName(
            String lastGeneratedPackageName) {
        this.lastGeneratedPackageName = lastGeneratedPackageName;
    }

    /**
     * @return Fetches the last generated package name.
     */
    String lastGeneratedPackageName() {
        return lastGeneratedPackageName;
    }

    /**
     * @return Attempts to determine the generated module name based upon the batch of services being processed.
     */
    String determineGeneratedModuleName() {
        String moduleName = moduleName();
        moduleName = ModuleUtils.toSuggestedModuleName(moduleName,
                                                       lastKnownTypeSuffix(),
                                                       ModuleInfoDescriptor.DEFAULT_MODULE_NAME);
        return moduleName;
    }

    /**
     * @return Attempts to determine the generated package name based upon the batch of services being processed.
     */
    String determineGeneratedPackageName() {
        String export = lastGeneratedPackageName();
        if (export != null) {
            return export;
        }

        ModuleInfoDescriptor descriptor = lastKnownModuleInfoDescriptor();
        String packageName = Objects.requireNonNull(
                ModuleUtils.toSuggestedGeneratedPackageName(descriptor, serviceTypeNames(), PicoServicesConfig.NAME));
        return packageName;
    }

    /**
     * @return Fetches the last known module name.
     */
    String lastKnownModuleName() {
        return lastKnownModuleName;
    }

    /**
     * Called to signal the beginning of an annotation processing phase.
     *
     * @param processor the processor running
     * @param annotations the annotations being processed
     * @param roundEnv the round env
     */
    public static void onBeginProcessing(
            Msgr processor,
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        boolean reallyStarted = !annotations.isEmpty();
        if (reallyStarted && !roundEnv.processingOver()) {
            RUNNING_PROCESSORS.incrementAndGet();
        }
        processor.debug(processor.getClass()
                                .getSimpleName() + " processing " + annotations + "; really-started=" + reallyStarted);
    }

    /**
     * Called to signal the end of an annotation processing phase.
     *
     * @param processor the processor running
     * @param annotations the annotations being processed
     * @param roundEnv the round env
     */
    public static void onEndProcessing(
            Msgr processor,
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        boolean done = annotations.isEmpty();
        if (done && roundEnv.processingOver()) {
            RUNNING_PROCESSORS.decrementAndGet();
        }
        processor.debug(processor.getClass().getSimpleName() + " finished processing; done-done=" + done);
    }

    /**
     * @return Returns true if any processor is running.
     */
    static boolean isRunning() {
        return RUNNING_PROCESSORS.get() > 0;
    }

}
