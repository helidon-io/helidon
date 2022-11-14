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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.pico.Application;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.spi.ext.InjectionPlan;
import io.helidon.pico.spi.impl.DefaultInjectionPointInfo;
import io.helidon.pico.spi.impl.DefaultPicoServices;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ApplicationCreator;
import io.helidon.pico.tools.creator.ApplicationCreatorCodeGen;
import io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions;
import io.helidon.pico.tools.creator.ApplicationCreatorRequest;
import io.helidon.pico.tools.creator.ApplicationCreatorResponse;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.CompilerOptions;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.ModuleUtils;
import io.helidon.pico.tools.utils.TemplateHelper;
import io.helidon.pico.types.TypeName;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * The default reference implementation for {@link io.helidon.pico.tools.creator.ApplicationCreator}.
 */
@Singleton
public class DefaultApplicationCreator extends AbstractCreator implements ApplicationCreator {

    static final String NAME = PicoServicesConfig.NAME;

    static final String SERVICE_PROVIDER_APPLICATION_SERVICETYPEBINDING_HBS
            = "service-provider-application-servicetypebinding.hbs";
    static final String SERVICE_PROVIDER_APPLICATION_HBS
            = "service-provider-application.hbs";

    /**
     * Generates the source and class file for {@link io.helidon.pico.Application} using the current classpath.
     *
     * @param req the request
     * @return the response for application creation
     */
    @Override
    public ApplicationCreatorResponse createApplication(ApplicationCreatorRequest req) {
        DefaultApplicationCreatorResponse.DefaultApplicationCreatorResponseBuilder builder =
                DefaultApplicationCreatorResponse.builder();

        if (Objects.isNull(req.getServiceTypeNames())) {
            return handleError(req, new ToolsException("ServiceTypeNames is required to be passed"), builder);
        }

        if (Objects.isNull(req.getCodeGenRequest())) {
            return handleError(req, new ToolsException("CodeGenPaths are required"), builder);
        }

        List<TypeName> providersInUseThatAreAllowed = providersNotAllowed(req);
        if (!providersInUseThatAreAllowed.isEmpty()) {
            return handleError(req,
                   new ToolsException("There are dynamic " + Provider.class.getSimpleName()
                                              + "s being used that are not whitelisted: "
                                              + providersInUseThatAreAllowed
                                              + "; see the documentation for examples of whitelisting."), builder);
        }

        try {
            return codegen(req, builder);
        } catch (ToolsException te) {
            return handleError(req, te, builder);
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            return handleError(req, new ToolsException("failed in create", t), builder);
        }
    }

    protected List<TypeName> providersNotAllowed(ApplicationCreatorRequest req) {
        final PicoServices picoServices = PicoServices.picoServices().get();
        final Services services = picoServices.services();

        List<ServiceProvider<Provider>> providers = services.lookup(Provider.class);
        if (providers.isEmpty()) {
            return Collections.emptyList();
        }

        List<TypeName> providersInUseThatAreNotAllowed = new LinkedList<>();
        for (TypeName typeName : req.getServiceTypeNames()) {
            if (!isWhiteListedProviderName(req.getConfigOptions(), typeName)
                    && isProvider(typeName, services)
                    && !isWhiteListedProviderQualifierTypeName(req.getConfigOptions(), typeName, services)) {
                providersInUseThatAreNotAllowed.add(typeName);
            }
        }
        return providersInUseThatAreNotAllowed;
    }

    protected static boolean isWhiteListedProviderName(ApplicationCreatorConfigOptions configOptions,
                                                       TypeName typeName) {
        ApplicationCreatorConfigOptions.PermittedProviderType opt = configOptions.getPermittedProviderTypes();
        if (ApplicationCreatorConfigOptions.PermittedProviderType.ALL == opt) {
            return true;
        } else if (ApplicationCreatorConfigOptions.PermittedProviderType.NONE == opt) {
            return false;
        } else if (configOptions.getPermittedProviderNames().contains(typeName.name())) {
            return true;
        }
        return false;
    }

    protected static ServiceInfo toServiceInfo(TypeName typeName) {
        return DefaultServiceInfo.builder().serviceTypeName(typeName.name()).build();
    }

