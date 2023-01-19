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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.Application;
import io.helidon.pico.DefaultInjectionPointInfo;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.DependencyInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.RunLevel;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.services.AbstractServiceProvider;
import io.helidon.pico.services.Dependencies;
import io.helidon.pico.types.TypeName;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.helidon.pico.tools.CommonUtils.first;
import static io.helidon.pico.tools.CommonUtils.hasValue;
import static io.helidon.pico.tools.CommonUtils.toFlatName;
import static io.helidon.pico.tools.CommonUtils.toSet;
import static io.helidon.pico.tools.TypeTools.componentTypeNameOf;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromClassInfo;
import static io.helidon.pico.tools.TypeTools.isPackagePrivate;
import static io.helidon.pico.types.DefaultTypeName.create;
import static io.helidon.pico.types.DefaultTypeName.createFromTypeName;

/**
 * Responsible for building all pico-di related collateral for a module, including:
 * <li>The {@link io.helidon.pico.ServiceProvider} for each service type implementation passed in.
 * <li>The {@link io.helidon.pico.Activator} and {@link io.helidon.pico.DeActivator} for each service type implementation passed in.
 * <li>The {@link Module} for the aggregate service provider bindings for the same set of service type names.
 * <li>The module-info as appropriate for the above set of services (and contracts).
 * <li>The /META-INF/services entries as appropriate.
 *
 * This API can also be used to only produce meta-information describing the model without the codegen option - see
 * {@link ActivatorCreatorRequest#codeGenPaths()} for details.
 *
 * @deprecated
 */
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 1)
public class DefaultActivatorCreator extends AbstractCreator implements ActivatorCreator, Weighted {

    /**
     * The suffix name for the service type activator class.
     */
    static final String INNER_ACTIVATOR_CLASS_NAME = "$$" + NAME + "Activator";
    private static final String SERVICE_PROVIDER_ACTIVATOR_HBS = "service-provider-activator.hbs";
    private static final String SERVICE_PROVIDER_APPLICATION_STUB_HBS = "service-provider-application-stub.hbs";
    private static final String SERVICE_PROVIDER_MODULE_HBS = "service-provider-module.hbs";

    /**
     * Constructor.
     *
     * @deprecated
     */
    public DefaultActivatorCreator() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    String templateName() {
        return templateName;
    }

    @Override
    public ActivatorCreatorResponse createModuleActivators(
            ActivatorCreatorRequest req) throws ToolsException {
        String templateName = (hasValue(req.templateName())) ? req.templateName() : templateName();

        DefaultActivatorCreatorResponse.Builder builder = DefaultActivatorCreatorResponse.builder()
                .configOptions(req.configOptions())
                .templateName(templateName);

        if (req.serviceTypeNames().isEmpty()) {
            return handleError(req, new ToolsException("ServiceTypeNames is required to be passed"), builder);
        }

        try {
            LazyValue<ScanResult> scan = LazyValue.create(ReflectionHandler.INSTANCE::scan);
            return codegen(req, builder, scan);
        } catch (ToolsException te) {
            return handleError(req, te, builder);
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            return handleError(req, new ToolsException("failed in create", t), builder);
        }
    }

    @SuppressWarnings("unchecked")
    ActivatorCreatorResponse codegen
            (ActivatorCreatorRequest req,
             DefaultActivatorCreatorResponse.Builder builder,
             LazyValue<ScanResult> scan) {
        boolean isApplicationPreCreated = req.configOptions().isApplicationPreCreated();
        boolean isModuleCreated = req.configOptions().isModuleCreated();
        CodeGenPaths codeGenPaths = req.codeGenPaths();
        Map<TypeName, Boolean> serviceTypeToIsAbstractType = req.codeGen().serviceTypeIsAbstractTypes();
        List<TypeName> activatorTypeNames = new ArrayList<>();
        List<TypeName> activatorTypeNamesPutInModule = new ArrayList<>();
        Map<TypeName, ActivatorCodeGenDetail> activatorDetails = new LinkedHashMap<>();
        for (TypeName serviceTypeName : req.serviceTypeNames()) {
            ActivatorCodeGenDetail activatorDetail = createActivatorCodeGenDetail(req, serviceTypeName, scan);
            Object prev = activatorDetails.put(serviceTypeName, activatorDetail);
            assert (prev == null);
            codegenActivatorFilerOut(req, activatorDetail);
            TypeName activatorTypeName = toActivatorImplTypeName(serviceTypeName);
            activatorTypeNames.add(activatorTypeName);
            Boolean isAbstract = serviceTypeToIsAbstractType.get(serviceTypeName);
            isAbstract = (isAbstract != null) && isAbstract;
            if (!isAbstract) {
                activatorTypeNamesPutInModule.add(activatorTypeName);
            }

            InterceptionPlan interceptionPlan = req.codeGen().serviceTypeInterceptionPlan().get(serviceTypeName);
            if (interceptionPlan != null) {
                codegenInterceptorFilerOut(req, builder, interceptionPlan);
            }
        }
        builder.serviceTypeNames(activatorTypeNames)
                .serviceTypeDetails(activatorDetails);

        ModuleDetail moduleDetail;
        TypeName applicationTypeName;
        Map<String, List<String>> metaInfServices;
        TypeName moduleTypeName = toModuleTypeName(req, activatorTypeNames);
        if (moduleTypeName != null) {
            String className = DefaultApplicationCreator
                    .toApplicationClassName(req.codeGen().classPrefixName());
            applicationTypeName = create(moduleTypeName.packageName(), className);
            builder.applicationTypeName(applicationTypeName);
            String applicationStub = toApplicationStubBody(req, applicationTypeName, req.moduleName().orElse(null));
            if (isApplicationPreCreated && isModuleCreated) {
                codegenApplicationFilerOut(req, applicationTypeName, applicationStub);
            }

            moduleDetail = toModuleDetail(req,
                                          activatorTypeNamesPutInModule,
                                          moduleTypeName,
                                          applicationTypeName,
                                          isApplicationPreCreated,
                                          isModuleCreated);
            builder.moduleDetail(moduleDetail);
            if (moduleDetail != null && isModuleCreated) {
                codegenModuleFilerOut(req, moduleDetail);
                File out = codegenModuleInfoFilerOut(req, moduleDetail.descriptor().orElseThrow());
                logger().log(System.Logger.Level.DEBUG, "codegen module-info written to: " + out);
            }

            metaInfServices = toMetaInfServices(moduleDetail,
                                                applicationTypeName,
                                                isApplicationPreCreated,
                                                isModuleCreated);
            builder.metaInfServices(metaInfServices);
            if (!metaInfServices.isEmpty() && req.configOptions().isModuleCreated()) {
                codegenMetaInfServices(req, codeGenPaths, metaInfServices);
            }
        }

        return builder.build();
    }

