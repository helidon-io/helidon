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

package io.helidon.inject.maven.plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.CallingContext;
import io.helidon.inject.api.CallingContextFactory;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.ServiceProviderProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.runtime.ServiceBinderDefault;
import io.helidon.inject.tools.AbstractFilerMessager;
import io.helidon.inject.tools.ActivatorCreatorCodeGen;
import io.helidon.inject.tools.ApplicationCreatorCodeGen;
import io.helidon.inject.tools.ApplicationCreatorConfigOptions;
import io.helidon.inject.tools.ApplicationCreatorRequest;
import io.helidon.inject.tools.ApplicationCreatorResponse;
import io.helidon.inject.tools.CodeGenFiler;
import io.helidon.inject.tools.CodeGenPaths;
import io.helidon.inject.tools.CompilerOptions;
import io.helidon.inject.tools.ModuleInfoDescriptor;
import io.helidon.inject.tools.PermittedProviderType;
import io.helidon.inject.tools.ToolsException;
import io.helidon.inject.tools.spi.ApplicationCreator;

import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.helidon.inject.api.CallingContextFactory.globalCallingContext;
import static io.helidon.inject.runtime.InjectionExceptions.toErrorMessage;
import static io.helidon.inject.tools.ModuleUtils.REAL_MODULE_INFO_JAVA_NAME;
import static io.helidon.inject.tools.ModuleUtils.isUnnamedModuleName;
import static io.helidon.inject.tools.ModuleUtils.toBasePath;
import static io.helidon.inject.tools.ModuleUtils.toSuggestedModuleName;
import static java.util.Optional.ofNullable;

