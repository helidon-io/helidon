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

package io.helidon.inject.tools;

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

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.Resettable;
import io.helidon.inject.runtime.Dependencies;

import static io.helidon.inject.tools.ModuleUtils.APPLICATION_MODULE_INFO;
import static io.helidon.inject.tools.ModuleUtils.MODULE_COMPONENT_MODULE_INFO;

/**
 * Tracks the services to process, and ingests them to build the codegen model.
 * <p>
 * The basic flow:
 * 'annotation processor(s)' -> ServicesToProcess -> {@link AbstractCreator} derivative.
 * <p>
 * Note that the flow might be repeated multiple times since annotation processors by definition are recurrent in
 * nature.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class ServicesToProcess implements Resettable {
    private static final ServicesToProcess SERVICES = new ServicesToProcess();

    private static final AtomicInteger RUNNING_PROCESSORS = new AtomicInteger();
    private static final List<Runnable> RUNNABLES_TO_CALL_WHEN_DONE = new ArrayList<>();

    private final Set<TypeName> servicesTypeNames = new LinkedHashSet<>();
    private final Set<TypeName> servicesTypeNameToCodeGen = new LinkedHashSet<>();
    private final Set<String> requiredModules = new TreeSet<>();
    private final Map<TypeName, Set<TypeName>> servicesToContracts = new LinkedHashMap<>();
    private final Map<TypeName, Set<TypeName>> servicesToExternalContracts = new LinkedHashMap<>();
    private final Map<TypeName, Boolean> servicesToLockParentServiceTypeName = new LinkedHashMap<>();
    private final Map<TypeName, TypeName> servicesToParentServiceTypeNames = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToActivatorGenericDecl = new LinkedHashMap<>();
    private final Map<TypeName, AccessModifier> servicesToAccess = new LinkedHashMap<>();
    private final Map<TypeName, Boolean> servicesToIsAbstract = new LinkedHashMap<>();
    private final Map<TypeName, DependenciesInfo> servicesToDependencies = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToPreDestroyMethod = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToPostConstructMethod = new LinkedHashMap<>();
    private final Map<TypeName, Double> servicesToWeightedPriority = new LinkedHashMap<>();
    private final Map<TypeName, Integer> servicesToRunLevel = new LinkedHashMap<>();
    private final Map<TypeName, Set<TypeName>> servicesToScopeTypeNames = new LinkedHashMap<>();
    private final Map<TypeName, Set<Qualifier>> servicesToQualifiers = new LinkedHashMap<>();
    private final Map<TypeName, Set<TypeName>> servicesToProviderFor = new LinkedHashMap<>();
    private final Map<TypeName, InterceptionPlan> interceptorPlanFor = new LinkedHashMap<>();
    private final Map<TypeName, List<String>> extraCodeGen = new LinkedHashMap<>();
    private final Map<TypeName, List<String>> extraActivatorClassComments = new LinkedHashMap<>();
    private final Map<TypeName, List<TypeName>> serviceTypeHierarchy = new LinkedHashMap<>();
    private final Map<TypeName, String> servicesToDefaultConstructor = new LinkedHashMap<>();

    private Path lastKnownSourcePathBeingProcessed;
    private String lastKnownTypeSuffix;
    private String moduleName;
    private String lastKnownModuleName;
    private Path lastKnownModuleInfoFilePath;
    private ModuleInfoDescriptor lastKnownModuleInfoDescriptor;
    private Path lastGeneratedModuleInfoFilePath;
    private ModuleInfoDescriptor lastGeneratedModuleInfoDescriptor;
    private String lastGeneratedPackageName;

    private ServicesToProcess() {
    }

    /**
     * The current services to process instance.
     *
     * @return the current services to process instance
     */
    public static ServicesToProcess servicesInstance() {
        return SERVICES;
    }

    /**
     * Creates a new instance, apart from the current global singleton instance exposed from {@link #servicesInstance()}.
     *
     * @return the new instance
     * @see #servicesInstance()
     */
    public static ServicesToProcess create() {
        return new ServicesToProcess();
    }

    /**
     * Called to signal the beginning of an annotation processing phase.
     *
     * @param processor the processor running
     * @param annotations the annotations being processed
     * @param roundEnv the round env
     */
    public static void onBeginProcessing(Messager processor,
                                         Set<?> annotations,
                                         RoundEnvironment roundEnv) {
        boolean reallyStarted = !annotations.isEmpty();
        if (reallyStarted && !roundEnv.processingOver()) {
            RUNNING_PROCESSORS.incrementAndGet();
        }
        processor.debug(processor.getClass()
                                .getSimpleName() + " processing " + annotations + "; really-started=" + reallyStarted);
    }

    /**
     * Called to add a runnable to call when done with all annotation processing.
     *
     * @param runnable the runnable to call
     */
    public static void addOnDoneRunnable(Runnable runnable) {
        RUNNABLES_TO_CALL_WHEN_DONE.add(runnable);
    }

    /**
     * Called to signal the end of an annotation processing phase.
     *
     * @param processor the processor running
     * @param annotations the annotations being processed
     * @param roundEnv the round env
     */
    public static void onEndProcessing(Messager processor,
                                       Set<?> annotations,
                                       RoundEnvironment roundEnv) {
        boolean done = annotations.isEmpty();
        if (done && roundEnv.processingOver()) {
            RUNNING_PROCESSORS.decrementAndGet();
        }
        processor.debug(processor.getClass().getSimpleName() + " finished processing; done-done=" + done);

        if (done && RUNNING_PROCESSORS.get() == 0) {
            // perform module analysis to ensure the proper definitions are specified for modules and applications
            ServicesToProcess.servicesInstance().performModuleUsageValidation(processor);
        }

        if (done) {
            RUNNABLES_TO_CALL_WHEN_DONE.forEach(Runnable::run);
            RUNNABLES_TO_CALL_WHEN_DONE.clear();
        }
    }

    @Override
    public boolean reset(boolean ignoredDeep) {
        servicesTypeNames.clear();
        servicesTypeNameToCodeGen.clear();
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
        extraActivatorClassComments.clear();
        return true;
    }

    /**
     * Introduce a new service type name to be added.
     *
     * @param serviceTypeName the service type name
     */
    void addServiceTypeName(TypeName serviceTypeName) {
        servicesTypeNames.add(Objects.requireNonNull(serviceTypeName));
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param parentServiceTypeName the parent for this service type name
     * @return flag indicating whether the parent was accepted
     */
    public boolean addParentServiceType(TypeName serviceTypeName,
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
    public boolean addParentServiceType(TypeName serviceTypeName,
                                        TypeName parentServiceTypeName,
                                        Optional<Boolean> lockParent) {
        if (parentServiceTypeName == null) {
            return false;
        }

        Boolean locked = servicesToLockParentServiceTypeName.get(serviceTypeName);
        if (locked != null) {
            if (locked) {
                TypeName lockedParentType = servicesToParentServiceTypeNames.get(serviceTypeName);
                return parentServiceTypeName.equals(lockedParentType);
            } else {
                servicesToLockParentServiceTypeName.put(serviceTypeName, lockParent.orElse(null));
            }
        }

        addServiceTypeName(serviceTypeName);
        servicesToParentServiceTypeNames.put(serviceTypeName, parentServiceTypeName);
        lockParent.ifPresent(aBoolean -> servicesToLockParentServiceTypeName.put(serviceTypeName, aBoolean));

        return true;
    }

    /**
     * @return the map of service type names to each respective super class / parent types
     */
    Map<TypeName, TypeName> parentServiceTypes() {
        return Map.copyOf(servicesToParentServiceTypeNames);
    }

    /**
     * Introduce the activator generic portion of the declaration (e.g., the "CB extends MySingletonConfig" portion of
     * {@code MyService$$injectionActivator<CB extends MySingletonConfig>}).
     *
     * @param serviceTypeName the service type name
     * @param activatorGenericDecl the generics portion of the class decl
     */
    public void addActivatorGenericDecl(TypeName serviceTypeName,
                                        String activatorGenericDecl) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToActivatorGenericDecl.put(serviceTypeName, activatorGenericDecl);
        assert (prev == null || Objects.equals(prev, activatorGenericDecl));
    }

    /**
     * @return the map of service type names to activator generic declarations
     */
    Map<TypeName, String> activatorGenericDecls() {
        return Map.copyOf(servicesToActivatorGenericDecl);
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param access the access level for the service type name
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addAccessLevel(TypeName serviceTypeName,
                               AccessModifier access) {
        Objects.requireNonNull(serviceTypeName);
        Objects.requireNonNull(access);
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToAccess.put(serviceTypeName, access);
        if (prev != null && !access.equals(prev)) {
            throw new IllegalStateException("Can only support one access level for " + serviceTypeName);
        }
    }

    /**
     * @return the map of service type names to each respective access level
     */
    Map<TypeName, AccessModifier> accessLevels() {
        return servicesToAccess;
    }

    /**
     * Introduce the flag whether the given service type name is abstract (i.e., interface or abstract) and not concrete.
     *
     * @param serviceTypeName the service type name
     * @param isAbstract whether the service type name is abstract (i.e., interface or abstract)
     */
    public void addIsAbstract(TypeName serviceTypeName,
                              boolean isAbstract) {
        addServiceTypeName(serviceTypeName);
        servicesToIsAbstract.put(serviceTypeName, isAbstract);
    }

    /**
     * @return the map of service type names to whether they are abstract. If not found then assume concrete
     */
    Map<TypeName, Boolean> isAbstractMap() {
        return servicesToIsAbstract;
    }

    /**
     * @return the map of service type names to the super class hierarchy
     */
    Map<TypeName, List<TypeName>> serviceTypeToHierarchy() {
        return serviceTypeHierarchy;
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param serviceTypeHierarchy the list of superclasses (where this service type is the last in the list)
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addServiceTypeHierarchy(TypeName serviceTypeName,
                                        List<TypeName> serviceTypeHierarchy) {
        addServiceTypeName(serviceTypeName);
        if (serviceTypeHierarchy != null) {
            Object prev = this.serviceTypeHierarchy.put(serviceTypeName, serviceTypeHierarchy);
            if (prev != null && !serviceTypeHierarchy.equals(prev)) {
                throw new IllegalStateException("Can only support one hierarchy for " + serviceTypeName);
            }
        }
    }

    /**
     * Checks whether the service type has an established super class hierarchy set.
     *
     * @param serviceTypeName the service type name
     * @return true if the hierarchy is known for this service type
     */
    public boolean hasHierarchyFor(TypeName serviceTypeName) {
        Collection<?> coll = serviceTypeHierarchy.get(serviceTypeName);
        return (coll != null);
    }

    /**
     * Checks whether the service type has an established set of contracts that are known for it.
     *
     * @param serviceTypeName the service type name
     * @return true if contracts are known about this service type
     */
    public boolean hasContractsFor(TypeName serviceTypeName) {
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
    public boolean hasVisitedInterceptorPlanFor(TypeName serviceTypeName) {
        return interceptorPlanFor.containsKey(serviceTypeName);
    }

    /**
     * Sets the {@link InterceptionPlan} for the given service type.
     *
     * @param serviceTypeName   the service type name
     * @param plan              the interceptor plan
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addInterceptorPlanFor(TypeName serviceTypeName,
                                      Optional<InterceptionPlan> plan) {
        Object prev = interceptorPlanFor.put(serviceTypeName, plan.orElse(null));
        if (prev != null && plan.isPresent()) {
            throw new IllegalStateException("Should only set interception plan once for: " + serviceTypeName);
        }
    }

    /**
     * The interception plan for each service type that has a non-null interception plan.
     *
     * @return the interception plan for each service type
     */
    public Map<TypeName, InterceptionPlan> interceptorPlans() {
        return interceptorPlanFor.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, TreeMap::new));
    }

    /**
     * Clears out just the interceptor plans.
     */
    public void clearInterceptorPlans() {
        interceptorPlanFor.clear();
    }

    /**
     * @return the extra code gen to add for each service type
     */
    Map<TypeName, List<String>> extraCodeGen() {
        return extraCodeGen;
    }

    /**
     * Adds extra code gen per service type.
     *
     * @param serviceTypeName the service type name
     * @param codeGen the extra code gen to tack onto the activator implementation
     */
    public void addExtraCodeGen(TypeName serviceTypeName,
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
     * @return the extra activator class level comments for code generated types
     */
    Map<TypeName, List<String>> extraActivatorClassComments() {
        return extraActivatorClassComments;
    }

    /**
     * Adds extra cactivator class level comments.
     *
     * @param serviceTypeName the service type name
     * @param codeGen the extra comments tack onto the activator implementation
     */
    public void addExtraActivatorClassComments(TypeName serviceTypeName,
                                               String codeGen) {
        Objects.requireNonNull(codeGen);
        extraActivatorClassComments.compute(serviceTypeName, (key, val) -> {
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
    public void addTypeForContract(TypeName serviceTypeName,
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
    public void addDependencies(DependenciesInfo dependencies) {
        TypeName serviceTypeName = dependencies.fromServiceTypeName().orElseThrow();
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
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addPreDestroyMethod(TypeName serviceTypeName,
                                    String preDestroyMethodName) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToPreDestroyMethod.put(serviceTypeName, preDestroyMethodName);
        if (prev != null) {
            throw new IllegalStateException("Can only support one PreDestroy method for " + serviceTypeName);
        }
    }

    /**
     * Introduces a {@link jakarta.annotation.PostConstruct} method to the model for a given service type.
     *
     * @param serviceTypeName the service type name
     * @param postConstructMethodName the method name
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addPostConstructMethod(TypeName serviceTypeName,
                                       String postConstructMethodName) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToPostConstructMethod.put(serviceTypeName, postConstructMethodName);
        if (prev != null) {
            throw new IllegalStateException("Can only support one PostConstruct method for " + serviceTypeName);
        }
    }

    /**
     * Sets the weight of a service type.
     *
     * @param serviceTypeName the service type name
     * @param weight the weight priority
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addDeclaredWeight(TypeName serviceTypeName,
                                  Double weight) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToWeightedPriority.put(serviceTypeName, weight);
        if (prev != null) {
            throw new IllegalStateException("Can only support one weighted priority for " + serviceTypeName);
        }
    }

    /**
     * Sets the run level for a service type name.
     *
     * @param serviceTypeName the service type name
     * @param runLevel its run level
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addDeclaredRunLevel(TypeName serviceTypeName,
                                    Integer runLevel) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToRunLevel.put(serviceTypeName, runLevel);
        if (prev != null) {
            throw new IllegalStateException("Can only support one RunLevel for " + serviceTypeName);
        }
    }

    /**
     * Adds a scope type name for a service type name.
     *
     * @param serviceTypeName the service type name
     * @param scopeTypeName its scope type name
     */
    public void addScopeTypeName(TypeName serviceTypeName,
                                 TypeName scopeTypeName) {
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
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addProviderFor(TypeName serviceTypeName,
                               Set<TypeName> providerFor) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToProviderFor.put(serviceTypeName, providerFor);
        if (prev != null && !prev.equals(providerFor)) {
            throw new IllegalStateException("Can only support setting isProvider once for " + serviceTypeName);
        }
    }

    /**
     * Sets the qualifiers associated with a service type.
     *
     * @param serviceTypeName the service type name
     * @param qualifiers its qualifiers
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public void addQualifiers(TypeName serviceTypeName,
                              Set<Qualifier> qualifiers) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToQualifiers.put(serviceTypeName, qualifiers);
        if (prev != null) {
            throw new IllegalStateException("Can only support setting qualifiers once for " + serviceTypeName
                                                    + "; prev = " + prev + " and new = " + qualifiers);
        }
    }

    /**
     * Fetches the set of known service type names being processed in this batch.
     *
     * @return the list of known service type names being processed
     */
    public List<TypeName> serviceTypeNames() {
        ArrayList<TypeName> result = new ArrayList<>(servicesTypeNames);
        Collections.sort(result);
        return result;
    }

    /**
     * Fetches the set of known service type names being code generated in this batch.
     *
     * @return the list of known service type names being code generated
     */
    public List<TypeName> generatedServiceTypeNames() {
        ArrayList<TypeName> result = new ArrayList<>(servicesTypeNameToCodeGen);
        Collections.sort(result);
        return result;
    }

    /**
     * Sets the service type names being code generated in this batch.
     *
     * @param coll the collection of services to be code generated
     */
    public void generatedServiceTypeNames(Collection<TypeName> coll) {
        this.servicesTypeNameToCodeGen.clear();
        this.servicesTypeNameToCodeGen.addAll(coll);
    }

    /**
     * @return fetches the map of service types to their set of contracts for that service type
     */
    Map<TypeName, Set<TypeName>> contracts() {
        return new TreeMap<>(servicesToContracts);
    }

    /**
     * @return fetches the map of service types to their set of contracts for that service type
     */
    Map<TypeName, Set<TypeName>> externalContracts() {
        return new TreeMap<>(servicesToExternalContracts);
    }

    /**
     * @return fetches the map of service types to their injection point dependencies
     */
    Map<TypeName, DependenciesInfo> injectionPointDependencies() {
        return new TreeMap<>(servicesToDependencies);
    }

    /**
     * @return fetches the map of service types to their post construct methods
     */
    Map<TypeName, String> postConstructMethodNames() {
        return new TreeMap<>(servicesToPostConstructMethod);
    }

    /**
     * @return fetches the map of service types to their pre destroy methods
     */
    Map<TypeName, String> preDestroyMethodNames() {
        return new TreeMap<>(servicesToPreDestroyMethod);
    }

    Map<TypeName, String> defaultConstructors() {
        return new TreeMap<>(servicesToDefaultConstructor);
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
    public Map<TypeName, Set<TypeName>> scopeTypeNames() {
        return new TreeMap<>(servicesToScopeTypeNames);
    }

    /**
     * @return fetches the map of service types to the set of services they provide
     */
    Map<TypeName, Set<TypeName>> providerForTypeNames() {
        return new TreeMap<>(servicesToProviderFor);
    }

    /**
     * @return fetches the map of service types to the set of qualifiers associated with each
     */
    Map<TypeName, Set<Qualifier>> qualifiers() {
        return new TreeMap<>(servicesToQualifiers);
    }

    /**
     * Introduces the need for external modules.
     *
     * @param serviceTypeName the service type name
     * @param moduleNames the required module names to support known external contracts
     */
    public void addExternalRequiredModules(TypeName serviceTypeName,
                                           Collection<String> moduleNames) {
        if (moduleNames != null) {
            requiredModules.addAll(moduleNames);
        }
    }

    /**
     * @return the set of required (external) module names
     */
    Set<String> requiredModules() {
        return new TreeSet<>(requiredModules);
    }

    /**
     * Sets this module name.
     *
     * @param moduleName the module name
     */
    public void moduleName(String moduleName) {
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
    public void lastKnownModuleInfoDescriptor(ModuleInfoDescriptor descriptor) {
        this.lastKnownModuleInfoDescriptor = descriptor;
        if (descriptor != null) {
            moduleName(descriptor.name());
        }
    }

    /**
     * @return fetches the last known module info descriptor
     */
    ModuleInfoDescriptor lastKnownModuleInfoDescriptor() {
        return lastKnownModuleInfoDescriptor;
    }

    /**
     * The last known file path location for the module-info descriptor being processed.
     *
     * @param lastKnownModuleInfoFile the file path location for the descriptor
     */
    public void lastKnownModuleInfoFilePath(Path lastKnownModuleInfoFile) {
        this.lastKnownModuleInfoFilePath = lastKnownModuleInfoFile;
    }

    /**
     * @return fetches the last known module info file path
     */
    Path lastKnownModuleInfoFilePath() {
        return lastKnownModuleInfoFilePath;
    }

    /**
     * @return fetches the last generated module descriptor location
     */
    Path lastGeneratedModuleInfoFilePath() {
        return lastGeneratedModuleInfoFilePath;
    }

    /**
     * Sets the last known source path being processed.
     *
     * @param lastKnownSourcePathBeingProcessed the last source path being processed
     */
    public void lastKnownSourcePathBeingProcessed(Path lastKnownSourcePathBeingProcessed) {
        this.lastKnownSourcePathBeingProcessed = lastKnownSourcePathBeingProcessed;
    }

    /**
     * Sets the last known type suffix (e.g., "test").
     *
     * @param typeSuffix the optional type suffix
     */
    public void lastKnownTypeSuffix(String typeSuffix) {
        this.lastKnownTypeSuffix = typeSuffix;
    }

    /**
     * @return fetches the last known type suffix
     */
    String lastKnownTypeSuffix() {
        return lastKnownTypeSuffix;
    }

    /**
     * Sets the last generated package name.
     *
     * @param lastGeneratedPackageName the package name
     */
    public void lastGeneratedPackageName(String lastGeneratedPackageName) {
        this.lastGeneratedPackageName = lastGeneratedPackageName;
    }

    /**
     * Fetches the last generated package name.
     */
    String lastGeneratedPackageName() {
        return lastGeneratedPackageName;
    }

    /**
     * Attempts to determine the generated module name based upon the batch of services being processed.
     */
    String determineGeneratedModuleName() {
        String moduleName = moduleName();
        moduleName = ModuleUtils.toSuggestedModuleName(moduleName,
                                                       lastKnownTypeSuffix(),
                                                       ModuleInfoDescriptor.DEFAULT_MODULE_NAME);
        return moduleName;
    }

    /**
     * Attempts to determine the generated package name based upon the batch of services being processed.
     */
    String determineGeneratedPackageName() {
        String export = lastGeneratedPackageName();
        if (export != null) {
            return export;
        }

        ModuleInfoDescriptor descriptor = lastKnownModuleInfoDescriptor();
        String packageName = ModuleUtils.innerToSuggestedGeneratedPackageName(descriptor, serviceTypeNames(), "inject");
        return Objects.requireNonNull(packageName);
    }

    /**
     * Use a custom default constructor code.
     *
     * @param serviceType type of service
     * @param constructor constructor generated code
     */
    public void defaultConstructor(TypeName serviceType, String constructor) {
        this.servicesToDefaultConstructor.put(serviceType, constructor);
    }

    /**
     * If we made it here we know that Injection annotation processing was used. If there is a module-info in use and services where
     * defined during processing, then we should have a module created and optionally and application.  If so then we should
     * validate the integrity of the user's module-info.java for the {@link ModuleComponent} and
     * {@link Application} definitions - unless the user opted out of this check with the
     * {@link Options#TAG_IGNORE_MODULE_USAGE} option.
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    private void performModuleUsageValidation(Messager processor) {
        if (lastKnownModuleInfoFilePath != null && lastKnownModuleInfoDescriptor == null) {
            throw new IllegalStateException("Expected to have a module-info.java");
        }

        if (lastKnownModuleInfoDescriptor == null) {
            return;
        }

        boolean wasModuleDefined = !servicesTypeNames.isEmpty() || contracts().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(it -> it.name().equals(ModuleComponent.class.getName()));
        boolean wasApplicationDefined = contracts().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(it -> it.name().equals(Application.class.getName()));

        boolean shouldWarnOnly = Options.isOptionEnabled(Options.TAG_IGNORE_MODULE_USAGE);
        String message = ". Use -A" + Options.TAG_IGNORE_MODULE_USAGE + "=true to ignore.";

        if (wasModuleDefined) {
            Optional<ModuleInfoItem> moduleInfoItem = lastKnownModuleInfoDescriptor.first(MODULE_COMPONENT_MODULE_INFO);
            if (moduleInfoItem.isEmpty() || !moduleInfoItem.get().provides()) {
                IllegalStateException te = new IllegalStateException("Expected to have a 'provides "
                                                                             + ModuleComponent.class.getName()
                                                                             + " with ... ' entry in "
                                                                             + lastKnownModuleInfoFilePath + message);
                if (shouldWarnOnly) {
                    processor.warn(te.getMessage(), te);
                } else {
                    processor.error(te.getMessage(), te);
                }
            }
        }

        if (wasApplicationDefined) {
            Optional<ModuleInfoItem> moduleInfoItem = lastKnownModuleInfoDescriptor.first(APPLICATION_MODULE_INFO);
            if (moduleInfoItem.isEmpty() || !moduleInfoItem.get().provides()) {
                ToolsException te = new ToolsException("Expected to have a 'provides " + Application.class.getName()
                                                               + " with ... ' entry in " + lastKnownModuleInfoFilePath + message);
                if (shouldWarnOnly) {
                    processor.warn(te.getMessage(), te);
                } else {
                    processor.error(te.getMessage(), te);
                }
            }
        }
    }

}
