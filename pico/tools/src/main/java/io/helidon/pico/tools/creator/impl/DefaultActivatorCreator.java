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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.Weighted;
import io.helidon.pico.RunLevel;
import io.helidon.pico.Activator;
import io.helidon.pico.Application;
import io.helidon.pico.DeActivator;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.Module;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.ext.Dependency;
import io.helidon.pico.spi.impl.DefaultInjectionPointInfo;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ActivatorCodeGenDetail;
import io.helidon.pico.tools.creator.ActivatorCreator;
import io.helidon.pico.tools.creator.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.creator.ActivatorCreatorRequest;
import io.helidon.pico.tools.creator.ActivatorCreatorResponse;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.GeneralCreatorRequest;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.creator.ModuleDetail;
import io.helidon.pico.tools.processor.PicoSupported;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.TemplateHelper;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;
import lombok.Getter;

import static io.helidon.pico.tools.processor.TypeTools.componentTypeNameOf;
import static io.helidon.pico.tools.processor.TypeTools.createTypeNameFromClassInfo;
import static io.helidon.pico.tools.processor.TypeTools.isPackagePrivate;

/**
 * Responsible for building all pico-di related collateral for a module, including:
 * <li>The {@link ServiceProvider} for each service type implementation passed in.
 * <li>The {@link Activator} and {@link DeActivator} for each service type implementation passed in.
 * <li>The {@link Module} for the aggregate service provider bindings for the same set of service type names.
 * <li>The module-info as appropriate for the above set of services (and contracts).
 * <li>The /META-INF/services entries as appropriate.
 *
 * This API can also be used to only produce meta-information describing the model without the codegen option - see
 * {@link io.helidon.pico.tools.creator.ActivatorCreatorRequest#getCodeGenPaths()} for details.
 */
@Singleton
public class DefaultActivatorCreator extends AbstractCreator implements ActivatorCreator, Weighted {

    /**
     * The suffix name for the service type activator class.
     */
    public static final String INNER_ACTIVATOR_CLASS_NAME = "$$" + NAME + "Activator";
    private static final String SERVICE_PROVIDER_ACTIVATOR_HBS = "service-provider-activator.hbs";
    private static final String SERVICE_PROVIDER_APPLICATION_STUB_HBS = "service-provider-application-stub.hbs";
    private static final String SERVICE_PROVIDER_MODULE_HBS = "service-provider-module.hbs";

    private CodeGenFiler filer;
    private final String templateName;

    /**
     * Ctor.
     */
    public DefaultActivatorCreator() {
        this.templateName = TemplateHelper.DEFAULT_TEMPLATE_NAME;
    }

    /**
     * Ctor.
     *
     * @param filer the filer to used to support codegen
     */
    @Inject
    public DefaultActivatorCreator(CodeGenFiler filer) {
        this();
        this.filer = Objects.requireNonNull(filer);
    }

    public String getTemplateName() {
        return templateName;
    }

    /**
     * Sets the codegen filer for this creator.
     *
     * @param codeGen the codegen filer
     */
    public void setCodeGenFiler(CodeGenFiler codeGen) {
        assert (Objects.isNull(filer));
        this.filer = codeGen;
    }

    protected CodeGenFiler getFiler() {
        return filer;
    }

