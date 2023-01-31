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
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.Module;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.Services;
import io.helidon.pico.services.DefaultServiceBinder;
import io.helidon.pico.tools.AbstractFilerMsgr;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ApplicationCreator;
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
import io.helidon.pico.tools.ModuleUtils;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.helidon.pico.maven.plugin.Utils.applicationCreator;
import static io.helidon.pico.maven.plugin.Utils.hasValue;
import static io.helidon.pico.maven.plugin.Utils.picoServices;
import static io.helidon.pico.maven.plugin.Utils.toDescriptions;
import static io.helidon.pico.tools.ApplicationCreatorConfigOptions.PermittedProviderType;

/**
 * Abstract base for pico maven plugins responsible for creating application and test application's.
 *
 * @see io.helidon.pico.Application
 * @see io.helidon.pico.tools.ApplicationCreatorConfigOptions
 */
@SuppressWarnings("unused")
public abstract class AbstractApplicationCreatorMojo extends AbstractCreatorMojo {

    /**
     * The file name written to ./target/pico/ to track the last package name generated for this application.
     */
    protected static final String APPLICATION_PACKAGE_FILE_NAME = "pico-app-package-name.txt";

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String targetDir;

    /**
     * The package name to apply. If not found the package name will be inferred.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".package.name", readonly = true)
    private String packageName;

    /**
     * The approach for handling providers.
     * See {@link io.helidon.pico.tools.ApplicationCreatorConfigOptions#permittedProviderTypes()}.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".permitted.provider.types", readonly = true)
    private String permittedProviderTypes;
    private PermittedProviderType permittedProviderType;

    /**
     * Sets the named types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".permitted.provider.type.names", readonly = true)
    private List<String> permittedProviderTypeNames;

    /**
     * Sets the named qualifier types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".permitted.provider.qualifier.type.names", readonly = true)
    private List<String> permittedProviderQualifierTypeNames;

    /**
     * Default constructor.
     */
    protected AbstractApplicationCreatorMojo() {
    }

    /**
     * The project build directory.
     *
     * @return the project build directory
     */
    protected File getProjectBuildTargetDir() {
        return new File(targetDir);
    }

    /**
     * The scratch directory.
     *
     * @return the scratch directory
     */
    protected File getPicoScratchDir() {
        return new File(getProjectBuildTargetDir(), PicoServicesConfig.NAME);
    }

    /**
     * The target package name.
     *
     * @return the target package name
     */
    protected String getPackageName() {
        return packageName;
    }

    String getThisPackageName(
            ServiceProvider<Module> moduleSp,
            Collection<TypeName> typeNames,
            ModuleInfoDescriptor descriptor) {
        File packageFileName = new File(getPicoScratchDir(), APPLICATION_PACKAGE_FILE_NAME);

        String packageName = getPackageName();
        if (packageName == null) {
            // check for the existence of the file...

            if (packageFileName.exists()) {
                try {
                    packageName = Files.readString(packageFileName.toPath(), Charset.defaultCharset());
                } catch (IOException e) {
                    throw new ToolsException("unable to load: " + packageFileName, e);
                }

                if (hasValue(packageName)) {
                    return packageName;
                }
            }

            if (moduleSp != null) {
                packageName = DefaultTypeName.createFromTypeName(moduleSp.serviceInfo().serviceTypeName()).packageName();
            } else {
                packageName = ModuleUtils.toSuggestedGeneratedPackageName(descriptor, typeNames, PicoServicesConfig.NAME);
            }
        }

        // record it to scratch file for later consumption (during test build for example)
        try {
            Files.createDirectories(packageFileName.getParentFile().toPath());
            Files.writeString(packageFileName.toPath(), packageName);
        } catch (IOException e) {
            throw new ToolsException("unable to save: " + packageFileName, e);
        }

        return Objects.requireNonNull(packageName);
    }

    static boolean isUnnamed(
            String moduleName) {
        return !hasValue(moduleName)
                || moduleName.equals(ModuleInfoDescriptor.DEFAULT_MODULE_NAME)
                || moduleName.equals(ModuleInfoDescriptor.DEFAULT_MODULE_NAME + "/" + ModuleInfoDescriptor.DEFAULT_TEST_SUFFIX);
    }

    static PicoException noModuleFoundError() {
        return new PicoException("unnamed to determine the name for the current module - was APT run?");
    }

    static PicoException noModuleFoundError(
            String moduleName) {
        return new PicoException("no pico module named '" + moduleName + "' found in the current module - was APT run?");
    }

    String getThisModuleName() {
        String moduleName = ModuleUtils
                .toSuggestedModuleName(Paths.get("."),
                                       Path.of(getProject().getBuild().getSourceDirectory()), true).orElseThrow();
        if (isUnnamed(moduleName)) {
            throw noModuleFoundError();
        }
        return moduleName;
    }

