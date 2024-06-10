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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.processor.CopyrightHandler;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.InjectionException;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.runtime.AbstractServiceProvider;
import io.helidon.inject.runtime.HelidonInjectionPlan;
import io.helidon.inject.runtime.ServiceBinderDefault;
import io.helidon.inject.tools.spi.ApplicationCreator;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import static io.helidon.inject.api.ServiceInfoBasics.DEFAULT_INJECT_WEIGHT;
import static io.helidon.inject.runtime.ServiceUtils.isQualifiedInjectionTarget;

/**
 * The default implementation for {@link ApplicationCreator}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Singleton
@Weight(DEFAULT_INJECT_WEIGHT)
public class ApplicationCreatorDefault extends AbstractCreator implements ApplicationCreator {
    /**
     * The prefix to add before the generated "Application" class name (i.e., "Injection$$" in the "Injection$$Application").
     */
    public static final String NAME_PREFIX = AbstractCreator.NAME_PREFIX;

    /**
     * The "Application" part of the name.
     */
    public static final String APPLICATION_NAME_SUFFIX = "Application";

    /**
     * The FQN "Injection$$Application" name.
     */
    public static final String APPLICATION_NAME = NAME_PREFIX + APPLICATION_NAME_SUFFIX;

    static final String SERVICE_PROVIDER_APPLICATION_SERVICETYPEBINDING_HBS
            = "service-provider-application-servicetypebinding.hbs";
    static final String SERVICE_PROVIDER_APPLICATION_EMPTY_SERVICETYPEBINDING_HBS
            = "service-provider-application-empty-servicetypebinding.hbs";
    static final String SERVICE_PROVIDER_APPLICATION_HBS
            = "service-provider-application.hbs";
    private static final TypeName CREATOR = TypeName.create(ApplicationCreatorDefault.class);

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ApplicationCreatorDefault() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    static boolean isAllowListedProviderName(ApplicationCreatorConfigOptions configOptions,
                                             TypeName typeName) {
        PermittedProviderType opt = configOptions.permittedProviderTypes();
        if (PermittedProviderType.ALL == opt) {
            return true;
        } else if (PermittedProviderType.NONE == opt) {
            return false;
        } else {
            return configOptions.permittedProviderNames().contains(typeName.name());
        }
    }

    static ServiceInfoCriteria toServiceInfoCriteria(TypeName typeName) {
        return ServiceInfoCriteria.builder()
                .serviceTypeName(typeName)
                .includeIntercepted(true)
                .build();
    }

    static ServiceProvider<?> toServiceProvider(TypeName typeName,
                                                Services services) {
        return services.lookupFirst(toServiceInfoCriteria(typeName), true).orElseThrow();
    }

    static boolean isProvider(TypeName typeName,
                              Services services) {
        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        return sp.isProvider();
    }

    static boolean isAllowListedProviderQualifierTypeName(ApplicationCreatorConfigOptions configOptions,
                                                          TypeName typeName,
                                                          Services services) {
        Set<TypeName> permittedTypeNames = configOptions.permittedProviderQualifierTypeNames();
        if (permittedTypeNames.isEmpty()) {
            return false;
        }

        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        Set<TypeName> spQualifierTypeNames = sp.serviceInfo().qualifiers().stream()
                .map(Annotation::typeName)
                .collect(Collectors.toSet());
        spQualifierTypeNames.retainAll(permittedTypeNames);
        return !spQualifierTypeNames.isEmpty();
    }

    /**
     * Will uppercase the first letter of the provided name.
     *
     * @param name the name
     * @return the mame with the first letter capitalized
     */
    public static String upperFirstChar(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    static TypeName toApplicationTypeName(ApplicationCreatorRequest req) {
        ApplicationCreatorCodeGen codeGen = Objects.requireNonNull(req.codeGen());
        String packageName = codeGen.packageName().orElse(null);
        if (packageName == null) {
            packageName = "inject";
        }
        String className = Objects.requireNonNull(codeGen.className().orElse(null));
        return TypeName.builder()
                .packageName(packageName)
                .className(className)
                .build();
    }

    static String toModuleName(ApplicationCreatorRequest req) {
        return req.moduleName().orElse(ModuleInfoDescriptor.DEFAULT_MODULE_NAME);
    }

    static Optional<TypeName> moduleServiceTypeOf(InjectionServices injectionServices,
                                                  String moduleName) {
        Services services = injectionServices.services();
        ServiceProvider<?> serviceProvider;
        try {
            serviceProvider = services.lookup(ModuleComponent.class, moduleName);
        } catch (InjectionException e) {
            return Optional.empty();
        }
        return Optional.of(serviceProvider.serviceInfo().serviceTypeName());
    }

    /**
     * Generates the source and class file for {@link Application} using the current classpath.
     *
     * @param req the request
     * @return the response for application creation
     */
    @Override
    public ApplicationCreatorResponse createApplication(ApplicationCreatorRequest req) {
        ApplicationCreatorResponse.Builder builder = ApplicationCreatorResponse.builder();

        if (req.serviceTypeNames() == null) {
            return handleError(req, new ToolsException("ServiceTypeNames is required to be passed"), builder);
        }

        if (req.codeGen() == null) {
            return handleError(req, new ToolsException("CodeGenPaths are required"), builder);
        }

        List<TypeName> providersInUseThatAreAllowed = providersNotAllowed(req);
        if (!providersInUseThatAreAllowed.isEmpty()) {
            return handleError(req,
                               new ToolsException("There are dynamic " + Provider.class.getSimpleName()
                                                          + "s being used that are not allow-listed: "
                                                          + providersInUseThatAreAllowed
                                                          + "; see the documentation for examples of allow-listing."), builder);
        }

        try {
            return codegen(req, builder);
        } catch (ToolsException te) {
            return handleError(req, te, builder);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            return handleError(req, new ToolsException("Failed during create", t), builder);
        }
    }

    @SuppressWarnings("rawtypes")
    List<TypeName> providersNotAllowed(ApplicationCreatorRequest req) {
        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();
        Services services = injectionServices.services();

        List<ServiceProvider<Provider>> providers = services.lookupAll(Provider.class);
        if (providers.isEmpty()) {
            return List.of();
        }

        List<TypeName> providersInUseThatAreNotAllowed = new ArrayList<>();
        for (TypeName typeName : req.serviceTypeNames()) {
            if (!isAllowListedProviderName(req.configOptions(), typeName)
                    && isProvider(typeName, services)
                    && !isAllowListedProviderQualifierTypeName(req.configOptions(), typeName, services)) {
                providersInUseThatAreNotAllowed.add(typeName);
            }
        }
        return providersInUseThatAreNotAllowed;
    }

    ApplicationCreatorResponse codegen(ApplicationCreatorRequest req,
                                       ApplicationCreatorResponse.Builder builder) {
        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();

        String serviceTypeBindingTemplate = templateHelper()
                .safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_SERVICETYPEBINDING_HBS);
        String serviceTypeBindingEmptyTemplate = templateHelper()
                .safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_EMPTY_SERVICETYPEBINDING_HBS);

        List<TypeName> serviceTypeNames = new ArrayList<>();
        List<String> serviceTypeBindings = new ArrayList<>();
        for (TypeName serviceTypeName : req.serviceTypeNames()) {
            try {
                String injectionPlan = toServiceTypeInjectionPlan(injectionServices, serviceTypeName,
                                                                  serviceTypeBindingTemplate, serviceTypeBindingEmptyTemplate);
                if (injectionPlan == null) {
                    continue;
                }
                serviceTypeNames.add(serviceTypeName);
                serviceTypeBindings.add(injectionPlan);
            } catch (Exception e) {
                throw new ToolsException("Error during injection plan generation for: " + serviceTypeName, e);
            }
        }

        TypeName application = toApplicationTypeName(req);
        serviceTypeNames.add(application);

        String moduleName = toModuleName(req);

        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", application.className());
        subst.put("packagename", application.packageName());
        subst.put("description", application + " - Generated Application.");
        subst.put("header", CopyrightHandler.copyright(CREATOR,
                                                       CREATOR,
                                                       application));
        subst.put("generatedanno", toGeneratedSticker(CREATOR,
                                                      CREATOR, // there is no specific type trigger for application
                                                      application));
        subst.put("modulename", moduleName);
        subst.put("servicetypebindings", serviceTypeBindings);

        String template = templateHelper().safeLoadTemplate(req.templateName(), SERVICE_PROVIDER_APPLICATION_HBS);
        String body = templateHelper().applySubstitutions(template, subst, true).trim();

        if (req.codeGenPaths().isPresent()
                && req.codeGenPaths().get().generatedSourcesPath().isPresent()) {
            codegen(injectionServices, req, application, body);
        }

        GeneralCodeGenDetail codeGenDetail = GeneralCodeGenDetail.builder()
                .serviceTypeName(application)
                .body(body)
                .build();
        ApplicationCreatorCodeGen codeGenResponse = ApplicationCreatorCodeGen.builder()
                .packageName(application.packageName())
                .className(application.className())
                .classPrefixName(req.codeGen().classPrefixName())
                .build();
        return builder
                .applicationCodeGen(codeGenResponse)
                .serviceTypeNames(serviceTypeNames)
                .putServiceTypeDetail(application, codeGenDetail)
                .templateName(req.templateName())
                .moduleName(req.moduleName())
                .build();
    }

    String toServiceTypeInjectionPlan(InjectionServices injectionServices,
                                      TypeName serviceTypeName,
                                      String serviceTypeBindingTemplate,
                                      String serviceTypeBindingEmptyTemplate) {
        Services services = injectionServices.services();

        ServiceInfoCriteria si = toServiceInfoCriteria(serviceTypeName);
        ServiceProvider<?> sp = services.lookupFirst(si);
        String activator = toActivatorCodeGen(sp);
        if (activator == null) {
            return null;
        }
        Map<String, Object> subst = new HashMap<>();
        subst.put("servicetypename", serviceTypeName.name());
        subst.put("activator", activator);
        subst.put("modulename", sp.serviceInfo().moduleName().orElse(null));
        if (isQualifiedInjectionTarget(sp)) {
            subst.put("injectionplan", toInjectionPlanBindings(sp));
            return templateHelper().applySubstitutions(serviceTypeBindingTemplate, subst, true);
        } else {
            return templateHelper().applySubstitutions(serviceTypeBindingEmptyTemplate, subst, true);
        }
    }

    @SuppressWarnings("unchecked")
    List<String> toInjectionPlanBindings(ServiceProvider<?> sp) {
        AbstractServiceProvider<?> asp = AbstractServiceProvider
                .toAbstractServiceProvider(ServiceBinderDefault.toRootProvider(sp), true).orElseThrow();
        DependenciesInfo deps = asp.dependencies();
        if (deps.allDependencies().isEmpty()) {
            return List.of();
        }

        List<String> plan = new ArrayList<>(deps.allDependencies().size());
        Map<String, HelidonInjectionPlan> injectionPlan = asp.getOrCreateInjectionPlan(false);
        for (Map.Entry<String, HelidonInjectionPlan> e : injectionPlan.entrySet()) {
            StringBuilder line = new StringBuilder();
            InjectionPointInfo ipInfo = e.getValue().injectionPointInfo();
            List<? extends ServiceProvider<?>> ipQualified = e.getValue().injectionPointQualifiedServiceProviders();
            List<?> ipUnqualified = e.getValue().unqualifiedProviders();
            boolean resolved = false;
            try {
                if (ipInfo.listWrapped()) {
                    line.append(".bindMany(");
                } else if (ipQualified.isEmpty()) {
                    if (!ipUnqualified.isEmpty()) {
                        resolved = true;
                        line.append(".resolvedBind(");
                    } else {
                        line.append(".bindVoid(");
                    }
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
                } else if (ipInfo.listWrapped() && !ipQualified.isEmpty()) {
                    line.append(", ").append(toActivatorCodeGen((Collection<ServiceProvider<?>>) ipQualified));
                } else if (!ipQualified.isEmpty()) {
                    line.append(", ").append(toActivatorCodeGen(ipQualified.get(0)));
                }
                line.append(")");

                plan.add(line.toString());
            } catch (Exception exc) {
                throw new IllegalStateException("Failed to process: " + e.getKey() + " with " + e.getValue(), exc);
            }
        }

        return plan;
    }

    /**
     * Perform the file creation and javac it.
     *
     * @param injectionServices   the injection services to use
     * @param req                 the request
     * @param applicationTypeName the application type name
     * @param body                the source code / body to generate
     */
    void codegen(InjectionServices injectionServices,
                 ApplicationCreatorRequest req,
                 TypeName applicationTypeName,
                 String body) {
        CodeGenFiler filer = createDirectCodeGenFiler(req.codeGenPaths().orElse(null), req.analysisOnly());
        Path applicationJavaFilePath = filer.codegenJavaFilerOut(applicationTypeName, body).orElse(null);

        String outputDirectory = req.codeGenPaths().isEmpty()
                ? null : req.codeGenPaths().get().outputPath().orElse(null);
        if (outputDirectory != null) {
            File outDir = new File(outputDirectory);

            // setup meta-inf services
            codegenMetaInfServices(filer,
                                   req.codeGenPaths().orElse(null),
                                   Map.of(TypeNames.INJECT_APPLICATION, List.of(applicationTypeName.name())));

            // setup module-info
            codegenModuleInfoDescriptor(filer, injectionServices, req, applicationTypeName);

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
                    throw new ToolsException("Failed to compile: " + applicationJavaFilePath, e);
                }
            }
        }
    }

    void codegenModuleInfoDescriptor(CodeGenFiler filer,
                                     InjectionServices injectionServices,
                                     ApplicationCreatorRequest req,
                                     TypeName applicationTypeName) {
        Optional<Path> injectionModuleInfoPath = filer.toResourceLocation(ModuleUtils.MODULE_INFO_JAVA_NAME);
        ModuleInfoDescriptor descriptor = filer.readModuleInfo(ModuleUtils.MODULE_INFO_JAVA_NAME).orElse(null);
        if (descriptor != null) {
            Objects.requireNonNull(injectionModuleInfoPath.orElseThrow());
            String moduleName = req.moduleName().orElse(null);
            if (moduleName == null || ModuleInfoDescriptor.DEFAULT_MODULE_NAME.equals(moduleName)) {
                moduleName = descriptor.name();
            }

            TypeName moduleTypeName = moduleServiceTypeOf(injectionServices, moduleName).orElse(null);
            if (moduleTypeName != null) {
                String typePrefix = req.codeGen().classPrefixName();
                ModuleInfoCreatorRequest moduleBuilderRequest = ModuleInfoCreatorRequest.builder()
                        .name(moduleName)
                        .moduleTypeName(moduleTypeName)
                        .applicationTypeName(applicationTypeName)
                        .moduleInfoPath(injectionModuleInfoPath.get().toAbsolutePath().toString())
                        .classPrefixName(typePrefix)
                        .applicationCreated(true)
                        .moduleCreated(false)
                        .build();
                descriptor = createModuleInfo(moduleBuilderRequest);
                filer.codegenModuleInfoFilerOut(descriptor, true);
                return;
            }
        }

        Path realModuleInfoPath = filer.toSourceLocation(ModuleUtils.REAL_MODULE_INFO_JAVA_NAME).orElse(null);
        if (realModuleInfoPath != null && !realModuleInfoPath.toFile().exists()) {
            throw new ToolsException("Expected to find " + realModuleInfoPath
                                             + ". Did the Injection APT run?");
        }
    }

    void codegenMetaInfServices(CodeGenFiler filer,
                                CodeGenPaths paths,
                                Map<String, List<String>> metaInfServices) {
        filer.codegenMetaInfServices(paths, metaInfServices);
    }

    ApplicationCreatorResponse handleError(ApplicationCreatorRequest request,
                                           ToolsException e,
                                           ApplicationCreatorResponse.Builder builder) {
        if (request.throwIfError()) {
            throw e;
        }

        return builder.error(e).success(false).build();
    }

}