    protected static ServiceProvider<?> toServiceProvider(TypeName typeName, Services services) {
        return services.lookupFirst(toServiceInfo(typeName), true);
    }

    protected static boolean isProvider(TypeName typeName, Services services) {
        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        return sp.isProvider();
    }

    protected static boolean isWhiteListedProviderQualifierTypeName(ApplicationCreatorConfigOptions configOptions,
                                                                    TypeName typeName,
                                                                    Services services) {
        Set<TypeName> permittedTypeNames = configOptions.getPermittedProviderQualifierTypeNames();
        if (permittedTypeNames.isEmpty()) {
            return false;
        }

        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        Set<TypeName> spQualifierTypeNames = sp.serviceInfo().qualifiers().stream()
                .map(it -> it.typeName())
                .collect(Collectors.toSet());
        spQualifierTypeNames.retainAll(permittedTypeNames);
        return !spQualifierTypeNames.isEmpty();
    }

    @SuppressWarnings("unchecked")
    protected ApplicationCreatorResponse codegen(ApplicationCreatorRequest req,
                                 DefaultApplicationCreatorResponse.DefaultApplicationCreatorResponseBuilder builder) {
        final PicoServices picoServices = PicoServices.picoServices().get();

        String serviceTypeBindingTemplate = TemplateHelper
                .safeLoadTemplate(req.getTemplateName(), SERVICE_PROVIDER_APPLICATION_SERVICETYPEBINDING_HBS);

        List<TypeName> serviceTypeNames = new LinkedList<>();
        List<String> serviceTypeBindings = new ArrayList<>();
        for (TypeName serviceTypeName : req.getServiceTypeNames()) {
            String injectionPlan = toServiceTypeInjectionPlan(picoServices,
                                                              serviceTypeName,
                                                              serviceTypeBindingTemplate);
            if (Objects.isNull(injectionPlan)) {
                continue;
            }
            serviceTypeNames.add(serviceTypeName);
            serviceTypeBindings.add(injectionPlan);
        }

        TypeName application = toApplicationTypeName(req);
        serviceTypeNames.add(application);

        String moduleName = toModuleName(req);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", application.className());
        subst.put("packagename", application.packageName());
        subst.put("description", null);
        subst.put("generatedanno", getGeneratedSticker(req));
        subst.put("modulename", moduleName);
        subst.put("servicetypebindings", serviceTypeBindings);

        String template = TemplateHelper.safeLoadTemplate(req.getTemplateName(), SERVICE_PROVIDER_APPLICATION_HBS);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        String body = TemplateHelper.applySubstitutions(ps, template, subst).trim();

        if (Objects.nonNull(req.getCodeGenPaths().getGeneratedSourcesPath())) {
            codegen(picoServices, (DefaultApplicationCreatorRequest) req, application, body);
        }

        DefaultGeneralCodeGenDetail codeGenDetail = DefaultGeneralCodeGenDetail.builder()
                .serviceTypeName(application)
                .body(body)
                .build();
        DefaultApplicationCreatorCodeGen codeGenResponse = DefaultApplicationCreatorCodeGen.builder()
                .packageName(application.packageName())
                .className(application.className())
                .classPrefixName(req.getCodeGenRequest().getClassPrefixName())
                .build();
        return (ApplicationCreatorResponse) builder
                .applicationCodeGenResponse(codeGenResponse)
                .serviceTypeNames(serviceTypeNames)
                .serviceTypeDetail(application, codeGenDetail)
                .templateName(req.getTemplateName())
                .moduleName(req.getModuleName())
                .build();
    }

    protected static String toApplicationClassName(String modulePrefix) {
        modulePrefix = Objects.isNull(modulePrefix) ? "" : modulePrefix;
        return NAME + modulePrefix + "Application";
    }

    protected TypeName toApplicationTypeName(ApplicationCreatorRequest req) {
        ApplicationCreatorCodeGen codeGen = Objects.requireNonNull(req.getCodeGenRequest());
        String packageName = codeGen.getPackageName();
        if (Objects.isNull(packageName)) {
            packageName = PicoServicesConfig.NAME;
        }
        String className = Objects.requireNonNull(codeGen.getClassName());
        return DefaultTypeName.create(packageName, className);
    }

