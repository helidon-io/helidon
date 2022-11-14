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

package io.helidon.pico.tools.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.creator.impl.Msgr;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.ModuleUtils;
import io.helidon.pico.types.TypeName;

/**
 * Tracks the services to process, and ingests them to build the codegen model.
 *
 * The basic flow:
 *
 * 'annotation processor(s)' -> ServicesToProcess -> {@link io.helidon.pico.tools.creator.impl.AbstractCreator}
 * derivative.
 * <p/>
 *
 * Note that the flow might be repeated multiple times since annotation processors by definition are recurrent in
 * nature.
 */
public class ServicesToProcess implements Resetable {

    private static final class INSTANCE {
        private static ServicesToProcess services = new ServicesToProcess();
    }

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
    private final Map<TypeName, Dependencies> servicesToDependencies = new LinkedHashMap<>();
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

    private File lastKnownSourcePathBeingProcessed;
    private String lastKnownTypeSuffix;
    private String moduleName;
    private String lastKnownModuleName;
    private File lastKnownModuleInfoFile;
    private SimpleModuleDescriptor lastKnownModuleInfoDescriptor;
    private File lastGeneratedModuleInfoFile;
    private SimpleModuleDescriptor lastGeneratedModuleInfoDescriptor;
    private String lastGeneratedPackageName;

    public static ServicesToProcess getServicesInstance() {
        return INSTANCE.services;
    }

    @Override
    public void clear() {
        servicesTypeNames.clear();
        requiredModules.clear();
        servicesToContracts.clear();
        servicesToExternalContracts.clear();
        // we intentionally except parent service type names from being cleared - we need to remember this fact!
        if (false) {
            servicesToLockParentServiceTypeName.clear();
            servicesToParentServiceTypeNames.clear();
            servicesToActivatorGenericDecl.clear();
        }
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
    }

