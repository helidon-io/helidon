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

package io.helidon.pico.maven.plugin;

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

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.pico.api.CallingContext;
import io.helidon.pico.api.CallingContextFactory;
import io.helidon.pico.api.DefaultCallingContext;
import io.helidon.pico.api.DefaultServiceInfoCriteria;
import io.helidon.pico.api.Module;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.ServiceProviderProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.runtime.DefaultServiceBinder;
import io.helidon.pico.tools.AbstractFilerMessager;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ApplicationCreatorCodeGen;
import io.helidon.pico.tools.ApplicationCreatorConfigOptions;
import io.helidon.pico.tools.ApplicationCreatorRequest;
import io.helidon.pico.tools.ApplicationCreatorResponse;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CodeGenPaths;
import io.helidon.pico.tools.CompilerOptions;
import io.helidon.pico.tools.DefaultApplicationCreatorCodeGen;
import io.helidon.pico.tools.DefaultApplicationCreatorConfigOptions;
import io.helidon.pico.tools.DefaultApplicationCreatorRequest;
import io.helidon.pico.tools.DefaultCodeGenPaths;
import io.helidon.pico.tools.DefaultCompilerOptions;
import io.helidon.pico.tools.ModuleInfoDescriptor;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.spi.ApplicationCreator;

import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.helidon.pico.api.CallingContext.globalCallingContext;
import static io.helidon.pico.api.CallingContext.toErrorMessage;
import static io.helidon.pico.maven.plugin.MavenPluginUtils.applicationCreator;
import static io.helidon.pico.maven.plugin.MavenPluginUtils.hasValue;
import static io.helidon.pico.maven.plugin.MavenPluginUtils.picoServices;
import static io.helidon.pico.maven.plugin.MavenPluginUtils.toDescriptions;
import static io.helidon.pico.tools.ApplicationCreatorConfigOptions.PermittedProviderType;
import static io.helidon.pico.tools.ModuleUtils.REAL_MODULE_INFO_JAVA_NAME;
import static io.helidon.pico.tools.ModuleUtils.isUnnamedModuleName;
import static io.helidon.pico.tools.ModuleUtils.toBasePath;
import static io.helidon.pico.tools.ModuleUtils.toSuggestedModuleName;
import static java.util.Optional.ofNullable;