    ServiceProvider<Module> getThisModule(
            String name,
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
                .anyMatch(p -> new File(p.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME).exists());
    }

    /**
     * Favors the 'test' module-info if available, and falls back to 'main' module-info.
     *
     * @param location the location for the located module-info
     * @return the module-info descriptor to return or null if none is available
     */
    ModuleInfoDescriptor getAnyModuleInfo(
            AtomicReference<File> location) {
        File file = getNonTestSourceRootPaths().stream()
                .map(p -> new File(p.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME))
                .filter(File::exists)
                .findFirst()
                .orElse(null);

        if (file == null) {
            file = getTestSourceRootPaths().stream()
                    .map(p -> new File(p.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME))
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
    public void execute() throws MojoExecutionException {
        getLog().debug("Executing " + getClass().getName() + "...");

        this.permittedProviderType = PermittedProviderType.valueOf(permittedProviderTypes.toUpperCase());

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
            assert (!picoServices.config().usesCompileTimeApplications());
            Services services = picoServices.services();

            // get the application creator only after pico services were initialized (we need to ignore any existing apps)
            ApplicationCreator creator = applicationCreator();

            List<ServiceProvider<Module>> allModules = services
                    .lookupAll(DefaultServiceInfoCriteria.builder().addContractImplemented(Module.class.getName()).build());
            getLog().info("processing modules: " + toDescriptions(allModules));
            if (allModules.isEmpty()) {
                getLog().warn("no modules to process");
            }

            // retrieves all the services in the registry
            List<ServiceProvider<?>> allServices = services
                    .lookupAll(DefaultServiceInfoCriteria.builder().build(), false);
            if (allServices.isEmpty()) {
                ToolsException e = new ToolsException("no services to process");
                getLog().warn(e.getMessage(), e);
                if (isFailOnWarning()) {
                    throw e;
                }
                return;
            }

            Set<TypeName> serviceTypeNames = toNames(allServices);
            serviceTypeNames.removeAll(serviceNamesForExclusion);

            String moduleInfoModuleName = getThisModuleName();
            ServiceProvider<Module> moduleSp = getThisModule(moduleInfoModuleName, services);
            String typeSuffix = getClassPrefixName();
            AtomicReference<File> moduleInfoPathRef = new AtomicReference<>();
            ModuleInfoDescriptor descriptor = getAnyModuleInfo(moduleInfoPathRef);
            String moduleInfoPath = (moduleInfoPathRef.get() != null)
                    ? moduleInfoPathRef.get().getPath()
                    : null;
            String packageName = getThisPackageName(moduleSp, serviceTypeNames, descriptor);

            CodeGenPaths codeGenPaths = DefaultCodeGenPaths.builder()
                    .generatedSourcesPath(getGeneratedSourceDirectory().getPath())
                    .outputPath(getOutputDirectory().getPath())
                    .moduleInfoPath(moduleInfoPath)
                    .build();
            ApplicationCreatorCodeGen applicationCodeGen = DefaultApplicationCreatorCodeGen.builder()
                    .packageName(packageName)
                    .className(getGeneratedClassName())
                    .classPrefixName(typeSuffix)
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
            AbstractFilerMsgr directFiler = AbstractFilerMsgr.createDirectFiler(codeGenPaths, getLogger());
            CodeGenFiler codeGenFiler = new CodeGenFiler(directFiler);
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
            } else if (!isUnnamed(moduleInfoModuleName)) {
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
        } catch (Throwable t) {
            getLog().error("creator failed", t);
            throw new MojoExecutionException("creator failed", t);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    List<TypeName> toTypeNames(
            List<String> permittedProviderQualifierTypeNames) {
        if (permittedProviderQualifierTypeNames == null || permittedProviderQualifierTypeNames.isEmpty()) {
            return List.of();
        }

        return permittedProviderQualifierTypeNames.stream()
                .map(DefaultTypeName::createFromTypeName)
                .collect(Collectors.toList());
    }

    Set<TypeName> toNames(
            List<ServiceProvider<?>> services) {
        Map<TypeName, ServiceProvider<?>> result = new LinkedHashMap<>();
        services.forEach(sp -> {
            sp = DefaultServiceBinder.toRootProvider(sp);
            String serviceType = sp.serviceInfo().serviceTypeName();
            TypeName name = DefaultTypeName.createFromTypeName(serviceType);
            ServiceProvider<?> prev = result.put(name, sp);
            if (prev != null) {
                if (!(prev instanceof ServiceProviderProvider)) {
                    throw new ToolsException("there are two registrations for the same service type: " + prev + " and " + sp);
                }
                getLog().debug("two registrations for the same service type: " + prev + " and " + sp);
            }
        });
        return new TreeSet<>(result.keySet());
    }

}
