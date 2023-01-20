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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.Application;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.services.AbstractServiceProvider;
import io.helidon.pico.services.DefaultServiceBinder;
import io.helidon.pico.services.InjectionPlan;
import io.helidon.pico.services.Utils;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * The default reference implementation for {@link ApplicationCreator}.
 *
 * @deprecated
 */
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 1)
public class DefaultApplicationCreator extends AbstractCreator implements ApplicationCreator {
    static final String NAME = PicoServicesConfig.NAME;

    static final String SERVICE_PROVIDER_APPLICATION_SERVICETYPEBINDING_HBS
            = "service-provider-application-servicetypebinding.hbs";
    static final String SERVICE_PROVIDER_APPLICATION_HBS
            = "service-provider-application.hbs";

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public DefaultApplicationCreator() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    /**
     * Generates the source and class file for {@link io.helidon.pico.Application} using the current classpath.
     *
     * @param req the request
     * @return the response for application creation
     */
    @Override
    public ApplicationCreatorResponse createApplication(
            ApplicationCreatorRequest req) {
        DefaultApplicationCreatorResponse.Builder builder =
                DefaultApplicationCreatorResponse.builder();

        if (Objects.isNull(req.serviceTypeNames())) {
            return handleError(req, new ToolsException("ServiceTypeNames is required to be passed"), builder);
        }

        if (Objects.isNull(req.codeGen())) {
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
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            return handleError(req, new ToolsException("failed in create", t), builder);
        }
    }

    List<TypeName> providersNotAllowed(
            ApplicationCreatorRequest req) {
        PicoServices picoServices = PicoServices.picoServices().orElseThrow();
        Services services = picoServices.services();

        List<ServiceProvider<Provider>> providers = services.lookupAll(Provider.class);
        if (providers.isEmpty()) {
            return List.of();
        }

        List<TypeName> providersInUseThatAreNotAllowed = new LinkedList<>();
        for (TypeName typeName : req.serviceTypeNames()) {
            if (!isWhiteListedProviderName(req.configOptions(), typeName)
                    && isProvider(typeName, services)
                    && !isWhiteListedProviderQualifierTypeName(req.configOptions(), typeName, services)) {
                providersInUseThatAreNotAllowed.add(typeName);
            }
        }
        return providersInUseThatAreNotAllowed;
    }

    static boolean isWhiteListedProviderName(
            ApplicationCreatorConfigOptions configOptions,
            TypeName typeName) {
        ApplicationCreatorConfigOptions.PermittedProviderType opt = configOptions.permittedProviderTypes();
        if (ApplicationCreatorConfigOptions.PermittedProviderType.ALL == opt) {
            return true;
        } else if (ApplicationCreatorConfigOptions.PermittedProviderType.NONE == opt) {
            return false;
        } else if (configOptions.permittedProviderNames().contains(typeName.name())) {
            return true;
        }
        return false;
    }

    static ServiceInfoCriteria toServiceInfoCriteria(
            TypeName typeName) {
        return DefaultServiceInfoCriteria.builder().serviceTypeName(typeName.name()).build();
    }

    static ServiceProvider<?> toServiceProvider(
            TypeName typeName,
            Services services) {
        return services.lookupFirst(toServiceInfoCriteria(typeName), true).orElseThrow();
    }

    static boolean isProvider(
            TypeName typeName,
            Services services) {
        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        return sp.isProvider();
    }

    static boolean isWhiteListedProviderQualifierTypeName(
            ApplicationCreatorConfigOptions configOptions,
            TypeName typeName,
            Services services) {
        Set<TypeName> permittedTypeNames = configOptions.permittedProviderQualifierTypeNames();
        if (permittedTypeNames.isEmpty()) {
            return false;
        }

        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        Set<TypeName> spQualifierTypeNames = sp.serviceInfo().qualifiers().stream()
                .map(AnnotationAndValue::typeName)
                .collect(Collectors.toSet());
        spQualifierTypeNames.retainAll(permittedTypeNames);
        return !spQualifierTypeNames.isEmpty();
    }

    ApplicationCreatorResponse codegen(
            ApplicationCreatorRequest req,
            DefaultApplicationCreatorResponse.Builder builder) {
        PicoServices picoServices = PicoServices.picoServices().orElseThrow();

        String serviceTypeBindingTemplate = templateHelper()
                .safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_SERVICETYPEBINDING_HBS);