/**
 * Abstract base for the {@code Pico maven-plugin} responsible for creating {@code Application} and Test {@code Application}'s.
 *
 * @see io.helidon.pico.api.Application
 * @see io.helidon.pico.tools.ApplicationCreatorConfigOptions
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class AbstractApplicationCreatorMojo extends AbstractCreatorMojo {

    /**
     * The approach for handling providers.
     * See {@link io.helidon.pico.tools.ApplicationCreatorConfigOptions#permittedProviderTypes()}.
     */
    @Parameter(property = PicoServicesConfig.NAME + ".permitted.provider.types", readonly = true)
    private String permittedProviderTypes;
    private PermittedProviderType permittedProviderType;

    /**
     * Sets the named types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = PicoServicesConfig.NAME + ".permitted.provider.type.names", readonly = true)
    private List<String> permittedProviderTypeNames;

    /**
     * Sets the named qualifier types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = PicoServicesConfig.NAME + ".permitted.provider.qualifier.type.names", readonly = true)
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
        return new ToolsException("No " + PicoServicesConfig.NAME + " module named '" + moduleName
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
                // throw noModuleFoundError();
                getLog().warn(noModuleFoundError().getMessage());
            } else {
                moduleName = appPackageName;
            }
        }
        return moduleName;
    }

    ServiceProvider<Module> lookupThisModule(String name,
                                             Services services) {
        return services.lookupFirst(Module.class, name, false).orElseThrow(() -> noModuleFoundError(name));
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
        this.permittedProviderType = PermittedProviderType.valueOf(permittedProviderTypes.toUpperCase());

        CallingContext callCtx = null;
        Optional<DefaultCallingContext.Builder> callingContextBuilder =
                CallingContextFactory.createBuilder(false);
        if (callingContextBuilder.isPresent()) {
            callingContextBuilder.get().moduleName(Optional.ofNullable(getThisModuleName()));
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

            PicoServices picoServices = picoServices(false);
            if (picoServices.config().usesCompileTimeApplications()) {
                String desc = "should not be using 'application' bindings";
                String msg = (callCtx == null) ? toErrorMessage(desc) : toErrorMessage(callCtx, desc);
                throw new IllegalStateException(msg);
            }
            Services services = picoServices.services();

            // get the application creator only after pico services were initialized (we need to ignore any existing apps)
            ApplicationCreator creator = applicationCreator();

            List<ServiceProvider<Module>> allModules = services
                    .lookupAll(DefaultServiceInfoCriteria.builder().addContractImplemented(Module.class.getName()).build());
            getLog().info("processing modules: " + toDescriptions(allModules));
            if (allModules.isEmpty()) {
                warn("no modules to process");
            }

            // retrieves all the services in the registry
            List<ServiceProvider<?>> allServices = services
                    .lookupAll(DefaultServiceInfoCriteria.builder().build(), false);
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
            ServiceProvider<Module> moduleSp = lookupThisModule(moduleInfoModuleName, services);
            String packageName = determinePackageName(Optional.ofNullable(moduleSp), serviceTypeNames, descriptor, true);

            CodeGenPaths codeGenPaths = DefaultCodeGenPaths.builder()
                    .generatedSourcesPath(getGeneratedSourceDirectory().getPath())
                    .outputPath(getOutputDirectory().getPath())
                    .moduleInfoPath(ofNullable(moduleInfoPath))
                    .build();
            ApplicationCreatorCodeGen applicationCodeGen = DefaultApplicationCreatorCodeGen.builder()
                    .packageName(packageName)
                    .className(getGeneratedClassName())
                    .classPrefixName(classPrefixName)
                    .build();
            List<String> compilerArgs = getCompilerArgs();
            CompilerOptions compilerOptions = DefaultCompilerOptions.builder()
                    .classpath(classpath)
                    .modulepath(modulepath)
                    .sourcepath(getSourceRootPaths())
                    .source(getSource())
                    .target(getTarget())
                    .commandLineArguments((compilerArgs != null) ? compilerArgs : List.of())
                    .build();
            ApplicationCreatorConfigOptions configOptions = DefaultApplicationCreatorConfigOptions.builder()
                    .permittedProviderTypes(permittedProviderType)
                    .permittedProviderNames(permittedProviderTypeNames)
                    .permittedProviderQualifierTypeNames(toTypeNames(permittedProviderQualifierTypeNames))
                    .build();
            String moduleName = getModuleName();
            AbstractFilerMessager directFiler = AbstractFilerMessager.createDirectFiler(codeGenPaths, getLogger());
            CodeGenFiler codeGenFiler = CodeGenFiler.create(directFiler);
            DefaultApplicationCreatorRequest.Builder reqBuilder = DefaultApplicationCreatorRequest.builder()
                    .codeGen(applicationCodeGen)
                    .messager(new Messager2LogAdapter())
                    .filer(codeGenFiler)
                    .configOptions(configOptions)
                    .serviceTypeNames(serviceTypeNames)
                    .codeGenPaths(codeGenPaths)
                    .compilerOptions(compilerOptions)
                    .throwIfError(isFailOnError())
                    .generator(getClass().getName())
                    .templateName(getTemplateName());
            if (hasValue(moduleName)) {
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
            throw new ToolsException("An error occurred creating the " + PicoServicesConfig.NAME + " Application", e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    List<TypeName> toTypeNames(List<String> permittedProviderQualifierTypeNames) {
        if (permittedProviderQualifierTypeNames == null || permittedProviderQualifierTypeNames.isEmpty()) {
            return List.of();
        }

        return permittedProviderQualifierTypeNames.stream()
                .map(DefaultTypeName::createFromTypeName)
                .collect(Collectors.toList());
    }

    Set<TypeName> toNames(List<ServiceProvider<?>> services) {
        Map<TypeName, ServiceProvider<?>> result = new LinkedHashMap<>();
        services.forEach(sp -> {
            sp = DefaultServiceBinder.toRootProvider(sp);
            String serviceType = sp.serviceInfo().serviceTypeName();
            TypeName name = DefaultTypeName.createFromTypeName(serviceType);
            ServiceProvider<?> prev = result.put(name, sp);
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
        Optional<DefaultCallingContext.Builder> optBuilder = CallingContextFactory.createBuilder(false);
        CallingContext callCtx = (optBuilder.isPresent())
                ? optBuilder.get().moduleName(Optional.ofNullable(getThisModuleName())).build() : null;
        String desc = "no modules to process";
        String ctxMsg = (callCtx == null) ? CallingContext.toErrorMessage(desc) : CallingContext.toErrorMessage(callCtx, desc);
        ToolsException e = new ToolsException(ctxMsg);
        if (PicoServices.isDebugEnabled()) {
            getLog().warn(e.getMessage(), e);
        } else {
            getLog().warn(e.getMessage());
        }
        if (isFailOnWarning()) {
            throw e;
        }
    }

}