    protected String toModuleName(ApplicationCreatorRequest req) {
        String moduleName = req.getModuleName();
        return Objects.nonNull(moduleName) ? moduleName : SimpleModuleDescriptor.DEFAULT_MODULE_NAME;
    }

    protected String toServiceTypeInjectionPlan(PicoServices picoServices,
                                                TypeName serviceTypeName,
                                                String serviceTypeBindingTemplate) {
        final Services services = picoServices.services();

        ServiceInfo si = DefaultServiceInfo.builder().serviceTypeName(serviceTypeName.name()).build();
        ServiceProvider<Object> sp = services.lookupFirst(si);
        if (Objects.isNull(sp)) {
            throw new ToolsException("expected to find service provider " + si);
        } else if (!DefaultPicoServices.isQualifiedInjectionTarget(sp)) {
            return null;
        } else {
            String activator = toActivatorCodeGen(sp);
            if (Objects.isNull(activator)) {
                return null;
            }
            Map<String, Object> subst = new HashMap<>();
            subst.put("servicetypename", serviceTypeName.name());
            subst.put("activator", activator);
            subst.put("injectionplan", toInjectionPlanBindings(sp));
            subst.put("modulename", sp.serviceInfo().moduleName());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
            return TemplateHelper.applySubstitutions(ps, serviceTypeBindingTemplate, subst);
        }
    }