        List<TypeName> serviceTypeNames = new LinkedList<>();
        List<String> serviceTypeBindings = new ArrayList<>();
        for (TypeName serviceTypeName : req.serviceTypeNames()) {
            String injectionPlan = toServiceTypeInjectionPlan(picoServices,
                                                              serviceTypeName,
                                                              serviceTypeBindingTemplate);
            if (injectionPlan == null) {
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
        subst.put("generatedanno", toGeneratedSticker(req));
        subst.put("modulename", moduleName);
        subst.put("servicetypebindings", serviceTypeBindings);

        String template = templateHelper().safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_HBS);
        String body = templateHelper().applySubstitutions(template, subst, true).trim();

        if (Objects.nonNull(req.codeGenPaths().generatedSourcesPath())) {
            codegen(picoServices, req, application, body);
        }

        GeneralCodeGenDetail codeGenDetail = DefaultGeneralCodeGenDetail.builder()
                .serviceTypeName(application)
                .body(body)
                .build();
        ApplicationCreatorCodeGen codeGenResponse = DefaultApplicationCreatorCodeGen.builder()
                .packageName(application.packageName())
                .className(application.className())
                .classPrefixName(req.codeGen().classPrefixName())
                .build();
        return builder
                .applicationCodeGen(codeGenResponse)
                .serviceTypeNames(serviceTypeNames)
                .addServiceTypeDetail(application, codeGenDetail)
                .templateName(req.templateName())
                .moduleName(req.moduleName())
                .build();
    }

    static String toApplicationClassName(
            String modulePrefix) {
        modulePrefix = (modulePrefix == null) ? "" : modulePrefix;
        return NAME + modulePrefix + "Application";
    }

    static TypeName toApplicationTypeName(
            ApplicationCreatorRequest req) {
        ApplicationCreatorCodeGen codeGen = Objects.requireNonNull(req.codeGen());
        String packageName = codeGen.packageName().orElse(null);
        if (packageName == null) {
            packageName = PicoServicesConfig.NAME;
        }
        String className = Objects.requireNonNull(codeGen.className().orElse(null));
        return DefaultTypeName.create(packageName, className);
    }

    static String toModuleName(
            ApplicationCreatorRequest req) {
        return req.moduleName().orElse(ModuleInfoDescriptor.DEFAULT_MODULE_NAME);
    }

    String toServiceTypeInjectionPlan(
            PicoServices picoServices,
            TypeName serviceTypeName,
            String serviceTypeBindingTemplate) {
        Services services = picoServices.services();

        ServiceInfoCriteria si = toServiceInfoCriteria(serviceTypeName);
        ServiceProvider<?> sp = services.lookupFirst(si);
        if (!Utils.isQualifiedInjectionTarget(sp)) {
            return null;
        } else {
            String activator = toActivatorCodeGen(sp);
            if (activator == null) {
                return null;
            }
            Map<String, Object> subst = new HashMap<>();
            subst.put("servicetypename", serviceTypeName.name());
            subst.put("activator", activator);
            subst.put("injectionplan", toInjectionPlanBindings(sp));
            subst.put("modulename", sp.serviceInfo().moduleName());

            return templateHelper().applySubstitutions(serviceTypeBindingTemplate, subst, true);
        }
    }