    @Override
    public ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest req) throws ToolsException {
        String templateName = (Objects.nonNull(req.getTemplateName())) ? req.getTemplateName() : getTemplateName();

        DefaultActivatorCreatorResponse.DefaultActivatorCreatorResponseBuilder builder =
                DefaultActivatorCreatorResponse.builder()
                .configOptions(req.getConfigOptions())
                .templateName(templateName);

        if (Objects.isNull(req.getServiceTypeNames())) {
            return handleError(req, new ToolsException("ServiceTypeNames is required to be passed"), builder);
        }

        if (Objects.isNull(getFiler())) {
            return handleError(req, new ToolsException("an annotation processor env is required"), builder);
        }

        if (Objects.isNull(req.getConfigOptions())) {
            return handleError(req, new ToolsException("ConfigOptions are required"), builder);
        }

        if (Objects.isNull(req.getCodeGenRequest())) {
            return handleError(req, new ToolsException("CodeGenPaths are required"), builder);
        }

        try {
            LazyValue<ScanResult> scan = LazyValue.create(ReflectionHandler.INSTANCE::getScan);
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
    protected ActivatorCreatorResponse codegen(ActivatorCreatorRequest req,
                                               DefaultActivatorCreatorResponse.DefaultActivatorCreatorResponseBuilder builder,
                                               LazyValue<ScanResult> scan) {
        final boolean isApplicationPreCreated = req.getConfigOptions().isApplicationPreCreated();
        final boolean isModuleCreated = req.getConfigOptions().isModuleCreated();
        final Map<TypeName, Boolean> serviceTypeToIsAbstractType = req.getCodeGenRequest().getServiceTypeIsAbstractTypes();
        final List<TypeName> activatorTypeNames = new LinkedList<>();
        final List<TypeName> activatorTypeNamesPutInModule = new LinkedList<>();
        final Map<TypeName, ActivatorCodeGenDetail> activatorDetails = new LinkedHashMap<>();
        final CodeGenPaths codeGenPaths = toPaths(req);
        for (TypeName serviceTypeName : req.getServiceTypeNames()) {
            DefaultActivatorCodeGenDetail activatorDetail = codegenActivator(req, serviceTypeName, scan);
            Object prev = activatorDetails.put(serviceTypeName, activatorDetail);
            assert (Objects.isNull(prev));
            codegenActivatorFilerOut(req, activatorDetail);
            TypeName activatorTypeName = toActivatorImplTypeName(serviceTypeName);
            activatorTypeNames.add(activatorTypeName);
            Boolean isAbstract = serviceTypeToIsAbstractType.get(serviceTypeName);
            isAbstract = Objects.nonNull(isAbstract) && isAbstract;
            if (!isAbstract) {
                activatorTypeNamesPutInModule.add(activatorTypeName);
            }

            InterceptionPlan interceptionPlan = req.getCodeGenRequest().getServiceTypeInterceptionPlan().get(serviceTypeName);
            if (Objects.nonNull(interceptionPlan)) {
                codegenInterceptorFilerOut(builder, interceptionPlan);
            }
        }
        builder.serviceTypeNames(activatorTypeNames)
                .serviceTypeDetails(activatorDetails);

        DefaultModuleDetail moduleDetail;
        TypeName applicationTypeName;
        Map<String, List<String>> metaInfServices;
        TypeName moduleTypeName = toModuleTypeName(req, activatorTypeNames);
        if (Objects.nonNull(moduleTypeName)) {
            String className = DefaultApplicationCreator
                    .toApplicationClassName(req.getCodeGenRequest().getClassPrefixName());
            applicationTypeName = DefaultTypeName.create(moduleTypeName.packageName(), className);
            builder.applicationTypeName(applicationTypeName);
            String applicationStub = toApplicationStubBody(req, applicationTypeName, req.getModuleName());
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
            if (Objects.nonNull(moduleDetail) && isModuleCreated) {
                codegenModuleFilerOut(req, moduleDetail);
                File out = codegenModuleInfoFilerOut(req, moduleDetail.getDescriptor());
                getLogger().log(System.Logger.Level.DEBUG, "codegen module-info written to: " + out);
            }

            metaInfServices = toMetaInfServices(moduleDetail,
                                                applicationTypeName,
                                                isApplicationPreCreated,
                                                isModuleCreated);
            builder.metaInfServices(metaInfServices);
            if (Objects.nonNull(metaInfServices) && !metaInfServices.isEmpty() && req.getConfigOptions()
                    .isModuleCreated()) {
                codegenMetaInfServices(req, codeGenPaths, metaInfServices);
            }
        }

        return builder.build();
    }

    protected CodeGenPaths toPaths(ActivatorCreatorRequest req) {
        if (Objects.nonNull(req.getCodeGenPaths())) {
            return req.getCodeGenPaths();
        }

        return DefaultGeneralCodeGenPaths.builder().build();
    }

    private DefaultModuleDetail toModuleDetail(ActivatorCreatorRequest req,
                                               List<TypeName> activatorTypeNamesPutInModule,
                                               TypeName moduleTypeName,
                                               TypeName applicationTypeName,
                                               boolean isApplicationCreated,
                                               boolean isModuleCreated) {
        String className = moduleTypeName.className();
        String packageName = moduleTypeName.packageName();
        String moduleName = req.getModuleName();
        String generator = req.getGenerator();

        ActivatorCreatorCodeGen codeGen = req.getCodeGenRequest();
        String typePrefix = codeGen.getClassPrefixName();
        Collection<String> modulesRequired = codeGen.getModulesRequired();
        Map<TypeName, Set<TypeName>> serviceTypeContracts = codeGen.getServiceTypeContracts();
        Map<TypeName, Set<TypeName>> externalContracts = codeGen.getServiceTypeExternalContracts();

        String moduleInfoPath = req.getCodeGenPaths().getModuleInfoPath();
        PicoModuleBuilderRequest moduleBuilderRequest = PicoModuleBuilderRequest.builder()
                .moduleName(moduleName)
                .moduleTypeName(moduleTypeName)
                .applicationTypeName(applicationTypeName)
                .modulesRequired(modulesRequired)
                .serviceTypeContracts(serviceTypeContracts)
                .externalContracts(externalContracts)
                .generator(generator)
                .moduleInfoPath(moduleInfoPath)
                .classPrefixName(typePrefix)
                .isApplicationCreated(isApplicationCreated)
                .isModuleCreated(isModuleCreated)
                .build();
        SimpleModuleDescriptor moduleInfo = createModuleInfo(moduleBuilderRequest);
        moduleName = moduleInfo.getName();
        String moduleBody = toModuleBody(req, packageName, className, moduleName, activatorTypeNamesPutInModule);
        return DefaultModuleDetail.builder()
                .moduleName(moduleName)
                .moduleTypeName(moduleTypeName)
                .serviceProviderActivatorTypeNames(activatorTypeNamesPutInModule)
                .moduleBody(moduleBody)
                .moduleInfoBody(moduleInfo.getContents())
                .descriptor(moduleInfo)
                .build();
    }

    /**
     * Applies to module-info.
     */
    private TypeName toModuleTypeName(ActivatorCreatorRequest req, List<TypeName> activatorTypeNames) {
        String packageName;
        if (AnnotationAndValue.hasNonBlankValue(req.getPackageName())) {
            packageName = req.getPackageName();
        } else {
            if (Objects.isNull(activatorTypeNames) || activatorTypeNames.isEmpty()) {
                return null;
            }
            packageName = activatorTypeNames.get(0).packageName() + "." + NAME;
        }

        String className = toModuleClassName(req.getCodeGenRequest().getClassPrefixName());
        return DefaultTypeName.create(packageName, className);
    }

    protected static String toModuleClassName(String modulePrefix) {
        modulePrefix = Objects.isNull(modulePrefix) ? "" : modulePrefix;
        return NAME + modulePrefix + "Module";
    }

    protected Map<String, List<String>> toMetaInfServices(ModuleDetail moduleDetail,
                                                          TypeName applicationTypeName,
                                                          boolean isApplicationCreated,
                                                          boolean isModuleCreated) {
        Map<String, List<String>> metaInfServices = new LinkedHashMap<>();
        if (isApplicationCreated && Objects.nonNull(applicationTypeName)) {
            metaInfServices.put(Application.class.getName(),
                                Collections.singletonList(applicationTypeName.name()));
        }
        if (isModuleCreated && Objects.nonNull(moduleDetail)) {
            metaInfServices.put(Module.class.getName(),
                                Collections.singletonList(moduleDetail.getModuleTypeName().name()));
        }
        return metaInfServices;
    }

    protected void codegenMetaInfServices(GeneralCreatorRequest req,
                                          CodeGenPaths paths,
                                          Map<String, List<String>> metaInfServices) {
        boolean prev = true;
        if (req.isAnalysisOnly()) {
            prev = CodeGenFiler.setFilerEnabled(false);
        }

        try {
            getFiler().codegenMetaInfServices(paths, metaInfServices);
        } finally {
            if (req.isAnalysisOnly()) {
                CodeGenFiler.setFilerEnabled(prev);
            }
        }
    }

    protected void codegenActivatorFilerOut(GeneralCreatorRequest req,
                                            DefaultActivatorCodeGenDetail activatorDetail) {
        boolean prev = true;
        if (req.isAnalysisOnly()) {
            prev = CodeGenFiler.setFilerEnabled(false);
        }

        try {
            getFiler().codegenActivatorFilerOut(activatorDetail);
        } finally {
            if (req.isAnalysisOnly()) {
                CodeGenFiler.setFilerEnabled(prev);
            }
        }
    }

    protected void codegenModuleFilerOut(GeneralCreatorRequest req,
                                         ModuleDetail moduleDetail) {
        boolean prev = true;
        if (req.isAnalysisOnly()) {
            prev = CodeGenFiler.setFilerEnabled(false);
        }

        try {
            getFiler().codegenModuleFilerOut(moduleDetail);
        } finally {
            if (req.isAnalysisOnly()) {
                CodeGenFiler.setFilerEnabled(prev);
            }
        }
    }

    protected void codegenApplicationFilerOut(GeneralCreatorRequest req,
                                              TypeName applicationTypeName,
                                              String applicationBody) {
        boolean prev = true;
        if (req.isAnalysisOnly()) {
            prev = CodeGenFiler.setFilerEnabled(false);
        }

        try {
            getFiler().codegenApplicationFilerOut(applicationTypeName, applicationBody);
        } finally {
            if (req.isAnalysisOnly()) {
                CodeGenFiler.setFilerEnabled(prev);
            }
        }
    }

    protected File codegenModuleInfoFilerOut(GeneralCreatorRequest req,
                                             SimpleModuleDescriptor descriptor) {
        boolean prev = true;
        if (req.isAnalysisOnly()) {
            prev = CodeGenFiler.setFilerEnabled(false);
        }

        try {
            return getFiler().codegenModuleInfoFilerOut(descriptor, true);
        } finally {
            if (req.isAnalysisOnly()) {
                CodeGenFiler.setFilerEnabled(prev);
            }
        }
    }

    /**
     * Generates just the interceptors.
     *
     * @param interceptionPlans the interceptor plans
     */
    public void codegenInterceptors(Map<TypeName, InterceptionPlan> interceptionPlans) {
        if (Objects.isNull(interceptionPlans)) {
            return;
        }

        for (Map.Entry<TypeName, InterceptionPlan> e : interceptionPlans.entrySet()) {
            try {
                codegenInterceptorFilerOut(null, e.getValue());
            } catch (Throwable t) {
                throw new ToolsException("Failed while processing: " + e.getKey(), t);
            }
        }
    }

    protected File codegenInterceptorFilerOut(DefaultActivatorCreatorResponse.DefaultActivatorCreatorResponseBuilder builder,
                         InterceptionPlan interceptionPlan) {
        TypeName interceptorTypeName = DefaultInterceptorCreator.createInterceptorSourceTypeName(interceptionPlan);
        String body = DefaultInterceptorCreator.createInterceptorSourceBody(interceptionPlan);
        if (Objects.nonNull(builder)) {
            builder.serviceTypeInterceptorPlan(interceptorTypeName, interceptionPlan);
        }
        return getFiler().codegenJavaFilerOut(interceptorTypeName, body);
    }

    protected DefaultActivatorCodeGenDetail codegenActivator(ActivatorCreatorRequest req,
                                                             TypeName serviceTypeName,
                                                             LazyValue<ScanResult> scan) {
        ActivatorCreatorCodeGen codeGen = req.getCodeGenRequest();
        String template = TemplateHelper.safeLoadTemplate(req.getTemplateName(), SERVICE_PROVIDER_ACTIVATOR_HBS);
        ServiceInfo serviceInfo = toServiceInfo(serviceTypeName, codeGen);
        TypeName activatorTypeName = toActivatorTypeName(serviceTypeName);
        TypeName parentTypeName = toParentTypeName(serviceTypeName, codeGen);
        String activatorGenericDecl = toActivatorGenericDecl(serviceTypeName, codeGen);
        Dependencies dependencies = toDependencies(serviceTypeName, codeGen);
        Dependencies parentDependencies = toDependencies(parentTypeName, codeGen);
        Set<String> scopeTypeNames = toScopeTypeNames(serviceTypeName, codeGen);
        String generatedSticker = getGeneratedSticker(req);
        List<String> description = toDescription(serviceTypeName);
        Double weightedPriority = getWeightedPriority(serviceTypeName, codeGen);
        Integer runLevel = getRunLevel(serviceTypeName, codeGen);
        String postConstructMethodName = getPostConstructMethodName(serviceTypeName, codeGen);
        String preDestroyMethodName = getPreDestroyMethodName(serviceTypeName, codeGen);
        List<?> serviceTypeInjectionOrder = getServiceTypeHierarchy(serviceTypeName, codeGen, scan);
        List<String> extraCodeGen = getExtraCodeGen(serviceTypeName, codeGen);
        boolean isProvider = toIsProvider(serviceTypeName, codeGen);
        boolean isConcrete = toIsConcrete(serviceTypeName, codeGen);
        boolean isSupportsJsr330InStrictMode = false;
        if (Objects.nonNull(req.getConfigOptions())) {
            isSupportsJsr330InStrictMode = req.getConfigOptions().isSupportsJsr330InStrictMode();
        }
        Collection<Object> injectionPointsSkippedInParent =
                toCodegenInjectMethodsSkippedInParent(isSupportsJsr330InStrictMode, activatorTypeName, codeGen, scan);

        ActivatorCreatorArgs args = ActivatorCreatorArgs.builder()
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
                .isConcrete(isConcrete)
                .isProvider(isProvider)
                .isSupportsJsr330InStrictMode(isSupportsJsr330InStrictMode)
                .build();
        String activatorBody = toActivatorBody(args);

        return DefaultActivatorCodeGenDetail.builder()
                .serviceInfo(serviceInfo)
                .dependencies(dependencies)
                .serviceTypeName(toActivatorImplTypeName(activatorTypeName))
                .body(activatorBody)
                .build();
    }

    private String toApplicationStubBody(ActivatorCreatorRequest req, TypeName applicationTypeName, String moduleName) {
        String template = TemplateHelper.safeLoadTemplate(req.getTemplateName(), SERVICE_PROVIDER_APPLICATION_STUB_HBS);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", applicationTypeName.className());
        subst.put("packagename", applicationTypeName.packageName());
        subst.put("description", null);
        subst.put("generatedanno", getGeneratedSticker(req));
        subst.put("modulename", moduleName);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        return TemplateHelper.applySubstitutions(ps, template, subst).trim();
    }

    protected String toModuleBody(ActivatorCreatorRequest req,
                                  String packageName,
                                  String className,
                                  String moduleName,
                                  List<TypeName> activatorTypeNames) {
        String template = TemplateHelper.safeLoadTemplate(req.getTemplateName(), SERVICE_PROVIDER_MODULE_HBS);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", className);
        subst.put("packagename", packageName);
        subst.put("description", null);
        subst.put("generatedanno", getGeneratedSticker(req));
        subst.put("modulename", moduleName);
        subst.put("activators", activatorTypeNames);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        return TemplateHelper.applySubstitutions(ps, template, subst).trim();
    }

    @Override
    public TypeName toActivatorImplTypeName(TypeName serviceTypeName) {
        return DefaultTypeName.create(serviceTypeName.packageName(),
                                      CommonUtils.toFlatName(serviceTypeName.className())
                                              + INNER_ACTIVATOR_CLASS_NAME);
    }

    private String toActivatorBody(ActivatorCreatorArgs args) {
        Map<String, Object> subst = new HashMap<>();
        subst.put("activatorsuffix", INNER_ACTIVATOR_CLASS_NAME);
        subst.put("classname", args.getActivatorTypeName().className());
        subst.put("flatclassname", CommonUtils.toFlatName(args.getActivatorTypeName().className()));
        subst.put("packagename", args.getActivatorTypeName().packageName());
        subst.put("activatorgenericdecl", args.getActivatorGenericDecl());
        subst.put("parent", toCodenParent(args.isSupportsJsr330InStrictMode,
                                          args.getActivatorTypeName(), args.getParentTypeName()));
        subst.put("scopetypenames", args.getScopeTypeNames());
        subst.put("description", args.getDescription());
        subst.put("generatedanno", args.getGeneratedSticker());
        subst.put("isprovider", args.isProvider());
        subst.put("isconcrete", args.isConcrete());
        subst.put("contracts", args.getServiceInfo().contractsImplemented());
        if (args.getServiceInfo() instanceof DefaultServiceInfo) {
            subst.put("externalcontracts", args.getServiceInfo().externalContractsImplemented());
        }
        subst.put("qualifiers", toCodegenQualifiers(args.getServiceInfo().qualifiers()));
        subst.put("dependencies", toCodegenDependencies(args.getDependencies()));
        subst.put("weight", args.getWeightedPriority());
        subst.put("isweightset", Objects.nonNull(args.getWeightedPriority()));
        subst.put("runlevel", args.getRunLevel());
        subst.put("isrunlevelset", Objects.nonNull(args.getRunLevel()));
        subst.put("postconstruct", args.getPostConstructMethodName());
        subst.put("predestroy", args.getPreDestroyMethodName());
        subst.put("ctorarglist", toCodegenCtorArgList(args.getDependencies()));
        subst.put("ctorargs", toCodegenInjectCtorArgs(args.getDependencies()));
        subst.put("injectedfields", toCodegenInjectFields(args.getDependencies()));
        subst.put("injectedmethods", toCodegenInjectMethods(args.getActivatorTypeName(), args.getDependencies()));
        subst.put("injectedmethodsskippedinparent", args.getInjectionPointsSkippedInParent());
        subst.put("extracodegen", args.getExtraCodeGen());
        subst.put("injectionorder", args.getServiceTypeInjectionOrder());
        subst.put("issupportsjsr330instrictmode", args.isSupportsJsr330InStrictMode());

        getLogger().log(System.Logger.Level.DEBUG, "dependencies for "
                + args.getServiceTypeName() + " == " + args.getDependencies());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        return TemplateHelper.applySubstitutions(ps, args.getTemplate(), subst).trim();
    }

    protected String toCodenParent(boolean ignoredIsSupportsJsr330InStrictMode,
                                   TypeName activatorTypeName,
                                   TypeName parentTypeName) {
        String result;
        if (Objects.isNull(parentTypeName) || Object.class.getName().equals(parentTypeName.name())) {
//            getFiler().getMessager().log(activatorTypeName + " path is: 1: " + ServicesToProcess.getServicesInstance().getServiceTypeToParentServiceTypes());
            result = AbstractServiceProvider.class.getName() + "<" + activatorTypeName.className() + ">";
        } else if (Objects.isNull(parentTypeName.typeArguments()) || parentTypeName.typeArguments().isEmpty()) {
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

    protected List<String> toCodegenDependencies(Dependencies dependencies) {
        if (Objects.isNull(dependencies)) {
            return null;
        }

        List<String> result = new ArrayList<>();
        dependencies.getDependencies()
                .forEach(dep1 -> dep1.getIpDependencies()
                        .forEach(dep2 -> result.add(toCodegenDependency(dep1.dependencyTo(), dep2))));

        return result;
    }

    protected String toCodegenDependency(ServiceInfo dependencyTo, DefaultInjectionPointInfo ipInfo) {
        StringBuilder builder = new StringBuilder();
        //.add("world", World.class, InjectionPointInfo.ElementKind.FIELD, InjectionPointInfo.Access.PACKAGE_PRIVATE)
        String elemName = Objects.requireNonNull(ipInfo.elementName());
        if (ipInfo.elementKind() == InjectionPointInfo.ElementKind.CTOR && elemName.equals(InjectionPointInfo.CTOR)) {
            elemName = "CTOR";
        } else {
            elemName = "\"" + elemName + "\"";
        }
        builder.append(".add(").append(elemName).append(", ");
        builder.append(Objects.requireNonNull(componentTypeNameOf(CommonUtils.first(dependencyTo.contractsImplemented()))))
                .append(".class, ");
        builder.append("ElementKind.").append(Objects.requireNonNull(ipInfo.elementKind())).append(", ");
        if (InjectionPointInfo.ElementKind.FIELD != ipInfo.elementKind()) {
                builder.append(ipInfo.getElementArgs()).append(", ");
        }
        builder.append("Access.").append(Objects.requireNonNull(ipInfo.access())).append(")");
        Integer elemPos = ipInfo.elementOffset();
        Set<QualifierAndValue> qualifiers = ipInfo.getQualifiers();
        if (Objects.nonNull(elemPos)) {
            builder.append(".elemOffset(").append(elemPos).append(")");
        }
        if (Objects.nonNull(qualifiers)) {
            builder.append(toCodegenQualifiers(qualifiers));
        }
        if (ipInfo.isListWrapped()) {
            builder.append(".setIsListWrapped()");
        }
        if (ipInfo.isProviderWrapped()) {
            builder.append(".setIsProviderWrapped()");
        }
        if (ipInfo.isOptionalWrapped()) {
            builder.append(".setIsOptionalWrapped()");
        }
        if (ipInfo.isStaticDecl()) {
            builder.append(".setIsStatic()");
        }
        return builder.toString();
    }

    protected String toCodegenQualifiers(Collection<QualifierAndValue> qualifiers) {
        StringBuilder builder = new StringBuilder();
        for (QualifierAndValue qualifier : qualifiers) {
            builder.append(".qualifier(").append(toCodegenQualifiers(qualifier)).append(")");
        }
        return builder.toString();
    }

    protected String toCodegenQualifiers(QualifierAndValue qualifier) {
        String val = toCodegenQuotedString(qualifier.value().orElse(null));
        String result = DefaultQualifierAndValue.class.getName() + ".create("
                + qualifier.qualifierTypeName() + ".class";
        if (Objects.nonNull(val)) {
            result += ", " + val;
        }
        result += ")";
        return result;
    }

    protected String toCodegenQuotedString(String value) {
        return Objects.isNull(value) ? null : "\"" + value + "\"";
    }

    protected String toCodegenDecl(ServiceInfo dependencyTo, DefaultInjectionPointInfo injectionPointInfo) {
        String contract = CommonUtils.first(dependencyTo.contractsImplemented());
        StringBuilder builder = new StringBuilder();
        if (injectionPointInfo.isOptionalWrapped()) {
            builder.append("Optional<").append(contract).append(">");
        } else {
            if (injectionPointInfo.isListWrapped()) {
                builder.append("List<");
            }
            if (injectionPointInfo.isProviderWrapped()) {
                builder.append("Provider<");
            }
            builder.append(contract);
            if (injectionPointInfo.isProviderWrapped()) {
                builder.append(">");
            }
            if (injectionPointInfo.isListWrapped()) {
                builder.append(">");
            }
        }
        PicoSupported.isSupportedInjectionPoint(true, getLogger(),
                                                DefaultTypeName.createFromTypeName(injectionPointInfo.serviceTypeName()),
                                                injectionPointInfo,
                                                InjectionPointInfo.Access.PRIVATE == injectionPointInfo.access(),
                                                injectionPointInfo.isStaticDecl());
        return builder.toString();
    }

    protected String toCodegenCtorArgList(Dependencies dependencies) {
        if (Objects.isNull(dependencies)) {
            return null;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> nameRef = new AtomicReference<>();
        List<String> args = new LinkedList<>();
        dependencies.getDependencies()
                .forEach(dep1 -> dep1.getIpDependencies().stream()
                                       .filter(dep2 -> DefaultInjectionPointInfo.CTOR.equals(dep2.elementName()))
                                       .forEach(dep2 -> {
                                           if (Objects.isNull(nameRef.get())) {
                                               nameRef.set(dep2.baseIdentity());
                                           } else {
                                               assert (nameRef.get().equals(dep2.baseIdentity()))
                                                       : "only 1 ctor can be injectable";
                                           }
                                           args.add("c" + count.incrementAndGet());
                                       })
        );

        return (args.isEmpty()) ? null : CommonUtils.toString(args);
    }

    protected List<String> toCodegenInjectCtorArgs(Dependencies dependencies) {
        if (Objects.isNull(dependencies)) {
            return null;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicReference<String> nameRef = new AtomicReference<>();
        List<String> args = new LinkedList<>();
        dependencies.getDependencies()
                .forEach(dep1 -> dep1.getIpDependencies().stream()
                        .filter(dep2 -> DefaultInjectionPointInfo.CTOR.equals(dep2.elementName()))
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

    protected List<Object> toCodegenInjectFields(Dependencies dependencies) {
        if (Objects.isNull(dependencies)) {
            return null;
        }

        List<Object> fields = new LinkedList<>();
        dependencies.getDependencies()
                .forEach(dep1 -> dep1.getIpDependencies().stream()
                        .filter(dep2 -> InjectionPointInfo.ElementKind.FIELD
                                .equals(dep2.elementKind()))
                        .forEach(dep2 -> {
                            String cn = toCodegenDecl(dep1.dependencyTo(), dep2);
                            IdAndToString setter;
                            String id = dep2.identity();
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

    protected List<Object> toCodegenInjectMethods(TypeName serviceTypeName, Dependencies dependencies) {
        if (Objects.isNull(dependencies)) {
            return null;
        }

        List<Object> methods = new LinkedList<>();
        String lastElemName = null;
        String lastId = null;
        List<String> compositeSetter = null;
        for (Dependency<Object> dep1 : dependencies.getDependencies()) {
            for (DefaultInjectionPointInfo ipInfo : dep1.getIpDependencies()) {
                if (ipInfo.elementKind() != InjectionPointInfo.ElementKind.METHOD) {
                    continue;
                }

                String id = toBaseIdTagName(ipInfo, serviceTypeName);
                String elemName = ipInfo.elementName();
                Integer elemPos = ipInfo.elementOffset();
                int elemArgs = ipInfo.getElementArgs();
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

        if (Objects.nonNull(compositeSetter)) {
            IdAndToString setter = new IdAndToString(lastId, lastElemName + "("
                    + CommonUtils.toString(compositeSetter, null, ",\n\t\t\t\t")
                    + ")");
            methods.add(setter);
        }

        return methods;
    }

    protected Collection<Object> toCodegenInjectMethodsSkippedInParent(boolean isSupportsJsr330InStrictMode,
                                                                 TypeName serviceTypeName,
                                                                 ActivatorCreatorCodeGen codeGen,
                                                                 LazyValue<ScanResult> scan) {
        List<TypeName> hierarchy = codeGen.getServiceTypeHierarchy().get(serviceTypeName);
        TypeName parent = parentOf(serviceTypeName, codeGen);
        if (Objects.isNull(hierarchy) && Objects.nonNull(parent)) {
            hierarchy = Collections.singletonList(parent);
        }
        if (Objects.isNull(hierarchy)) {
            return null;
        }

        Dependencies deps = codeGen.getServiceTypeInjectionPointDependencies().get(serviceTypeName);

        Set<Object> result = new LinkedHashSet<>();
        hierarchy.stream().filter((typeName) -> !serviceTypeName.equals(typeName))
                .forEach(parentTypeName -> {
                    Dependencies parentDeps = codeGen.getServiceTypeInjectionPointDependencies().get(parentTypeName);
                    List<Object> skipList = toCodegenInjectMethodsSkippedInParent(isSupportsJsr330InStrictMode,
                                                                                  serviceTypeName,
                                                                                  deps,
                                                                                  parentTypeName,
                                                                                  parentDeps,
                                                                                  scan);
                    if (Objects.nonNull(skipList)) {
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
    protected List<Object> toCodegenInjectMethodsSkippedInParent(boolean isSupportsJsr330InStrictMode,
                                                            TypeName serviceTypeName,
                                                            Dependencies dependencies,
                                                            TypeName parentTypeName,
                                                            Dependencies parentDependencies,
                                                            LazyValue<ScanResult> scan) {
        if (!isSupportsJsr330InStrictMode || Objects.isNull(parentTypeName)) {
            return null;
        }

        ClassInfo classInfo = toClassInfo(serviceTypeName, scan);
        ClassInfo parentClassInfo = toClassInfo(parentTypeName, scan);

        MethodInfoList parentMethods = parentClassInfo.getDeclaredMethodInfo();
        Map<IdAndToString, MethodInfo> injectedParentMethods = parentMethods.stream()
                .filter((m) -> Objects.nonNull(m.getAnnotationInfo(Inject.class.getName())))
                .filter((m) -> DefaultExternalModuleCreator.isPicoSupported(parentTypeName, m, false, getLogger()))
                .collect(Collectors.toMap(DefaultActivatorCreator::toBaseIdTag, Function.identity()));
        if (injectedParentMethods.isEmpty()) {
            return null;
        }

        MethodInfoList methods = classInfo.getDeclaredMethodInfo();
        Map<IdAndToString, MethodInfo> allSupportedMethodsOnServiceType = methods.stream()
                .filter((m) -> DefaultExternalModuleCreator.isPicoSupported(serviceTypeName, m, false, getLogger()))
                .collect(Collectors.toMap(DefaultActivatorCreator::toBaseIdTag, Function.identity()));

        List<Object> removeList = null;

        for (Map.Entry<IdAndToString, MethodInfo> e : injectedParentMethods.entrySet()) {
            MethodInfo method = allSupportedMethodsOnServiceType.get(e.getKey());
            if (Objects.nonNull(method)) {
                AnnotationInfo annotationInfo = method.getAnnotationInfo(Inject.class.getName());
                if (Objects.nonNull(annotationInfo)) {
                    continue;
                }
                if (Objects.isNull(removeList)) {
                    removeList = new LinkedList<>();
                }
                removeList.add(e.getKey());
            }
        }

        return removeList;
    }

    static ClassInfo toClassInfo(TypeName serviceTypeName, LazyValue<ScanResult> scan) {
        ClassInfo classInfo = scan.get().getClassInfo(serviceTypeName.name());
        if (Objects.isNull(classInfo)) {
            throw new ToolsException("unable to introspect: " + serviceTypeName);
        }
        return classInfo;
    }

    static IdAndToString toBaseIdTag(MethodInfo m) {
        String packageName = m.getClassInfo().getPackageName();
        boolean isPackagePrivate = isPackagePrivate(m.getModifiers());
        InjectionPointInfo.Access access = (isPackagePrivate)
                ? InjectionPointInfo.Access.PACKAGE_PRIVATE : InjectionPointInfo.Access.PUBLIC;
        String idTag = toBaseIdTagName(m.getName(), m.getParameterInfo().length, access, packageName);
        return new IdAndToString(idTag, m);
    }

    static String toBaseIdTagName(DefaultInjectionPointInfo ipInfo, TypeName serviceTypeName) {
        String packageName = serviceTypeName.packageName();
        return toBaseIdTagName(ipInfo.elementName(), ipInfo.getElementArgs(), ipInfo.access(), packageName);
    }

    static String toBaseIdTagName(String methodName, int methodArgCount, InjectionPointInfo.Access access, String packageName) {
        return DefaultInjectionPointInfo.toMethodBaseIdentity(methodName, methodArgCount, access, () -> packageName);
    }

    protected Double getWeightedPriority(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        Double weight = codeGen.getServiceTypeWeightedPriorities().get(serviceTypeName);
        if (Objects.isNull(weight) && hasParent(serviceTypeName, codeGen)) {
            // we might be a child of another service, in which case we will need to override its value
            weight = Weighted.DEFAULT_WEIGHT;
        }
        return weight;
    }

    protected Integer getRunLevel(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        Integer runLevel = codeGen.getServiceTypeRunLevels().get(serviceTypeName);
        if (Objects.isNull(runLevel) && hasParent(serviceTypeName, codeGen)) {
            // we might be a child of another service, in which case we will need to override its value
            runLevel = RunLevel.NORMAL;
        }
        return runLevel;
    }

    protected boolean hasParent(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return Objects.nonNull(parentOf(serviceTypeName, codeGen));
    }

    protected TypeName parentOf(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return codeGen.getServiceTypeToParentServiceTypes().get(serviceTypeName);
    }

    protected List<String> toDescription(TypeName serviceTypeName) {
        return Collections.singletonList("Activator for {@link " + serviceTypeName + "}.");
    }

    protected TypeName toActivatorTypeName(TypeName serviceTypeName) {
        return serviceTypeName;
    }

    protected TypeName toParentTypeName(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return codeGen.getServiceTypeToParentServiceTypes().get(serviceTypeName);
    }

    protected String toActivatorGenericDecl(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return codeGen.getServiceTypeToActivatorGenericDecl().get(serviceTypeName);
    }

    protected Set<String> toScopeTypeNames(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return codeGen.getServiceTypeScopeNames().get(serviceTypeName);
    }

    /**
     * One might expect that isProvider should only be set to true if the service type implements Provider<>. However,
     * that alone would fail JSR-330 testing. The interpretation there is any service without a scope is inferred to be
     * non-singleton, provided/dependent scope.
     */
    protected boolean toIsProvider(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        Set<String> scopeTypeName = toScopeTypeNames(serviceTypeName, codeGen);
        if ((Objects.isNull(scopeTypeName) || scopeTypeName.isEmpty()) && toIsConcrete(serviceTypeName, codeGen)) {
            return true;
        }

        Set<TypeName> providerFor = codeGen.getServiceTypeToProviderForTypes().get(serviceTypeName);
        return Objects.nonNull(providerFor) && !providerFor.isEmpty();
    }

    protected boolean toIsConcrete(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        Boolean isAbstract = codeGen.getServiceTypeIsAbstractTypes().get(serviceTypeName);
        return Objects.isNull(isAbstract) || !isAbstract;
    }

    /**
     * Creates service info from the service type name and the activator create codegen request.
     *
     * @param serviceTypeName the service type name
     * @param codeGen         the code gen request
     * @return the service info
     */
    public ServiceInfo toServiceInfo(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        Set<TypeName> contracts = codeGen.getServiceTypeContracts().get(serviceTypeName);
        Set<TypeName> externalContracts = codeGen.getServiceTypeExternalContracts().get(serviceTypeName);
        Set<QualifierAndValue> qualifiers = codeGen.getServiceTypeQualifiers().get(serviceTypeName);
        return DefaultServiceInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .contractsImplemented(CommonUtils.toStringSet(contracts, TypeName::name))
                .externalContractsImplemented(CommonUtils.toStringSet(externalContracts, TypeName::name))
                .qualifiers(Objects.isNull(qualifiers) ? Collections.emptySet() : qualifiers)
                .build();
    }

    protected Dependencies toDependencies(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        if (Objects.isNull(serviceTypeName)) {
            return null;
        }

        return codeGen.getServiceTypeInjectionPointDependencies().get(serviceTypeName);
    }

    protected String getPostConstructMethodName(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return codeGen.getServiceTypePostConstructMethodNames().get(serviceTypeName);
    }

    protected String getPreDestroyMethodName(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        return codeGen.getServiceTypePreDestroyMethodNames().get(serviceTypeName);
    }

    protected ActivatorCreatorResponse handleError(ActivatorCreatorRequest request,
                                   ToolsException e,
                                   DefaultActivatorCreatorResponse.DefaultActivatorCreatorResponseBuilder<?, ?> builder) {
        if (request.isFailOnError()) {
            throw e;
        }

        return builder
                .error(e)
                .success(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    protected List<TypeName> getServiceTypeHierarchy(TypeName serviceTypeName,
                                                          ActivatorCreatorCodeGen codeGen,
                                                          LazyValue<ScanResult> scan) {
        Map<TypeName, List<TypeName>> map = codeGen.getServiceTypeHierarchy();
        List<TypeName> order = (Objects.nonNull(map)) ? map.get(serviceTypeName) : null;
        if (Objects.nonNull(order)) {
            return (1 == order.size()) ? null : order;
        }

        return getServiceTypeHierarchy(serviceTypeName, scan);
    }

    protected List<String> getExtraCodeGen(TypeName serviceTypeName, ActivatorCreatorCodeGen codeGen) {
        Map<TypeName, List<String>> map = codeGen.getExtraCodeGen();
        List<String> extraCodeGen = (Objects.nonNull(map)) ? map.get(serviceTypeName) : null;
        return Objects.isNull(extraCodeGen) ? Collections.emptyList() : extraCodeGen;
    }

    protected static List<TypeName> getServiceTypeHierarchy(TypeName serviceTypeName, LazyValue<ScanResult> scan) {
        List<TypeName> order = new LinkedList<>();
        ClassInfo classInfo = toClassInfo(serviceTypeName, scan);
        while (Objects.nonNull(classInfo)) {
            order.add(0, createTypeNameFromClassInfo(classInfo));
            classInfo = classInfo.getSuperclass();
        }
        return (1 == order.size()) ? null : order;
    }

    @Builder
    @Getter
    static class ActivatorCreatorArgs {
        private final String template;
        private final TypeName serviceTypeName;
        private final TypeName activatorTypeName;
        private final String activatorGenericDecl;
        private final TypeName parentTypeName;
        private final Set<String> scopeTypeNames;
        private final List<String> description;
        private final ServiceInfo serviceInfo;
        private final Dependencies dependencies;
        private final Dependencies parentDependencies;
        private final Collection<Object> injectionPointsSkippedInParent;
        private final List<?> serviceTypeInjectionOrder;
        private final String generatedSticker;
        private final Double weightedPriority;
        private final Integer runLevel;
        private final String postConstructMethodName;
        private final String preDestroyMethodName;
        private final List<String> extraCodeGen;
        private final boolean isConcrete;
        private final boolean isProvider;
        private final boolean isSupportsJsr330InStrictMode;
    }

}