    @SuppressWarnings("unchecked")
    protected List<String> toInjectionPlanBindings(ServiceProvider<Object> sp) {
        AbstractServiceProvider asp = ServiceProviderBindable.toRootProvider(sp);
        DependenciesInfo deps = asp.dependencies();
        if (Objects.isNull(deps) || deps.allDependencies().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> plan = new ArrayList<>(deps.allDependencies().size());
        Map<String, InjectionPlan<Object>> injectionPlan = asp.getOrCreateInjectionPlan(false);
        for (Map.Entry<String, InjectionPlan<Object>> e : injectionPlan.entrySet()) {
            StringBuilder line = new StringBuilder();

            DefaultInjectionPointInfo ipInfo = e.getValue().getIpInfo();
            List<? extends ServiceProvider<?>> ipQualified = e.getValue().getIpQualifiedServiceProviders();
            List<?> ipUnqualified = e.getValue().getIpUnqualifiedServiceProviderResolutions();
            boolean resolved = false;
            if (Objects.isNull(ipQualified) || ipQualified.isEmpty()) {
                if (Objects.nonNull(ipUnqualified) && !ipUnqualified.isEmpty()) {
                    resolved = true;
                    line.append(".resolvedBind(");
                } else {
                    line.append(".bindVoid(");
                }
            } else if (ipInfo.isListWrapped()) {
                line.append(".bindMany(");
            } else {
                line.append(".bind(");
            }

            line.append("\"").append(e.getKey()).append("\"");

            if (resolved) {
                Object target = ipUnqualified.get(0);
                if (!(target instanceof Class)) {
                    target = target.getClass();
                }
                line.append(", ").append(((Class<?>) target).getName()).append(".class");
            } else if (Objects.isNull(ipQualified) || ipQualified.isEmpty()) {
                // no-op
            } else if (ipInfo.isListWrapped()) {
                line.append(", ").append(toActivatorCodeGen((Collection<ServiceProvider<?>>) ipQualified));
            } else {
                line.append(", ").append(toActivatorCodeGen(ipQualified.get(0)));
            }
            line.append(")");

            plan.add(line.toString());
        }

        return plan;
    }

    /**
     * Perform the file creation and javac it.
     *
     * @param picoServices        the pico services to use
     * @param req                 the request
     * @param applicationTypeName the application type name
     * @param body                the source code / body to generate
     */
    protected void codegen(PicoServices picoServices,
                           DefaultApplicationCreatorRequest req,
                           TypeName applicationTypeName,
                           String body) {
        CodeGenFiler filer = createDirectCodeGenFiler(req.getCodeGenPaths(), req.isAnalysisOnly());
        File applicationJavaFile = filer.codegenJavaFilerOut(applicationTypeName, body);

        final String outputDirectory = req.getCodeGenPaths().getOutputPath();
        if (Objects.nonNull(outputDirectory)) {
            final File outDir = new File(outputDirectory);

            // setup meta-inf services
            codegenMetaInfServices(filer,
                                   req.getCodeGenPaths(),
                                   Collections.singletonMap(Application.class.getName(),
                                                            Collections.singletonList(applicationTypeName.name())));

            // setup module-info
            codegenModuleInfoDescriptor(filer, picoServices, req, applicationTypeName);

            // compile, but only if we generated the source file
            if (Objects.nonNull(applicationJavaFile)) {
                CompilerOptions opts = req.getCompilerOptions();
                JavaC compiler = JavaC.builder()
                        .outputDirectory(outDir)
                        .classpath(opts.getClasspath())
                        .modulepath(opts.getModulepath())
                        .sourcepath(opts.getSourcepath())
                        .source(opts.getSource())
                        .target(opts.getTarget())
                        .commandLineArgs(opts.getCommandLineArguments())
                        .logger(getLogger())
                        .messager(req.getMessager())
                        .build();
                JavaC.Result result = compiler.compile(applicationJavaFile);
                ToolsException e = result.maybeGenerateError();
                if (Objects.nonNull(e)) {
                    throw new ToolsException("failed to compile: " + applicationJavaFile, e);
                }
            }
        }
    }

    protected TypeName moduleServiceTypeOf(PicoServices picoServices, String moduleName) {
        Services services = picoServices.services();
        ServiceProvider<?> serviceProvider = services.lookupFirst(Module.class, moduleName);
        return DefaultTypeName.createFromTypeName(serviceProvider.serviceInfo().serviceTypeName());
    }

    protected void codegenModuleInfoDescriptor(CodeGenFiler filer,
                                               PicoServices picoServices,
                                               DefaultApplicationCreatorRequest req,
                                               TypeName applicationTypeName) {
        File picoModuleInfo = filer.toResourceLocation(ModuleUtils.PICO_MODULE_INFO_JAVA_NAME);
        SimpleModuleDescriptor descriptor = filer.readModuleInfo(ModuleUtils.PICO_MODULE_INFO_JAVA_NAME);
        if (Objects.nonNull(descriptor)) {
            assert (Objects.nonNull(picoModuleInfo));
            String moduleName = req.getModuleName();
            if (Objects.isNull(moduleName) || SimpleModuleDescriptor.DEFAULT_MODULE_NAME.equals(moduleName)) {
                moduleName = descriptor.getName();
            }
            TypeName moduleTypeName = moduleServiceTypeOf(picoServices, moduleName);
            String generator = req.getGenerator(getClass());
            String typePrefix = req.getCodeGenRequest().getClassPrefixName();
            PicoModuleBuilderRequest moduleBuilderRequest = PicoModuleBuilderRequest.builder()
                    .moduleName(moduleName)
                    .moduleTypeName(moduleTypeName)
                    .applicationTypeName(applicationTypeName)
                    .generator(generator)
                    .moduleInfoPath(picoModuleInfo.getPath())
                    .classPrefixName(typePrefix)
                    .isApplicationCreated(true)
                    .isModuleCreated(false)
                    .build();
            descriptor = createModuleInfo(moduleBuilderRequest);
            filer.codegenModuleInfoFilerOut(descriptor, true);
        } else {
            File realModuleInfo = filer.toSourceLocation(ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
            if (Objects.nonNull(realModuleInfo) && realModuleInfo.exists()) {
                throw new ToolsException("expected to find " + picoModuleInfo
                                                 + ". did the " + NAME + " annotation processor run?");
            }
        }
    }
    protected void codegenMetaInfServices(CodeGenFiler filer, CodeGenPaths paths, Map<String, List<String>> metaInfServices) {
        filer.codegenMetaInfServices(paths, metaInfServices);
    }

    protected ApplicationCreatorResponse handleError(ApplicationCreatorRequest request,
                             ToolsException e,
                             DefaultApplicationCreatorResponse.DefaultApplicationCreatorResponseBuilder builder) {
        if (request.isFailOnError()) {
            throw e;
        }

        return (ApplicationCreatorResponse) builder.error(e).success(false).build();
    }

}