    @SuppressWarnings("unchecked")
    List<String> toInjectionPlanBindings(
            ServiceProvider<?> sp) {
        AbstractServiceProvider<?> asp = AbstractServiceProvider
                .toAbstractServiceProvider(DefaultServiceBinder.toRootProvider(sp), true).orElseThrow();
        DependenciesInfo deps = asp.dependencies();
        if (deps.allDependencies().isEmpty()) {
            return List.of();
        }

        List<String> plan = new ArrayList<>(deps.allDependencies().size());
        Map<String, InjectionPlan> injectionPlan = asp.getOrCreateInjectionPlan(false);
        for (Map.Entry<String, InjectionPlan> e : injectionPlan.entrySet()) {
            StringBuilder line = new StringBuilder();

            InjectionPointInfo ipInfo = e.getValue().injectionPointInfo();
            List<? extends ServiceProvider<?>> ipQualified = e.getValue().injectionPointQualifiedServiceProviders();
            List<?> ipUnqualified = e.getValue().unqualifiedProviders();
            boolean resolved = false;
            if (ipQualified == null || ipQualified.isEmpty()) {
                if (ipUnqualified != null && !ipUnqualified.isEmpty()) {
                    resolved = true;
                    line.append(".resolvedBind(");
                } else {
                    line.append(".bindVoid(");
                }
            } else if (ipInfo.listWrapped()) {
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
            } else if (ipQualified == null || ipQualified.isEmpty()) {
                // no-op
            } else if (ipInfo.listWrapped()) {
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
    void codegen(
            PicoServices picoServices,
            ApplicationCreatorRequest req,
            TypeName applicationTypeName,
            String body) {
        CodeGenFiler filer = createDirectCodeGenFiler(req.codeGenPaths(), req.analysisOnly());
        Path applicationJavaFilePath = filer.codegenJavaFilerOut(applicationTypeName, body).orElse(null);

        String outputDirectory = req.codeGenPaths().outputPath();
        if (outputDirectory != null) {
            File outDir = new File(outputDirectory);

            // setup meta-inf services
            codegenMetaInfServices(filer,
                                   req.codeGenPaths(),
                                   Map.of(Application.class.getName(), List.of(applicationTypeName.name())));

            // setup module-info
            codegenModuleInfoDescriptor(filer, picoServices, req, applicationTypeName);

            // compile, but only if we generated the source file
            if (applicationJavaFilePath != null) {
                CompilerOptions opts = req.compilerOptions().orElse(null);
                JavaC.Builder compilerBuilder = JavaC.builder()
                        .outputDirectory(outDir)
                        .logger(logger())
                        .messager(req.messager().orElseThrow());
                if (opts != null) {
                    compilerBuilder
                            .classpath(opts.classpath())
                            .modulepath(opts.modulepath())
                            .sourcepath(opts.sourcepath())
                            .source(opts.source())
                            .target(opts.target())
                            .commandLineArgs(opts.commandLineArguments());
                }
                JavaC compiler = compilerBuilder.build();
                JavaC.Result result = compiler.compile(applicationJavaFilePath.toFile());
                ToolsException e = result.maybeGenerateError();
                if (e != null) {
                    throw new ToolsException("failed to compile: " + applicationJavaFilePath, e);
                }
            }
        }
    }

    static TypeName moduleServiceTypeOf(
            PicoServices picoServices,
            String moduleName) {
        Services services = picoServices.services();
        ServiceProvider<?> serviceProvider = services.lookup(Module.class, moduleName);
        return DefaultTypeName.createFromTypeName(serviceProvider.serviceInfo().serviceTypeName());
    }

    void codegenModuleInfoDescriptor(
            CodeGenFiler filer,
            PicoServices picoServices,
            ApplicationCreatorRequest req,
            TypeName applicationTypeName) {
        Optional<Path> picoModuleInfoPath = filer.toResourceLocation(ModuleUtils.PICO_MODULE_INFO_JAVA_NAME);
        ModuleInfoDescriptor descriptor = filer.readModuleInfo(ModuleUtils.PICO_MODULE_INFO_JAVA_NAME).orElse(null);
        if (descriptor != null) {
            Objects.requireNonNull(picoModuleInfoPath.orElseThrow());
            String moduleName = req.moduleName().orElse(null);
            if (moduleName == null || ModuleInfoDescriptor.DEFAULT_MODULE_NAME.equals(moduleName)) {
                moduleName = descriptor.name();
            }

            TypeName moduleTypeName = moduleServiceTypeOf(picoServices, moduleName);
            String typePrefix = req.codeGen().classPrefixName();
            ModuleInfoCreatorRequest moduleBuilderRequest = DefaultModuleInfoCreatorRequest.builder()
                    .name(moduleName)
                    .moduleTypeName(moduleTypeName)
                    .applicationTypeName(applicationTypeName)
                    .moduleInfoPath(picoModuleInfoPath.get().toAbsolutePath().toString())
                    .classPrefixName(typePrefix)
                    .applicationCreated(true)
                    .moduleCreated(false)
                    .build();
            descriptor = createModuleInfo(moduleBuilderRequest);
            filer.codegenModuleInfoFilerOut(descriptor, true);
        } else {
            Path realModuleInfoPath = filer.toSourceLocation(ModuleUtils.REAL_MODULE_INFO_JAVA_NAME).orElse(null);
            if (realModuleInfoPath != null && !realModuleInfoPath.toFile().exists()) {
                throw new ToolsException("expected to find " + realModuleInfoPath
                                                 + ". did the " + NAME + " annotation processor run?");
            }
        }
    }
    void codegenMetaInfServices(
            CodeGenFiler filer,
            CodeGenPaths paths,
            Map<String, List<String>> metaInfServices) {
        filer.codegenMetaInfServices(paths, metaInfServices);
    }

    ApplicationCreatorResponse handleError(
            ApplicationCreatorRequest request,
            ToolsException e,
            DefaultApplicationCreatorResponse.Builder builder) {
        if (request.throwIfError()) {
            throw e;
        }

        return builder.error(e).success(false).build();
    }

}