    private ModuleDetail toModuleDetail(
            ActivatorCreatorRequest req,
            List<TypeName> activatorTypeNamesPutInModule,
            TypeName moduleTypeName,
            TypeName applicationTypeName,
            boolean isApplicationCreated,
            boolean isModuleCreated) {
        String className = moduleTypeName.className();
        String packageName = moduleTypeName.packageName();
        String moduleName = req.moduleName().orElse(null);
        String generator = req.generator();

        ActivatorCreatorCodeGen codeGen = req.codeGen();
        String typePrefix = codeGen.classPrefixName();
        Collection<String> modulesRequired = codeGen.modulesRequired();
        Map<TypeName, Set<TypeName>> serviceTypeContracts = codeGen.serviceTypeContracts();
        Map<TypeName, Set<TypeName>> externalContracts = codeGen.serviceTypeExternalContracts();

        String moduleInfoPath = req.codeGenPaths().moduleInfoPath();
        ModuleInfoCreatorRequest moduleCreatorRequest = DefaultModuleInfoCreatorRequest.builder()
                .name(moduleName)
                .moduleTypeName(moduleTypeName)
                .applicationTypeName(applicationTypeName)
                .modulesRequired(modulesRequired)
                .contracts(serviceTypeContracts)
                .externalContracts(externalContracts)
                .moduleInfoPath(moduleInfoPath)
                .classPrefixName(typePrefix)
                .applicationCreated(isApplicationCreated)
                .moduleCreated(isModuleCreated)
                .build();
        ModuleInfoDescriptor moduleInfo = createModuleInfo(moduleCreatorRequest);
        moduleName = moduleInfo.name();
        String moduleBody = toModuleBody(req, packageName, className, moduleName, activatorTypeNamesPutInModule);
        return DefaultModuleDetail.builder()
                .moduleName(moduleName)
                .moduleTypeName(moduleTypeName)
                .serviceProviderActivatorTypeNames(activatorTypeNamesPutInModule)
                .moduleBody(moduleBody)
                .moduleInfoBody(moduleInfo.contents())
                .descriptor(moduleInfo)
                .build();
    }

    /**
     * Applies to module-info.
     */
    static TypeName toModuleTypeName(
            ActivatorCreatorRequest req,
            List<TypeName> activatorTypeNames) {
        String packageName;
        if (hasValue(req.packageName().orElse(null))) {
            packageName = req.packageName().orElseThrow();
        } else {
            if (activatorTypeNames == null || activatorTypeNames.isEmpty()) {
                return null;
            }
            packageName = activatorTypeNames.get(0).packageName() + "." + NAME;
        }

        String className = toModuleClassName(req.codeGen().classPrefixName());
        return create(packageName, className);
    }

    static String toModuleClassName(
            String modulePrefix) {
        modulePrefix = (modulePrefix == null) ? "" : modulePrefix;
        return NAME + modulePrefix + "Module";
    }

    static Map<String, List<String>> toMetaInfServices(
            ModuleDetail moduleDetail,
            TypeName applicationTypeName,
            boolean isApplicationCreated,
            boolean isModuleCreated) {
        Map<String, List<String>> metaInfServices = new LinkedHashMap<>();
        if (isApplicationCreated && applicationTypeName != null) {
            metaInfServices.put(Application.class.getName(),
                                List.of(applicationTypeName.name()));
        }
        if (isModuleCreated && moduleDetail != null) {
            metaInfServices.put(Module.class.getName(),
                                List.of(moduleDetail.moduleTypeName().name()));
        }
        return metaInfServices;
    }

    void codegenMetaInfServices(
            GeneralCreatorRequest req,
            CodeGenPaths paths,
            Map<String, List<String>> metaInfServices) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenMetaInfServices(paths, metaInfServices);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    void codegenActivatorFilerOut(
            GeneralCreatorRequest req,
            ActivatorCodeGenDetail activatorDetail) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenActivatorFilerOut(activatorDetail);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    void codegenModuleFilerOut(
            GeneralCreatorRequest req,
            ModuleDetail moduleDetail) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenModuleFilerOut(moduleDetail);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    void codegenApplicationFilerOut(
            GeneralCreatorRequest req,
            TypeName applicationTypeName,
            String applicationBody) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            req.filer().codegenApplicationFilerOut(applicationTypeName, applicationBody);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    File codegenModuleInfoFilerOut(
            GeneralCreatorRequest req,
            ModuleInfoDescriptor descriptor) {
        boolean prev = true;
        if (req.analysisOnly()) {
            prev = CodeGenFiler.filerEnabled(false);
        }

        try {
            return req.filer().codegenModuleInfoFilerOut(descriptor, true);
        } finally {
            if (req.analysisOnly()) {
                CodeGenFiler.filerEnabled(prev);
            }
        }
    }