    /**
     * Introduce a new service type name to be added.
     *
     * @param serviceTypeName the service type name
     */
    public void addServiceTypeName(TypeName serviceTypeName) {
        servicesTypeNames.add(Objects.requireNonNull(serviceTypeName));
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param parentServiceTypeName the parent for this service type name
     * @return flag indicating whether the parent was accepted
     */
    public boolean setParentServiceType(TypeName serviceTypeName, TypeName parentServiceTypeName) {
        return setParentServiceType(serviceTypeName, parentServiceTypeName, null);
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param parentServiceTypeName the parent for this service type name
     * @return flag indicating whether the parent was accepted
     */
    public boolean setParentServiceType(TypeName serviceTypeName, TypeName parentServiceTypeName, Boolean lockParent) {
        if (Objects.isNull(parentServiceTypeName)) {
            return false;
        }

        Boolean locked = servicesToLockParentServiceTypeName.get(serviceTypeName);
        if (Objects.nonNull(locked)) {
            if (locked) {
                TypeName lockedParentType = servicesToParentServiceTypeNames.get(serviceTypeName);
                if (!parentServiceTypeName.equals(lockedParentType)) {
//                    throw new ToolsException("parent is locked to: " + lockedParentType + " for " + serviceTypeName
//                                                     + "; rejecting: " + parentServiceTypeName);
                    return false;
                }

                return true;
            } else {
                servicesToLockParentServiceTypeName.put(serviceTypeName, lockParent);
            }
        }

        addServiceTypeName(serviceTypeName);
        servicesToParentServiceTypeNames.put(serviceTypeName, parentServiceTypeName);
        if (Objects.nonNull(lockParent)) {
            servicesToLockParentServiceTypeName.put(serviceTypeName, lockParent);
        }

        return true;
    }

    /**
     * @return the map of service type names to each respective super class / parent types.
     */
    public Map<TypeName, TypeName> getServiceTypeToParentServiceTypes() {
        return Collections.unmodifiableMap(servicesToParentServiceTypeNames);
    }

    /**
     * Introduce the activator generic portion of the declaration (e.g., the "CB extends MySingletonConfig" portion of
     * MyService$$picoActivator<CB extends MySingletonConfig>).
     *
     * @param serviceTypeName the service type name
     * @param activatorGenericDecl the generics portion of the class decl
     * @return flag indicating whether the parent was accepted
     */
    public void setActivatorGenericDecl(TypeName serviceTypeName, String activatorGenericDecl) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToActivatorGenericDecl.put(serviceTypeName, activatorGenericDecl);
        assert (Objects.isNull(prev) || Objects.equals(prev, activatorGenericDecl));
    }

    /**
     * @return the map of service type names to activator generic declarations.
     */
    public Map<TypeName, String> getServiceTypeToActivatorGenericDecl() {
        return Collections.unmodifiableMap(servicesToActivatorGenericDecl);
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param access the access level for the service type name
     */
    public void setServiceTypeAccessLevel(TypeName serviceTypeName, InjectionPointInfo.Access access) {
        addServiceTypeName(serviceTypeName);
        if (Objects.nonNull(access)) {
            Object prev = servicesToAccess.put(serviceTypeName, access);
            if (Objects.nonNull(prev) && !access.equals(prev)) {
                throw new ToolsException("can only support one access level for " + serviceTypeName);
            }
        }
    }

    /**
     * @return the map of service type names to each respective access level.
     */
    public Map<TypeName, InjectionPointInfo.Access> getServiceTypeToAccessLevel() {
        return servicesToAccess;
    }

    /**
     * Introduce the flag whether the given service type name is abstract (i.e., interface or abstract) and not concrete.
     *
     * @param serviceTypeName the service type name
     * @param isAbstract whether the service type name is abstract (i.e., interface or abstract)
     */
    public void setServiceTypeIsAbstract(TypeName serviceTypeName, boolean isAbstract) {
        addServiceTypeName(serviceTypeName);
        servicesToIsAbstract.put(serviceTypeName, isAbstract);
    }

    /**
     * @return the map of service type names to whether they are abstract. If not found then assume concrete.
     */
    public Map<TypeName, Boolean> getServiceTypeToIsAbstract() {
        return servicesToIsAbstract;
    }

    /**
     * @return the map of service type names to the super class hierarchy.
     */
    public Map<TypeName, List<TypeName>> getServiceTypeToHierarchy() {
        return serviceTypeHierarchy;
    }

    /**
     * Introduce the parent superclass for a given service type name.
     *
     * @param serviceTypeName the service type name
     * @param serviceTypeHierarchy the list of superclasses (where this service type is the last in the list)
     */
    public void setServiceTypeHierarchy(TypeName serviceTypeName, List<TypeName> serviceTypeHierarchy) {
        addServiceTypeName(serviceTypeName);
        if (Objects.nonNull(serviceTypeHierarchy)) {
            Object prev = this.serviceTypeHierarchy.put(serviceTypeName, serviceTypeHierarchy);
            if (Objects.nonNull(prev) && !serviceTypeHierarchy.equals(prev)) {
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
    public boolean hasHierarchyFor(TypeName serviceTypeName) {
        Collection<?> coll = serviceTypeHierarchy.get(serviceTypeName);
        return Objects.nonNull(coll);
    }

    /**
     * Checks whether the service type has an established set of contracts that are known for it.
     *
     * @param serviceTypeName the service type name
     * @return true if contracts are known about this service type
     */
    public boolean hasContractsFor(TypeName serviceTypeName) {
        Collection<?> coll = servicesToContracts.get(serviceTypeName);
        if (Objects.nonNull(coll)) {
            return true;
        }

        coll = servicesToExternalContracts.get(serviceTypeName);
        if (Objects.nonNull(coll)) {
            return true;
        }

        coll = servicesToProviderFor.get(serviceTypeName);
        return Objects.nonNull(coll);
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
     * Sets the {@link io.helidon.pico.tools.creator.InterceptionPlan} for the given service type.
     *
     * @param serviceTypeName   the service type name
     * @param plan              the interceptor plan
     */
    public void interceptorPlanFor(TypeName serviceTypeName, InterceptionPlan plan) {
        Object prev = interceptorPlanFor.put(serviceTypeName, plan);
        if (Objects.nonNull(prev) && Objects.nonNull(plan)) {
            throw new ToolsException("should only set interception plan once for: " + serviceTypeName);
        }
    }

    /**
     * @return the interception plan for each service type that has a non-null interception plan.
     */
    public Map<TypeName, InterceptionPlan> getInterceptorPlans() {
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
    public Map<TypeName, List<String>> getExtraCodeGen() {
        return extraCodeGen;
    }

    /**
     * Adds extra code gen per service type.
     *
     * @param serviceTypeName the service type name
     * @param codeGen the extra code gen to tack onto the activation implementation
     */
    public void addExtraCodeGen(TypeName serviceTypeName, String codeGen) {
        extraCodeGen.compute(serviceTypeName, (key, val) -> {
            if (Objects.isNull(val)) {
                val = new LinkedList<>();
            }
            val.add(Objects.requireNonNull(codeGen));
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
            if (Objects.isNull(val)) {
                val = new TreeSet<>();
            }
            val.add(contractTypeName);
            return val;
        });

        if (isExternal) {
            servicesToExternalContracts.compute(serviceTypeName, (key, val) -> {
                if (Objects.isNull(val)) {
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
    public void addDependencies(Dependencies dependencies) {
        TypeName serviceTypeName =
                DefaultTypeName.createFromTypeName(Objects.requireNonNull(dependencies.getForServiceTypeName()));
        addServiceTypeName(serviceTypeName);
        Dependencies prevDependencies = servicesToDependencies.get(serviceTypeName);
        if (Objects.nonNull(prevDependencies)) {
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
    public void setPreDestroyMethod(TypeName serviceTypeName, String preDestroyMethodName) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToPreDestroyMethod.put(serviceTypeName, preDestroyMethodName);
        if (Objects.nonNull(prev)) {
            throw new ToolsException("can only support one PreDestroy method for " + serviceTypeName);
        }
    }

    /**
     * Introduces a {@link jakarta.annotation.PostConstruct} method to the model for a given service type.
     *
     * @param serviceTypeName the service type name
     * @param postConstructMethodName the method name
     */
    public void setPostConstructMethod(TypeName serviceTypeName, String postConstructMethodName) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToPostConstructMethod.put(serviceTypeName, postConstructMethodName);
        if (Objects.nonNull(prev)) {
            throw new ToolsException("can only support one PostConstruct method for " + serviceTypeName);
        }
    }

    /**
     * Sets the weight of a service type.
     *
     * @param serviceTypeName the service type name
     * @param priority its priority
     */
    public void setWeightedPriority(TypeName serviceTypeName, Double priority) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToWeightedPriority.put(serviceTypeName, priority);
        if (Objects.nonNull(prev)) {
            throw new ToolsException("can only support one Priority for " + serviceTypeName);
        }
    }

    /**
     * Sets the run level for a service type name.
     *
     * @param serviceTypeName the service type name
     * @param runLevel its run level
     */
    public void setRunLevel(TypeName serviceTypeName, Integer runLevel) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToRunLevel.put(serviceTypeName, runLevel);
        if (Objects.nonNull(prev)) {
            throw new ToolsException("can only support one RunLevel for " + serviceTypeName);
        }
    }

    /**
     * Adds a scope type name for a service type name.
     *
     * @param serviceTypeName the service type name
     * @param scopeTypeName its scope type name
     */
    public void addScopeTypeName(TypeName serviceTypeName, String scopeTypeName) {
        if (Objects.isNull(scopeTypeName)) {
            return;
        }
        addServiceTypeName(serviceTypeName);

        Object prev = servicesToScopeTypeNames.compute(serviceTypeName, (k, v) -> {
            if (Objects.isNull(v)) {
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
    public void setProviderFor(TypeName serviceTypeName, Set<TypeName> providerFor) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToProviderFor.put(serviceTypeName, providerFor);
        if (Objects.nonNull(prev) && !prev.equals(providerFor)) {
            throw new ToolsException("can only support setting isProvider once for " + serviceTypeName);
        }
    }

    /**
     * Sets the qualifiers associated with a service type.
     *
     * @param serviceTypeName the service type name
     * @param qualifiers its qualifiers
     */
    public void setQualifiers(TypeName serviceTypeName, Set<QualifierAndValue> qualifiers) {
        addServiceTypeName(serviceTypeName);
        Object prev = servicesToQualifiers.put(serviceTypeName, qualifiers);
        if (Objects.nonNull(prev)) {
            throw new ToolsException("can only support setting qualifiers once for " + serviceTypeName
                    + "; prev = " + prev + " and new = " + qualifiers);
        }
    }

    /**
     * @return Fetches the set of known service type names being processed in this batch.
     */
    public List<TypeName> getServiceTypeNames() {
        ArrayList<TypeName> result = new ArrayList<>(servicesTypeNames);
        Collections.sort(result);
        return result;
    }

    /**
     * @return Fetches the map of service types to their set of contracts for that service type.
     */
    public Map<TypeName, Set<TypeName>> getServicesToContracts() {
        return new TreeMap<>(servicesToContracts);
    }

    /**
     * @return Fetches the map of service types to their set of contracts for that service type.
     */
    public Map<TypeName, Set<TypeName>> getServicesToExternalContracts() {
        return new TreeMap<>(servicesToExternalContracts);
    }

    /**
     * @return Fetches the map of service types to their injection point dependencies.
     */
    public Map<TypeName, Dependencies> getServicesToInjectionPointDependencies() {
        return new TreeMap<>(servicesToDependencies);
    }

    /**
     * @return Fetches the map of service types to their post construct methods.
     */
    public Map<TypeName, String> getServicesToPostConstructMethodNames() {
        return new TreeMap<>(servicesToPostConstructMethod);
    }

    /**
     * @return Fetches the map of service types to their pre destroy methods.
     */
    public Map<TypeName, String> getServicesToPreDestroyMethodNames() {
        return new TreeMap<>(servicesToPreDestroyMethod);
    }

    /**
     * @return Fetches the map of service types to their priorities.
     */
    public Map<TypeName, Double> getServicesToWeightedPriorities() {
        return new TreeMap<>(servicesToWeightedPriority);
    }

    /**
     * @return Fetches the map of service types to their run levels.
     */
    public Map<TypeName, Integer> getServicesToRunLevels() {
        return new TreeMap<>(servicesToRunLevel);
    }

    /**
     * @return Fetches the map of service types to their scope type names.
     */
    public Map<TypeName, Set<String>> getServicesToScopeTypeNames() {
        return new TreeMap<>(servicesToScopeTypeNames);
    }

    /**
     * @return Fetches the map of service types to the set of services they provide.
     */
    public Map<TypeName, Set<TypeName>> getServicesToProviderFor() {
        return new TreeMap<>(servicesToProviderFor);
    }

    /**
     * @return Fetches the map of service types to the set of qualifiers associated with each.
     */
    public Map<TypeName, Set<QualifierAndValue>> getQualifiers() {
        return new TreeMap<>(servicesToQualifiers);
    }

    /**
     * Introduces the need for external modules.
     *
     * @param serviceTypeName the service type name
     * @param moduleNames the required module names to support known external contracts
     */
    public void addExternalRequiredModules(TypeName serviceTypeName, Collection<String> moduleNames) {
        if (Objects.nonNull(moduleNames)) {
            requiredModules.addAll(moduleNames);
        }
    }

    /**
     * @return the set of required (external) module names.
     */
    public Set<String> getRequiredModules() {
        return new TreeSet<>(requiredModules);
    }

    /**
     * Sets this module name.
     *
     * @param moduleName the module name
     */
    public void setModuleName(String moduleName) {
        // special note: the compiler uses the same jvm instance for each round, including source and test, so we
        // cannot guard against changes here!!
        //        if (Objects.nonNull(this.moduleName) && !this.moduleName.equals(moduleName)) {
        //            throw new ToolsException("can only support setting module name once: " + this.moduleName + "
        //            and " + moduleName);
        //        }
        this.moduleName = moduleName;
        if (Objects.nonNull(moduleName)) {
            this.lastKnownModuleName = moduleName;
        }
    }

    /**
     * @return Fetches this module name.
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * The last known descriptor being processed.
     *
     * @param descriptor the descriptor
     */
    public void setLastKnownModuleInfoDescriptor(SimpleModuleDescriptor descriptor) {
        this.lastKnownModuleInfoDescriptor = descriptor;
        if (Objects.nonNull(descriptor)) {
            setModuleName(descriptor.getName());
        }
    }

    /**
     * @return Fetches the last known module info descriptor.
     */
    public SimpleModuleDescriptor getLastKnownModuleInfoDescriptor() {
        return lastKnownModuleInfoDescriptor;
    }

    /**
     * The last known file location for the module-info descriptor being processed.
     *
     * @param lastKnownModuleInfoFile the file location for the descriptor
     */
    public void setLastKnownModuleInfoFile(File lastKnownModuleInfoFile) {
        this.lastKnownModuleInfoFile = lastKnownModuleInfoFile;
    }

    /**
     * @return Fetches the last known module info file.
     */
    public File getLastKnownModuleInfoFile() {
        return lastKnownModuleInfoFile;
    }

    /**
     * Sets the last generated module info descriptor and its location.
     *
     * @param descriptor the descriptor
     * @param location the location for the descriptor
     */
    public void setLastGeneratedModuleInfoDescriptor(SimpleModuleDescriptor descriptor, File location) {
        this.lastGeneratedModuleInfoDescriptor = descriptor;
        this.lastGeneratedModuleInfoFile = location;
    }

    /**
     * @return Fetches the last generated module descriptor.
     */
    public SimpleModuleDescriptor getLastGeneratedModuleInfoDescriptor() {
        return lastGeneratedModuleInfoDescriptor;
    }

    /**
     * @return Fetches the last generated module descriptor location.
     */
    public File getLastGeneratedModuleInfoFile() {
        return lastGeneratedModuleInfoFile;
    }

    /**
     * Sets the last known source path being processed.
     *
     * @param lastKnownSourcePathBeingProcessed the last source path being processed
     */
    public void setLastKnownSourcePathBeingProcessed(File lastKnownSourcePathBeingProcessed) {
        this.lastKnownSourcePathBeingProcessed = lastKnownSourcePathBeingProcessed;
    }

    /**
     * @return Fetches the last known source path being processed.
     */
    public File getLastKnownSourcePathBeingProcessed() {
        return lastKnownSourcePathBeingProcessed;
    }

    /**
     * Sets the last known type suffix (e.g., "test").
     *
     * @param typeSuffix the optional type suffix
     */
    public void setLastKnownTypeSuffix(String typeSuffix) {
        this.lastKnownTypeSuffix = typeSuffix;
    }

    /**
     * @return Fetches the last known type suffix.
     */
    public String getLastKnownTypeSuffix() {
        return lastKnownTypeSuffix;
    }

    /**
     * Sets the last generated package name.
     *
     * @param lastGeneratedPackageName the package name
     */
    public void setLastGeneratedPackageName(String lastGeneratedPackageName) {
        this.lastGeneratedPackageName = lastGeneratedPackageName;
    }

    /**
     * @return Fetches the last generated package name.
     */
    public String getLastGeneratedPackageName() {
        return lastGeneratedPackageName;
    }

    /**
     * @return Attempts to determine the generated module name based upon the batch of services being processed.
     */
    public String determineGeneratedModuleName() {
        String moduleName = getModuleName();
        moduleName = ModuleUtils.toSuggestedModuleName(moduleName,
                                                       getLastKnownTypeSuffix(),
                                                       SimpleModuleDescriptor.DEFAULT_MODULE_NAME);
        return moduleName;
    }

    /**
     * @return Attempts to determine the generated package name based upon the batch of services being processed.
     */
    public String determineGeneratedPackageName() {
        String export = getLastGeneratedPackageName();
        if (Objects.nonNull(export)) {
            return export;
        }

        SimpleModuleDescriptor descriptor = getLastKnownModuleInfoDescriptor();
        String packageName = Objects
                .requireNonNull(ModuleUtils
                                        .getSuggestedGeneratedPackageName(descriptor,
                                                                          getServiceTypeNames(),
                                                                          PicoServicesConfig.NAME));
        return packageName;
    }

    /**
     * @return Fetches the last known module name.
     */
    public String getLastKnownModuleName() {
        return lastKnownModuleName;
    }

    /**
     * Called to signal the beginning of an annotation processing phase.
     *
     * @param processor the processor running
     * @param annotations the annotations being processed
     * @param roundEnv the round env
     */
    public static void onBeginProcessing(Msgr processor,
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
    public static void onEndProcessing(Msgr processor,
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
    public static boolean isRunning() {
        return RUNNING_PROCESSORS.get() > 0;
    }

}