/**
 * Abstract base for the Injection {@code maven-plugin} responsible for creating {@code Application} and Test {@code Application}'s.
 *
 * @see Application
 * @see ApplicationCreatorConfigOptions
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class AbstractApplicationCreatorMojo extends AbstractCreatorMojo {

    /**
     * The approach for handling providers.
     * See {@code ApplicationCreatorConfigOptions#permittedProviderTypes()}.
     */
    @Parameter(property = "inject.permitted.provider.types", readonly = true)
    private String permittedProviderTypes;
    private PermittedProviderType permittedProviderType;

    /**
     * Sets the named types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = "inject.permitted.provider.type.names", readonly = true)
    private List<String> permittedProviderTypeNames;

    /**
     * Sets the named qualifier types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = "inject.permitted.provider.qualifier.type.names", readonly = true)
    private List<String> permittedProviderQualifierTypeNames;

    /**
     * Default constructor.
     */
    protected AbstractApplicationCreatorMojo() {
    }

    static ToolsException noModuleFoundError() {
        return new ToolsException("Unable to determine the name of the current module - "
                                          + "was APT run and do you have a module-info?");
    }

    static ToolsException noModuleFoundError(String moduleName) {
        return new ToolsException("No Injection module named '" + moduleName
                                          + "' was found in the current module - was APT run?");
    }

    String getThisModuleName() {
        Build build = getProject().getBuild();
        Path basePath = toBasePath(build.getSourceDirectory());
        String moduleName = toSuggestedModuleName(basePath, Path.of(build.getSourceDirectory()), true).orElseThrow();
        if (isUnnamedModuleName(moduleName)) {
            // try to recover it from a previous tooling step
            String appPackageName = loadAppPackageName().orElse(null);
            if (appPackageName == null) {
                getLog().info(noModuleFoundError().getMessage());
            } else {
                moduleName = appPackageName;
            }
        }
        return moduleName;
    }

    Optional<ServiceProvider<ModuleComponent>> lookupThisModule(String name,
                                                                Services services,
                                                                boolean expected) {
        Optional<ServiceProvider<ModuleComponent>> result = services.lookupFirst(ModuleComponent.class, name, false);
        if (result.isEmpty() && expected) {
            throw noModuleFoundError(name);
        }
        return result;
    }

    String getClassPrefixName() {
        return ActivatorCreatorCodeGen.DEFAULT_CLASS_PREFIX_NAME;
    }

    abstract String getGeneratedClassName();

    abstract File getOutputDirectory();

    List<Path> getSourceRootPaths() {
        return getNonTestSourceRootPaths();
    }

    List<Path> getNonTestSourceRootPaths() {
        MavenProject project = getProject();
        List<Path> result = new ArrayList<>(project.getCompileSourceRoots().size());
        for (Object a : project.getCompileSourceRoots()) {
            result.add(Path.of(a.toString()));
        }
        return result;
    }

    List<Path> getTestSourceRootPaths() {
        MavenProject project = getProject();
        List<Path> result = new ArrayList<>(project.getTestCompileSourceRoots().size());
        for (Object a : project.getTestCompileSourceRoots()) {
            result.add(Path.of(a.toString()));
        }
        return result;
    }

    LinkedHashSet<Path> getModulepathElements() {
        return getSourceClasspathElements();
    }

    boolean hasModuleInfo() {
        return getSourceRootPaths().stream()
                .anyMatch(p -> new File(p.toFile(), REAL_MODULE_INFO_JAVA_NAME).exists());
    }

    /**
     * Favors the 'test' module-info if available, and falls back to 'main' module-info.
     *
     * @param location the location for the located module-info
     * @return the module-info descriptor to return or null if none is available
     */
    ModuleInfoDescriptor getAnyModuleInfo(AtomicReference<File> location) {
        File file = getNonTestSourceRootPaths().stream()
                .map(p -> new File(p.toFile(), REAL_MODULE_INFO_JAVA_NAME))
                .filter(File::exists)
                .findFirst()
                .orElse(null);

        if (file == null) {
            file = getTestSourceRootPaths().stream()
                    .map(p -> new File(p.toFile(), REAL_MODULE_INFO_JAVA_NAME))
                    .filter(File::exists)
                    .findFirst()
                    .orElse(null);
        }

        if (file != null && location != null) {
            location.set(file);
            return ModuleInfoDescriptor.create(file.toPath());
        }

        return null;
    }

    /**
     * @return This represents the set of services that we already code-gen'ed
     */
    Set<TypeName> getServiceTypeNamesForExclusion() {
        getLog().info("excluding service type names: []");
        return Set.of();
    }

    @Override
    protected void innerExecute() {
        this.permittedProviderType =
                (permittedProviderTypes == null || permittedProviderTypes.isBlank())
                        ? ApplicationCreatorConfigOptions.DEFAULT_PERMITTED_PROVIDER_TYPE
                        : PermittedProviderType.valueOf(permittedProviderTypes.toUpperCase());

        CallingContext callCtx = null;
        Optional<CallingContext.Builder> callingContextBuilder =
                CallingContextFactory.createBuilder(false);
        if (callingContextBuilder.isPresent()) {
            callingContextBuilder.get()
                    .update(it -> Optional.ofNullable(getThisModuleName()).ifPresent(it::moduleName));
            callCtx = callingContextBuilder.get().build();
            globalCallingContext(callCtx, true);
        }

        // we MUST get the exclusion list prior to building the next loader, since it will reset the service registry
        Set<TypeName> serviceNamesForExclusion = getServiceTypeNamesForExclusion();
        boolean hasModuleInfo = hasModuleInfo();
        Set<Path> modulepath = (hasModuleInfo) ? getModulepathElements() : Collections.emptySet();
        Set<Path> classpath = getClasspathElements();
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = ExecutableClassLoader.create(classpath, prev);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            InjectionServices injectionServices = MavenPluginUtils.injectionServices(false);
            if (injectionServices.config().usesCompileTimeApplications()) {
                String desc = "Should not be using 'application' bindings";
                String msg = (callCtx == null) ? toErrorMessage(desc) : toErrorMessage(callCtx, desc);
                throw new IllegalStateException(msg);
            }
            Services services = injectionServices.services();

            // get the application creator only after services are initialized (we need to ignore any existing apps)
            ApplicationCreator creator = MavenPluginUtils.applicationCreator();

            List<ServiceProvider<?>> allModules = services
                    .lookupAll(ServiceInfoCriteria.builder()
                                       .addContractImplemented(ModuleComponent.class)
                                       .build());
            if (InjectionServices.isDebugEnabled()) {
                getLog().info("processing modules: " + MavenPluginUtils.toDescriptions(allModules));
            } else {
                getLog().debug("processing modules: " + MavenPluginUtils.toDescriptions(allModules));
            }
            if (allModules.isEmpty()) {
                warn("No modules to process");
            }

            // retrieves all the services in the registry
            List<ServiceProvider<?>> allServices = services
                    .lookupAll(ServiceInfoCriteria.builder()
                                       .includeIntercepted(true)
                                       .build(), false);
            if (allServices.isEmpty()) {
                warn("no services to process");
                return;
            }

            Set<TypeName> serviceTypeNames = toNames(allServices);
            serviceTypeNames.removeAll(serviceNamesForExclusion);

            String classPrefixName = getClassPrefixName();
            AtomicReference<File> moduleInfoPathRef = new AtomicReference<>();
            ModuleInfoDescriptor descriptor = getAnyModuleInfo(moduleInfoPathRef);
            String moduleInfoPath = (moduleInfoPathRef.get() != null)
                    ? moduleInfoPathRef.get().getPath()
                    : null;
            String moduleInfoModuleName = getThisModuleName();
            Optional<ServiceProvider<ModuleComponent>> moduleSp = lookupThisModule(moduleInfoModuleName, services, false);
            String packageName = determinePackageName(moduleSp, serviceTypeNames, descriptor, true);

            CodeGenPaths codeGenPaths = CodeGenPaths.builder()
                    .generatedSourcesPath(getGeneratedSourceDirectory().getPath())
                    .outputPath(getOutputDirectory().getPath())
                    .update(it -> ofNullable(moduleInfoPath).ifPresent(it::moduleInfoPath))
                    .build();
            ApplicationCreatorCodeGen applicationCodeGen = ApplicationCreatorCodeGen.builder()
                    .packageName(packageName)
                    .className(getGeneratedClassName())
                    .classPrefixName(classPrefixName)
                    .build();
            List<String> compilerArgs = getCompilerArgs();
            CompilerOptions compilerOptions = CompilerOptions.builder()
                    .classpath(List.copyOf(classpath))
                    .modulepath(List.copyOf(modulepath))
                    .sourcepath(getSourceRootPaths())
                    .source(getSource())
                    .target(getTarget())
                    .commandLineArguments((compilerArgs != null) ? compilerArgs : List.of())
                    .build();
            ApplicationCreatorConfigOptions configOptions = ApplicationCreatorConfigOptions.builder()
                    .permittedProviderTypes(permittedProviderType)
                    .permittedProviderNames(Set.copyOf(permittedProviderTypeNames))
                    .permittedProviderQualifierTypeNames(Set.copyOf(toTypeNames(permittedProviderQualifierTypeNames)))
                    .build();
            String moduleName = getModuleName();
            AbstractFilerMessager directFiler = AbstractFilerMessager.createDirectFiler(codeGenPaths, getLogger());
            CodeGenFiler codeGenFiler = CodeGenFiler.create(directFiler);
            ApplicationCreatorRequest.Builder reqBuilder = ApplicationCreatorRequest.builder()
                    .codeGen(applicationCodeGen)
                    .messager(new Messager2LogAdapter())
                    .filer(codeGenFiler)
                    .configOptions(configOptions)
                    .serviceTypeNames(List.copyOf(serviceTypeNames))
                    .generatedServiceTypeNames(List.copyOf(serviceTypeNames))
                    .codeGenPaths(codeGenPaths)
                    .compilerOptions(compilerOptions)
                    .throwIfError(isFailOnError())
                    .generator(getClass().getName())
                    .templateName(getTemplateName());
            if (MavenPluginUtils.hasValue(moduleName)) {
                reqBuilder.moduleName(moduleName);
            } else if (!isUnnamedModuleName(moduleInfoModuleName)) {
                reqBuilder.moduleName(moduleInfoModuleName);
            }
            ApplicationCreatorRequest req = reqBuilder.build();
            ApplicationCreatorResponse res = creator.createApplication(req);
            if (res.success()) {
                getLog().debug("processed service type names: " + res.serviceTypeNames());
                if (getLog().isDebugEnabled()) {
                    getLog().debug("response: " + res);
                }
            } else {
                getLog().error("failed to process", res.error().orElse(null));
            }
        } catch (Exception e) {
            throw new ToolsException("An error occurred creating the Application in " + getClass().getName(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    List<TypeName> toTypeNames(List<String> permittedProviderQualifierTypeNames) {
        if (permittedProviderQualifierTypeNames == null || permittedProviderQualifierTypeNames.isEmpty()) {
            return List.of();
        }

        return permittedProviderQualifierTypeNames.stream()
                .map(TypeName::create)
                .collect(Collectors.toList());
    }

    Set<TypeName> toNames(List<ServiceProvider<?>> services) {
        Map<TypeName, ServiceProvider<?>> result = new LinkedHashMap<>();
        services.forEach(sp -> {
            sp = ServiceBinderDefault.toRootProvider(sp);
            TypeName serviceType = sp.serviceInfo().serviceTypeName();
            ServiceProvider<?> prev = result.put(serviceType, sp);
            if (prev != null) {
                if (!(prev instanceof ServiceProviderProvider)) {
                    throw new ToolsException("There are two registrations for the same service type: " + prev + " and " + sp);
                }
                getLog().debug("There are two registrations for the same service type: " + prev + " and " + sp);
            }
        });
        return new TreeSet<>(result.keySet());
    }

    void warn(String msg) {
        Optional<CallingContext.Builder> optBuilder = CallingContextFactory.createBuilder(false);
        CallingContext callCtx = optBuilder.map(builder -> builder
                .update(it -> Optional.ofNullable(getThisModuleName()).ifPresent(it::moduleName)))
                .map(CallingContext.Builder::build)
                .orElse(null);
        String desc = "no modules to process";
        String ctxMsg = (callCtx == null) ? toErrorMessage(desc) : toErrorMessage(callCtx, desc);
        ToolsException e = new ToolsException(ctxMsg);
        if (InjectionServices.isDebugEnabled()) {
            getLog().warn(e.getMessage(), e);
        } else {
            getLog().warn(e.getMessage());
        }
        if (isFailOnWarning()) {
            throw e;
        }
    }

}