    @Override
    public InterceptorCreatorResponse codegenInterceptors(
            GeneralCreatorRequest req,
            Map<TypeName, InterceptionPlan> interceptionPlans) {
        DefaultInterceptorCreatorResponse.Builder res = DefaultInterceptorCreatorResponse.builder();
        res.interceptionPlans(interceptionPlans);

        for (Map.Entry<TypeName, InterceptionPlan> e : interceptionPlans.entrySet()) {
            try {
                File file = codegenInterceptorFilerOut(req, null, e.getValue());
                res.addGeneratedFile(e.getKey(), file);
            } catch (Throwable t) {
                throw new ToolsException("Failed while processing: " + e.getKey(), t);
            }
        }

        return res.build();
    }

    private File codegenInterceptorFilerOut(
            GeneralCreatorRequest req,
            DefaultActivatorCreatorResponse.Builder builder,
            InterceptionPlan interceptionPlan) {
        TypeName interceptorTypeName = DefaultInterceptorCreator.createInterceptorSourceTypeName(interceptionPlan);
        DefaultInterceptorCreator interceptorCreator = new DefaultInterceptorCreator();
        String body = interceptorCreator.createInterceptorSourceBody(interceptionPlan);
        if (builder != null) {
            builder.addServiceTypeInterceptorPlan(interceptorTypeName, interceptionPlan);
        }
        return req.filer().codegenJavaFilerOut(interceptorTypeName, body);
    }

    private ActivatorCodeGenDetail createActivatorCodeGenDetail(
            ActivatorCreatorRequest req,
            TypeName serviceTypeName,
            LazyValue<ScanResult> scan) {
        ActivatorCreatorCodeGen codeGen = req.codeGen();
        String template = templateHelper.safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_ACTIVATOR_HBS);
        ServiceInfoBasics serviceInfo = toServiceInfo(serviceTypeName, codeGen);
        TypeName activatorTypeName = toActivatorTypeName(serviceTypeName);
        TypeName parentTypeName = toParentTypeName(serviceTypeName, codeGen);
        String activatorGenericDecl = toActivatorGenericDecl(serviceTypeName, codeGen);
        DependenciesInfo dependencies = toDependencies(serviceTypeName, codeGen);
        DependenciesInfo parentDependencies = toDependencies(parentTypeName, codeGen);
        Set<String> scopeTypeNames = toScopeTypeNames(serviceTypeName, codeGen);
        String generatedSticker = toGeneratedSticker(req);
        List<String> description = toDescription(serviceTypeName);
        Double weightedPriority = toWeightedPriority(serviceTypeName, codeGen);
        Integer runLevel = toRunLevel(serviceTypeName, codeGen);
        String postConstructMethodName = toPostConstructMethodName(serviceTypeName, codeGen);
        String preDestroyMethodName = toPreDestroyMethodName(serviceTypeName, codeGen);
        List<?> serviceTypeInjectionOrder = toServiceTypeHierarchy(serviceTypeName, codeGen, scan);
        List<String> extraCodeGen = getExtraCodeGen(serviceTypeName, codeGen);
        boolean isProvider = toIsProvider(serviceTypeName, codeGen);
        boolean isConcrete = toIsConcrete(serviceTypeName, codeGen);
        boolean isSupportsJsr330InStrictMode = req.configOptions().isSupportsJsr330InStrictMode();
        Collection<Object> injectionPointsSkippedInParent =
                toCodegenInjectMethodsSkippedInParent(isSupportsJsr330InStrictMode, activatorTypeName, codeGen, scan);

        ActivatorCreatorArgs args = DefaultActivatorCreatorArgs.builder()
                .template(template)
                .serviceTypeName(serviceTypeName)
                .activatorTypeName(activatorTypeName)
                .activatorGenericDecl(activatorGenericDecl)
                .parentTypeName(parentTypeName)
                .scopeTypeNames(scopeTypeNames)
                .description(description)
                .serviceInfo(serviceInfo)
                .dependencies(dependencies)
                .parentDependencies(parentDependencies)
                .injectionPointsSkippedInParent(injectionPointsSkippedInParent)
                .serviceTypeInjectionOrder(serviceTypeInjectionOrder)
                .generatedSticker(generatedSticker)
                .weightedPriority(weightedPriority)
                .runLevel(runLevel)
                .postConstructMethodName(postConstructMethodName)
                .preDestroyMethodName(preDestroyMethodName)
                .extraCodeGen(extraCodeGen)
                .concrete(isConcrete)
                .provider(isProvider)
                .supportsJsr330InStrictMode(isSupportsJsr330InStrictMode)
                .build();
        String activatorBody = toActivatorBody(args);

        return DefaultActivatorCodeGenDetail.builder()
                .serviceInfo(serviceInfo)
                .dependencies(dependencies)
                .serviceTypeName(toActivatorImplTypeName(activatorTypeName))
                .body(activatorBody)
                .build();
    }

    /**
     * Creates a payload given the batch of services to process.
     *
     * @param services  the services to process
     * @return the payload, or empty if unable or nothing to process
     */
    public static Optional<ActivatorCreatorCodeGen> createActivatorCreatorCodeGen(
            ServicesToProcess services) {
        // do not generate activators for modules or applications...
        List<TypeName> serviceTypeNames = services.serviceTypeNames();
        if (!serviceTypeNames.isEmpty()) {
            TypeName applicationTypeName = create(Application.class);
            TypeName moduleTypeName = create(Application.class);
            serviceTypeNames = serviceTypeNames.stream()
                    .filter(typeName -> {
                        Set<TypeName> contracts = services.contracts().get(typeName);
                        if (contracts == null) {
                            return true;
                        }
                        return !contracts.contains(applicationTypeName) && !contracts.contains(moduleTypeName);
                    })
                    .collect(Collectors.toList());
        }
        if (serviceTypeNames.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(DefaultActivatorCreatorCodeGen.builder()
                .serviceTypeToParentServiceTypes(toFilteredParentServiceTypes(services))
                .serviceTypeToActivatorGenericDecl(services.activatorGenericDecls())
                .serviceTypeHierarchy(toFilteredHierarchy(services))
                .serviceTypeAccessLevels(services.accessLevels())
                .serviceTypeIsAbstractTypes(services.isAbstractMap())
                .serviceTypeContracts(toFilteredContracts(services))
                .serviceTypeExternalContracts(services.externalContracts())
                .serviceTypeInjectionPointDependencies(services.injectionPointDependencies())
                .serviceTypePostConstructMethodNames(services.postConstructMethodNames())
                .serviceTypePreDestroyMethodNames(services.preDestroyMethodNames())
                .serviceTypeWeights(services.weightedPriorities())
                .serviceTypeRunLevels(services.runLevels())
                .serviceTypeScopeNames(services.scopeTypeNames())
                .serviceTypeToProviderForTypes(services.providerForTypeNames())
                .serviceTypeQualifiers(services.qualifiers())
                .modulesRequired(services.requiredModules())
                .classPrefixName(services.lastKnownTypeSuffix())
                .serviceTypeInterceptionPlan(services.interceptorPlans())
                .extraCodeGen(services.extraCodeGen())
                .build());
    }

    /**
     * Create a request based upon the contents of services to processor
     *
     * @param servicesToProcess the batch being processed
     * @param codeGen           the code gen request
     * @param configOptions     the config options
     * @param filer             the filer
     * @param throwIfError      fail on error?
     * @return the activator request instance
     */
    public static ActivatorCreatorRequest createActivatorCreatorRequest(
            ServicesToProcess servicesToProcess,
            ActivatorCreatorCodeGen codeGen,
            ActivatorCreatorConfigOptions configOptions,
            CodeGenFiler filer,
            boolean throwIfError) {
        String moduleName = servicesToProcess.determineGeneratedModuleName();
        String packageName = servicesToProcess.determineGeneratedPackageName();

        CodeGenPaths codeGenPaths = createCodeGenPaths(servicesToProcess);

        return DefaultActivatorCreatorRequest.builder()
                .serviceTypeNames(servicesToProcess.serviceTypeNames())
                .codeGen(codeGen)
                .codeGenPaths(codeGenPaths)
                .filer(filer)
                .configOptions(configOptions)
                .throwIfError(throwIfError)
                .moduleName(moduleName)
                .packageName(packageName)
                .build();
    }

    private static Map<TypeName, TypeName> toFilteredParentServiceTypes(
            ServicesToProcess services) {
        Map<TypeName, TypeName> parents = services.parentServiceTypes();
        Map<TypeName, TypeName> filteredParents = new LinkedHashMap<>(parents);
        for (Map.Entry<TypeName, TypeName> e : parents.entrySet()) {
            if (e.getValue() != null
                    && !services.serviceTypeNames().contains(e.getValue())
                    // if the caller is declaring a parent with generics, then assume they know what they are doing
                    && !e.getValue().fqName().contains("<")) {
                TypeName serviceTypeName = e.getKey();
                if (services.activatorGenericDecls().get(serviceTypeName) == null) {
                    filteredParents.put(e.getKey(), null);
                }
            }
        }
        return filteredParents;
    }

    private static Map<TypeName, List<TypeName>> toFilteredHierarchy(
            ServicesToProcess services) {
        Map<TypeName, List<TypeName>> hierarchy = services.serviceTypeToHierarchy();
        Map<TypeName, List<TypeName>> filteredHierarchy = new LinkedHashMap<>();
        for (Map.Entry<TypeName, List<TypeName>> e : hierarchy.entrySet()) {
            List<TypeName> filtered = e.getValue().stream()
                    .filter((typeName) -> services.serviceTypeNames().contains(typeName))
                    .collect(Collectors.toList());
            assert (!filtered.isEmpty()) : e;
            filteredHierarchy.put(e.getKey(), filtered);
        }
        return filteredHierarchy;
    }

    private static Map<TypeName, Set<TypeName>> toFilteredContracts(
            ServicesToProcess services) {
        Map<TypeName, Set<TypeName>> contracts = services.contracts();
        Map<TypeName, Set<TypeName>> filteredContracts = new LinkedHashMap<>();
        for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
            Set<TypeName> contractsForThisService = e.getValue();
            Set<TypeName> externalContractsForThisService = services.externalContracts().get(e.getKey());
            if (externalContractsForThisService == null || externalContractsForThisService.isEmpty()) {
                filteredContracts.put(e.getKey(), e.getValue());
            } else {
                Set<TypeName> filteredContractsForThisService = new LinkedHashSet<>(contractsForThisService);
                filteredContractsForThisService.removeAll(externalContractsForThisService);
                filteredContracts.put(e.getKey(), filteredContractsForThisService);
            }
        }
        return filteredContracts;
    }

    String toApplicationStubBody(
            ActivatorCreatorRequest req,
            TypeName applicationTypeName,
            String moduleName) {
        String template = templateHelper.safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_STUB_HBS);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", applicationTypeName.className());
        subst.put("packagename", applicationTypeName.packageName());
        subst.put("description", null);
        subst.put("generatedanno", toGeneratedSticker(req));
        subst.put("modulename", moduleName);

        return templateHelper.applySubstitutions(template, subst, true).trim();
    }

    String toModuleBody(
            ActivatorCreatorRequest req,
            String packageName,
            String className,
            String moduleName,
            List<TypeName> activatorTypeNames) {
        String template = templateHelper.safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_MODULE_HBS);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", className);
        subst.put("packagename", packageName);
        subst.put("description", null);
        subst.put("generatedanno", toGeneratedSticker(req));
        subst.put("modulename", moduleName);
        subst.put("activators", activatorTypeNames);

        return templateHelper.applySubstitutions(template, subst, true).trim();
    }

    @Override
    public TypeName toActivatorImplTypeName(
            TypeName serviceTypeName) {
        return create(serviceTypeName.packageName(),
                                      toFlatName(serviceTypeName.className())
                                              + INNER_ACTIVATOR_CLASS_NAME);
    }

    private String toActivatorBody(
            ActivatorCreatorArgs args) {
        Map<String, Object> subst = new HashMap<>();
        subst.put("activatorsuffix", INNER_ACTIVATOR_CLASS_NAME);
        subst.put("classname", args.activatorTypeName().className());
        subst.put("flatclassname", toFlatName(args.activatorTypeName().className()));
        subst.put("packagename", args.activatorTypeName().packageName());
        subst.put("activatorgenericdecl", args.activatorGenericDecl());
        subst.put("parent", toCodenParent(args.isSupportsJsr330InStrictMode(),
                                          args.activatorTypeName(), args.parentTypeName()));
        subst.put("scopetypenames", args.scopeTypeNames());
        subst.put("description", args.description());
        subst.put("generatedanno", args.generatedSticker());
        subst.put("isprovider", args.isProvider());
        subst.put("isconcrete", args.isConcrete());
        subst.put("contracts", args.serviceInfo().contractsImplemented());
        if (args.serviceInfo() instanceof ServiceInfo) {
            subst.put("externalcontracts", ((ServiceInfo) args.serviceInfo()).externalContractsImplemented());
        }
        subst.put("qualifiers", toCodegenQualifiers(args.serviceInfo().qualifiers()));
        subst.put("dependencies", toCodegenDependencies(args.dependencies()));
        subst.put("weight", args.weightedPriority());
        subst.put("isweightset", Objects.nonNull(args.weightedPriority()));
        subst.put("runlevel", args.runLevel());
        subst.put("isrunlevelset", Objects.nonNull(args.runLevel()));
        subst.put("postconstruct", args.postConstructMethodName());
        subst.put("predestroy", args.preDestroyMethodName());
        subst.put("ctorarglist", toCodegenCtorArgList(args.dependencies()));
        subst.put("ctorargs", toCodegenInjectCtorArgs(args.dependencies()));
        subst.put("injectedfields", toCodegenInjectFields(args.dependencies()));
        subst.put("injectedmethods", toCodegenInjectMethods(args.activatorTypeName(), args.dependencies()));
        subst.put("injectedmethodsskippedinparent", args.injectionPointsSkippedInParent());
        subst.put("extracodegen", args.extraCodeGen());
        subst.put("injectionorder", args.serviceTypeInjectionOrder());
        subst.put("issupportsjsr330instrictmode", args.isSupportsJsr330InStrictMode());

        logger().log(System.Logger.Level.DEBUG, "dependencies for "
                + args.serviceTypeName() + " == " + args.dependencies());

        return templateHelper.applySubstitutions(args.template(), subst, true).trim();
    }

    String toCodenParent(
            boolean ignoredIsSupportsJsr330InStrictMode,
            TypeName activatorTypeName,
            TypeName parentTypeName) {
        String result;
        if (parentTypeName == null || Object.class.getName().equals(parentTypeName.name())) {
//            getFiler().getMessager().log(activatorTypeName + " path is: 1: " + ServicesToProcess.getServicesInstance().getServiceTypeToParentServiceTypes());
            result = AbstractServiceProvider.class.getName() + "<" + activatorTypeName.className() + ">";
        } else if (parentTypeName.typeArguments() == null || parentTypeName.typeArguments().isEmpty()) {
//            getFiler().getMessager().log(activatorTypeName + " path is: 2");
            result = parentTypeName.packageName()
                    + (Objects.isNull(parentTypeName.packageName()) ? "" : ".")
                    + parentTypeName.className().replace(".", "$")
                    + INNER_ACTIVATOR_CLASS_NAME;
        } else {
//            getFiler().getMessager().log(activatorTypeName + " path is: 3");
            result = parentTypeName.fqName();
        }

//        getFiler().getMessager().log(activatorTypeName + " parent is: " + result);
        return result;
    }

    List<String> toCodegenDependencies(
            DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        List<String> result = new ArrayList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies()
                        .forEach(dep2 -> result.add(toCodegenDependency(dep1.dependencyTo(), dep2))));

        return result;
    }

    String toCodegenDependency(
            ServiceInfoCriteria dependencyTo,
            InjectionPointInfo ipInfo) {
        StringBuilder builder = new StringBuilder();
        //.add("world", World.class, InjectionPointInfo.ElementKind.FIELD, InjectionPointInfo.Access.PACKAGE_PRIVATE)
        String elemName = Objects.requireNonNull(ipInfo.elementName());
        if (ipInfo.elementKind() == InjectionPointInfo.ElementKind.CONSTRUCTOR && elemName.equals(InjectionPointInfo.CONSTRUCTOR)) {
            elemName = "CTOR";
        } else {
            elemName = "\"" + elemName + "\"";
        }
        builder.append(".add(").append(elemName).append(", ");
        builder.append(Objects.requireNonNull(componentTypeNameOf(first(dependencyTo.contractsImplemented(), true))))
                .append(".class, ");
        builder.append("ElementKind.").append(Objects.requireNonNull(ipInfo.elementKind())).append(", ");
        if (InjectionPointInfo.ElementKind.FIELD != ipInfo.elementKind()) {
                builder.append(ipInfo.elementArgs().orElseThrow()).append(", ");
        }
        builder.append("Access.").append(Objects.requireNonNull(ipInfo.access())).append(")");
        Integer elemPos = ipInfo.elementArgs().orElse(null);
        Set<QualifierAndValue> qualifiers = ipInfo.qualifiers();
        if (elemPos != null) {
            builder.append(".elemOffset(").append(elemPos).append(")");
        }
        if (!qualifiers.isEmpty()) {
            builder.append(toCodegenQualifiers(qualifiers));
        }
        if (ipInfo.listWrapped()) {
            builder.append(".setIsListWrapped()");
        }
        if (ipInfo.providerWrapped()) {
            builder.append(".setIsProviderWrapped()");
        }
        if (ipInfo.optionalWrapped()) {
            builder.append(".setIsOptionalWrapped()");
        }
        if (ipInfo.staticDeclaration()) {
            builder.append(".setIsStatic()");
        }
        return builder.toString();
    }

    String toCodegenQualifiers(
            Collection<QualifierAndValue> qualifiers) {
        StringBuilder builder = new StringBuilder();
        for (QualifierAndValue qualifier : qualifiers) {
            builder.append(".qualifier(").append(toCodegenQualifiers(qualifier)).append(")");
        }
        return builder.toString();
    }

    String toCodegenQualifiers(
            QualifierAndValue qualifier) {
        String val = toCodegenQuotedString(qualifier.value().orElse(null));
        String result = DefaultQualifierAndValue.class.getName() + ".create("
                + qualifier.qualifierTypeName() + ".class";
        if (val != null) {
            result += ", " + val;
        }
        result += ")";
        return result;
    }

    String toCodegenQuotedString(
            String value) {
        return (value == null) ? null : "\"" + value + "\"";
    }

    String toCodegenDecl(
            ServiceInfoCriteria dependencyTo,
            InjectionPointInfo injectionPointInfo) {
        String contract = first(dependencyTo.contractsImplemented(), true);
        StringBuilder builder = new StringBuilder();
        if (injectionPointInfo.optionalWrapped()) {
            builder.append("Optional<").append(contract).append(">");
        } else {
            if (injectionPointInfo.listWrapped()) {
                builder.append("List<");
            }
            if (injectionPointInfo.providerWrapped()) {
                builder.append("Provider<");
            }
            builder.append(contract);
            if (injectionPointInfo.providerWrapped()) {
                builder.append(">");
            }
            if (injectionPointInfo.listWrapped()) {
                builder.append(">");
            }
        }
        PicoSupported.isSupportedInjectionPoint(logger(),
                                                createFromTypeName(injectionPointInfo.serviceTypeName()),
                                                injectionPointInfo,
                                                InjectionPointInfo.Access.PRIVATE == injectionPointInfo.access(),
                                                injectionPointInfo.staticDeclaration());
        return builder.toString();
    }

    String toCodegenCtorArgList(
            DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> nameRef = new AtomicReference<>();
        List<String> args = new LinkedList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies().stream()
                                       .filter(dep2 -> DefaultInjectionPointInfo.CONSTRUCTOR.equals(dep2.elementName()))
                                       .forEach(dep2 -> {
                                           if ((nameRef.get() == null)) {
                                               nameRef.set(dep2.baseIdentity());
                                           } else {
                                               assert (nameRef.get().equals(dep2.baseIdentity())) : "only 1 ctor can be injectable";
                                           }
                                           args.add("c" + count.incrementAndGet());
                                       })
        );

        return (args.isEmpty()) ? null : CommonUtils.toString(args);
    }

    List<String> toCodegenInjectCtorArgs(
            DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> nameRef = new AtomicReference<>();
        List<String> args = new LinkedList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies().stream()
                        .filter(dep2 -> DefaultInjectionPointInfo.CONSTRUCTOR.equals(dep2.elementName()))
                        .forEach(dep2 -> {
                            if (Objects.isNull(nameRef.get())) {
                                nameRef.set(dep2.baseIdentity());
                            } else {
                                assert (nameRef.get().equals(dep2.baseIdentity())) : "only 1 ctor can be injectable";
                            }
                            String cn = toCodegenDecl(dep1.dependencyTo(), dep2);
                            String argName = "c" + count.incrementAndGet();
                            String id = dep2.baseIdentity() + "(" + count.get() + ")";
                            String argBuilder = cn + " "
                                    + argName + " = (" + cn + ") "
                                    + "get(deps, \"" + id + "\");";
                            args.add(argBuilder);
                        }));
        return args;
    }

    List<Object> toCodegenInjectFields(
            DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        List<Object> fields = new LinkedList<>();
        dependencies.allDependencies()
                .forEach(dep1 -> dep1.injectionPointDependencies().stream()
                        .filter(dep2 -> InjectionPointInfo.ElementKind.FIELD
                                .equals(dep2.elementKind()))
                        .forEach(dep2 -> {
                            String cn = toCodegenDecl(dep1.dependencyTo(), dep2);
                            IdAndToString setter;
                            String id = dep2.id();
                            if (Void.class.getName().equals(cn)) {
                                setter = new IdAndToString(id, dep2.elementName());
                            } else {
                                setter = new IdAndToString(id, dep2.elementName()
                                        + " = (" + cn + ") get(deps, \""
                                        + dep2.baseIdentity() + "\")");
                            }
                            fields.add(setter);
                        }));
        return fields;
    }

    List<Object> toCodegenInjectMethods(
            TypeName serviceTypeName,
            DependenciesInfo dependencies) {
        if (dependencies == null) {
            return null;
        }

        List<Object> methods = new LinkedList<>();
        String lastElemName = null;
        String lastId = null;
        List<String> compositeSetter = null;
        for (DependencyInfo dep1 : dependencies.allDependencies()) {
            for (InjectionPointInfo ipInfo : dep1.injectionPointDependencies()) {
                if (ipInfo.elementKind() != InjectionPointInfo.ElementKind.METHOD) {
                    continue;
                }

                String id = toBaseIdTagName(ipInfo, serviceTypeName);
                String elemName = ipInfo.elementName();
                Integer elemPos = ipInfo.elementOffset().orElse(null);
                int elemArgs = ipInfo.elementArgs().orElse(0);
                String cn = toCodegenDecl(dep1.dependencyTo(), ipInfo);

                if (Objects.nonNull(lastId) && !lastId.equals(id) && Objects.nonNull(compositeSetter)) {
                    IdAndToString setter = new IdAndToString(lastId, lastElemName + "("
                            + CommonUtils.toString(compositeSetter, null, ",\n\t\t\t\t")
                            + ")");
                    methods.add(setter);
                    compositeSetter = null;
                }

                if (0 == elemArgs) {
                    assert (Void.class.getName().equals(cn));
                    IdAndToString setter = new IdAndToString(id, elemName + "()");
                    methods.add(setter);
                } else if (1 == elemArgs) {
                    assert (elemArgs == elemPos);
                    IdAndToString setter = new IdAndToString(id,
                                       elemName + "((" + cn + ") get(deps, \"" + id + "(1)\"))");
                    methods.add(setter);
                } else {
                    assert (elemArgs > 1);
                    if (Objects.isNull(compositeSetter)) {
                        compositeSetter = new ArrayList<>();
                    }
                    compositeSetter.add("(" + cn + ") get(deps, \"" + id + "(" + elemPos + ")\")");
                }

                lastId = id;
                lastElemName = elemName;
            }
        }

        if (compositeSetter != null) {
            IdAndToString setter = new IdAndToString(lastId, lastElemName + "("
                    + CommonUtils.toString(compositeSetter, null, ",\n\t\t\t\t")
                    + ")");
            methods.add(setter);
        }

        return methods;
    }

    Collection<Object> toCodegenInjectMethodsSkippedInParent(
            boolean isSupportsJsr330InStrictMode,
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen,
            LazyValue<ScanResult> scan) {
        List<TypeName> hierarchy = codeGen.serviceTypeHierarchy().get(serviceTypeName);
        TypeName parent = parentOf(serviceTypeName, codeGen);
        if (hierarchy == null && parent != null) {
            hierarchy = List.of(parent);
        }
        if (hierarchy == null) {
            return null;
        }

        DependenciesInfo deps = codeGen.serviceTypeInjectionPointDependencies().get(serviceTypeName);

        Set<Object> result = new LinkedHashSet<>();
        hierarchy.stream().filter((typeName) -> !serviceTypeName.equals(typeName))
                .forEach(parentTypeName -> {
                    DependenciesInfo parentDeps = codeGen.serviceTypeInjectionPointDependencies().get(parentTypeName);
                    List<Object> skipList = toCodegenInjectMethodsSkippedInParent(isSupportsJsr330InStrictMode,
                                                                                  serviceTypeName,
                                                                                  deps,
                                                                                  parentTypeName,
                                                                                  parentDeps,
                                                                                  scan);
                    if (skipList != null) {
                        result.addAll(skipList);
                    }
                });

        return (result.isEmpty()) ? null : result;
    }

    /**
     * Called in strict Jsr330 compliance mode. If Inject anno is on parent method but not on child method
     * then we should hide the inject in the parent. Crazy that inject was not inherited if you ask me!
     *
     * @param isSupportsJsr330InStrictMode are we in jsr-330 strict mode
     * @param serviceTypeName   the activator service type name
     * @param dependencies      the dependencies for this service type
     * @param parentTypeName    the parent type
     * @param parentDependencies the parent dependencies
     * @param scan              the provider of class introspection
     * @return the list of injection point identifiers that should be skipped in the parent delegation call
     */
    List<Object> toCodegenInjectMethodsSkippedInParent(
            boolean isSupportsJsr330InStrictMode,
            TypeName serviceTypeName,
            DependenciesInfo dependencies,
            TypeName parentTypeName,
            DependenciesInfo parentDependencies,
            LazyValue<ScanResult> scan) {
        if (!isSupportsJsr330InStrictMode || parentTypeName == null) {
            return null;
        }

        ClassInfo classInfo = toClassInfo(serviceTypeName, scan);
        ClassInfo parentClassInfo = toClassInfo(parentTypeName, scan);
        MethodInfoList parentMethods = parentClassInfo.getDeclaredMethodInfo();
        Map<IdAndToString, MethodInfo> injectedParentMethods = parentMethods.stream()
                .filter((m) -> Objects.nonNull(m.getAnnotationInfo(Inject.class.getName())))
                .filter((m) -> DefaultExternalModuleCreator.isPicoSupported(parentTypeName, m, logger()))
                .collect(Collectors.toMap(DefaultActivatorCreator::toBaseIdTag, Function.identity()));
        if (injectedParentMethods.isEmpty()) {
            return null;
        }

        MethodInfoList methods = classInfo.getDeclaredMethodInfo();
        Map<IdAndToString, MethodInfo> allSupportedMethodsOnServiceType = methods.stream()
                .filter((m) -> DefaultExternalModuleCreator.isPicoSupported(serviceTypeName, m, logger()))
                .collect(Collectors.toMap(DefaultActivatorCreator::toBaseIdTag, Function.identity()));

        List<Object> removeList = null;

        for (Map.Entry<IdAndToString, MethodInfo> e : injectedParentMethods.entrySet()) {
            MethodInfo method = allSupportedMethodsOnServiceType.get(e.getKey());
            if (method != null) {
                AnnotationInfo annotationInfo = method.getAnnotationInfo(Inject.class.getName());
                if (annotationInfo != null) {
                    continue;
                }
                if (removeList == null) {
                    removeList = new LinkedList<>();
                }
                removeList.add(e.getKey());
            }
        }

        return removeList;
    }

    static ClassInfo toClassInfo(
            TypeName serviceTypeName,
            LazyValue<ScanResult> scan) {
        ClassInfo classInfo = scan.get().getClassInfo(serviceTypeName.name());
        if (classInfo == null) {
            throw new ToolsException("unable to introspect: " + serviceTypeName);
        }
        return classInfo;
    }

    static IdAndToString toBaseIdTag(
            MethodInfo m) {
        String packageName = m.getClassInfo().getPackageName();
        boolean isPackagePrivate = isPackagePrivate(m.getModifiers());
        InjectionPointInfo.Access access = (isPackagePrivate)
                ? InjectionPointInfo.Access.PACKAGE_PRIVATE : InjectionPointInfo.Access.PUBLIC;
        String idTag = toBaseIdTagName(m.getName(), m.getParameterInfo().length, access, packageName);
        return new IdAndToString(idTag, m);
    }

    static String toBaseIdTagName(
            InjectionPointInfo ipInfo,
            TypeName serviceTypeName) {
        String packageName = serviceTypeName.packageName();
        return toBaseIdTagName(ipInfo.elementName(), ipInfo.elementArgs().orElse(0), ipInfo.access(), packageName);
    }

    static String toBaseIdTagName(
            String methodName,
            int methodArgCount,
            InjectionPointInfo.Access access,
            String packageName) {
        return Dependencies.toMethodBaseIdentity(methodName, methodArgCount, access, () -> packageName);
    }

    Double toWeightedPriority(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        Double weight = codeGen.serviceTypeWeights().get(serviceTypeName);
        if (weight == null && hasParent(serviceTypeName, codeGen)) {
            // we might be a child of another service, in which case we will need to override its value
            weight = Weighted.DEFAULT_WEIGHT;
        }
        return weight;
    }

    Integer toRunLevel(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        Integer runLevel = codeGen.serviceTypeRunLevels().get(serviceTypeName);
        if (runLevel == null && hasParent(serviceTypeName, codeGen)) {
            // we might be a child of another service, in which case we will need to override its value
            runLevel = RunLevel.NORMAL;
        }
        return runLevel;
    }

    boolean hasParent(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return Objects.nonNull(parentOf(serviceTypeName, codeGen));
    }

    TypeName parentOf(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeToParentServiceTypes().get(serviceTypeName);
    }

    List<String> toDescription(
            TypeName serviceTypeName) {
        return List.of("Activator for {@link " + serviceTypeName + "}.");
    }

    TypeName toActivatorTypeName(
            TypeName serviceTypeName) {
        return serviceTypeName;
    }

    TypeName toParentTypeName(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeToParentServiceTypes().get(serviceTypeName);
    }

    String toActivatorGenericDecl(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeToActivatorGenericDecl().get(serviceTypeName);
    }

    Set<String> toScopeTypeNames(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypeScopeNames().get(serviceTypeName);
    }

    /**
     * One might expect that isProvider should only be set to true if the service type implements Provider<>. However,
     * that alone would fail JSR-330 testing. The interpretation there is any service without a scope is inferred to be
     * non-singleton, provided/dependent scope.
     */
    boolean toIsProvider(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        Set<String> scopeTypeName = toScopeTypeNames(serviceTypeName, codeGen);
        if ((scopeTypeName == null || scopeTypeName.isEmpty()) && toIsConcrete(serviceTypeName, codeGen)) {
            return true;
        }

        Set<TypeName> providerFor = codeGen.serviceTypeToProviderForTypes().get(serviceTypeName);
        return (providerFor != null) && !providerFor.isEmpty();
    }

    boolean toIsConcrete(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        Boolean isAbstract = codeGen.serviceTypeIsAbstractTypes().get(serviceTypeName);
        return (isAbstract == null) || !isAbstract;
    }

    /**
     * Creates service info from the service type name and the activator create codegen request.
     *
     * @param serviceTypeName the service type name
     * @param codeGen         the code gen request
     * @return the service info
     */
    public static ServiceInfoBasics toServiceInfo(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        Set<TypeName> contracts = codeGen.serviceTypeContracts().get(serviceTypeName);
        Set<TypeName> externalContracts = codeGen.serviceTypeExternalContracts().get(serviceTypeName);
        Set<QualifierAndValue> qualifiers = codeGen.serviceTypeQualifiers().get(serviceTypeName);
        return DefaultServiceInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .contractsImplemented(toSet(contracts, TypeName::name))
                .externalContractsImplemented(toSet(externalContracts, TypeName::name))
                .qualifiers((qualifiers == null) ? Set.of() : qualifiers)
                .build();
    }

    DependenciesInfo toDependencies(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        if (serviceTypeName == null) {
            return null;
        }

        return codeGen.serviceTypeInjectionPointDependencies().get(serviceTypeName);
    }

    String toPostConstructMethodName(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypePostConstructMethodNames().get(serviceTypeName);
    }

    String toPreDestroyMethodName(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        return codeGen.serviceTypePreDestroyMethodNames().get(serviceTypeName);
    }

    @SuppressWarnings("unchecked")
    List<TypeName> toServiceTypeHierarchy(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen,
            LazyValue<ScanResult> scan) {
        Map<TypeName, List<TypeName>> map = codeGen.serviceTypeHierarchy();
        List<TypeName> order = (Objects.nonNull(map)) ? map.get(serviceTypeName) : null;
        if (Objects.nonNull(order)) {
            return (1 == order.size()) ? null : order;
        }

        return serviceTypeHierarchy(serviceTypeName, scan);
    }

    List<String> getExtraCodeGen(
            TypeName serviceTypeName,
            ActivatorCreatorCodeGen codeGen) {
        Map<TypeName, List<String>> map = codeGen.extraCodeGen();
        List<String> extraCodeGen = (Objects.nonNull(map)) ? map.get(serviceTypeName) : null;
        return Objects.isNull(extraCodeGen) ? Collections.emptyList() : extraCodeGen;
    }

    static List<TypeName> serviceTypeHierarchy(
            TypeName serviceTypeName,
            LazyValue<ScanResult> scan) {
        List<TypeName> order = new LinkedList<>();
        ClassInfo classInfo = toClassInfo(serviceTypeName, scan);
        while (Objects.nonNull(classInfo)) {
            order.add(0, createTypeNameFromClassInfo(classInfo));
            classInfo = classInfo.getSuperclass();
        }
        return (1 == order.size()) ? null : order;
    }

    ActivatorCreatorResponse handleError(
            ActivatorCreatorRequest request,
            ToolsException e,
            DefaultActivatorCreatorResponse.Builder builder) {
        if (request.throwIfError()) {
            throw e;
        }

        return builder
                .error(e)
                .success(false)
                .build();
    }

}
