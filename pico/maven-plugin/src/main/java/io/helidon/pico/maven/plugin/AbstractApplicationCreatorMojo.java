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

package io.helidon.pico.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.pico.maven.plugin.utils.ExecutableClassLoader;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.Services;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.spi.impl.DefaultPicoServicesConfig;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ApplicationCreator;
import io.helidon.pico.tools.creator.ApplicationCreatorCodeGen;
import io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions;
import io.helidon.pico.tools.creator.ApplicationCreatorRequest;
import io.helidon.pico.tools.creator.ApplicationCreatorResponse;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.CompilerOptions;
import io.helidon.pico.tools.creator.impl.DefaultApplicationCreator;
import io.helidon.pico.tools.creator.impl.DefaultApplicationCreatorCodeGen;
import io.helidon.pico.tools.creator.impl.DefaultApplicationCreatorConfigOptions;
import io.helidon.pico.tools.creator.impl.DefaultApplicationCreatorRequest;
import io.helidon.pico.tools.creator.impl.DefaultGeneralCodeGenPaths;
import io.helidon.pico.tools.creator.impl.DefaultGeneralCompilerOptions;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.ModuleUtils;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Abstract base for pico maven plugins responsible for creating application and test application's.
 *
 * @see io.helidon.pico.Application
 * @see ApplicationCreatorConfigOptions
 */
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
     * See {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions#getPermittedProviderTypes()}.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".permitted.provider.types", readonly = true)
    private String permittedProviderTypes;
    private ApplicationCreatorConfigOptions.PermittedProviderType permittedProviderType;

    /**
     * Sets the named types permitted for providers, assuming use of
     * {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions.PermittedProviderType#NAMED}.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".permitted.provider.type.names", readonly = true)
    private List<String> permittedProviderTypeNames;

    /**
     * Sets the named qualifier types permitted for providers, assuming use of
     * {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions.PermittedProviderType#NAMED}.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".permitted.provider.qualifier.type.names", readonly = true)
    private List<String> permittedProviderQualifierTypeNames;

    protected File getProjectBuildTargetDir() {
        return new File(targetDir);
    }

    protected File getPicoScratchDir() {
        return new File(getProjectBuildTargetDir(), PicoServicesConfig.NAME);
    }

    protected String getPackageName() {
        return packageName;
    }

    @Override
    public void execute() throws MojoExecutionException {
        getLog().debug("Executing " + getClass().getName() + "...");

        this.permittedProviderType = ApplicationCreatorConfigOptions.toPermittedProviderTypes(permittedProviderTypes);

        // we MUST get the exclusion list prior to building the next loader, since it will reset the services registry.
        final Set<TypeName> serviceNamesForExclusion = getServiceTypeNamesForExclusion();
        final boolean hasModuleInfo = hasModuleInfo();
        final Set<Path> modulepath = (hasModuleInfo) ? getModulepathElements() : Collections.emptySet();
        final Set<Path> classpath = getClasspathElements();
        final ClassLoader prev = Thread.currentThread().getContextClassLoader();
        final URLClassLoader loader = ExecutableClassLoader.create(classpath, prev);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            DefaultApplicationCreator creator = (DefaultApplicationCreator) HelidonServiceLoader.create(
                    ServiceLoader.load(ApplicationCreator.class)).iterator().next();

            // we have to reset here since we most likely have new services found in this loader/context...
            PicoServices picoServices = CommonUtils.safeGetPicoRefServices(true);
            ((DefaultPicoServicesConfig) picoServices.config().get())
                    .setValue(DefaultPicoServicesConfig.KEY_BIND_APPLICATION, false);
            Services services = picoServices.services();

            List<ServiceProvider<Module>> allModules = services
                    .lookup(DefaultServiceInfo.builder().contractImplemented(Module.class.getName()).build());
            getLog().info("processing modules: " + ServiceProvider.toDescriptions(allModules));

            // retrieves all the services in the registry...
            List<ServiceProvider<Object>> allServices = services
                    .lookup(DefaultServiceInfo.builder().build(), false);
            if (allServices.isEmpty()) {
                ToolsException e = new ToolsException("No services to process");
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
            SimpleModuleDescriptor descriptor = getAnyModuleInfo(moduleInfoPathRef);
            String moduleInfoPath = (Objects.nonNull(moduleInfoPathRef.get()))
                    ? moduleInfoPathRef.get().getPath()
                    : null;
            String packageName = getThisPackageName(moduleSp, serviceTypeNames, descriptor);

            CodeGenPaths paths = DefaultGeneralCodeGenPaths.builder()
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
            CompilerOptions compilerOptions = DefaultGeneralCompilerOptions.builder()
                    .classpath(classpath)
                    .modulepath(modulepath)
                    .sourcepath(getSourceRootPaths())
                    .source(getSource())
                    .target(getTarget())
                    .commandLineArguments(Objects.nonNull(compilerArgs) ? compilerArgs : Collections.emptyList())
                    .build();
            ApplicationCreatorConfigOptions configOptions = DefaultApplicationCreatorConfigOptions.builder()
                    .permittedProviderTypes(permittedProviderType)
                    .permittedProviderNames(permittedProviderTypeNames)
                    .permittedProviderQualifierTypeNames(toTypeNames(permittedProviderQualifierTypeNames))
                    .build();
            String moduleName = getModuleName();
            ApplicationCreatorRequest req = (ApplicationCreatorRequest) DefaultApplicationCreatorRequest.builder()
                    .codeGenRequest(applicationCodeGen)
                    .messager(new Messager2LogAdapter())
                    .configOptions(configOptions)
                    .serviceTypeNames(serviceTypeNames)
                    .codeGenPaths(paths)
                    .compilerOptions(compilerOptions)
                    .failOnError(isFailOnError())
                    .generator(getClass().getName())
                    .templateName(getTemplateName())
                    .moduleName(Objects.nonNull(moduleName) ? moduleName : moduleInfoModuleName)
                    .build();
            ApplicationCreatorResponse res = creator.createApplication(req);
            if (res.isSuccess()) {
                getLog().debug("processed service type names: " + res.getServiceTypeNames());
                if (getLog().isDebugEnabled()) {
                    getLog().debug("response: " + res);
                }
            } else {
                getLog().error("failed to process", res.getError());
            }
        } catch (Throwable t) {
            getLog().error("creator failed", t);
            throw new MojoExecutionException("creator failed", t);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    protected List<TypeName> toTypeNames(List<String> permittedProviderQualifierTypeNames) {
        if (Objects.isNull(permittedProviderQualifierTypeNames) || permittedProviderQualifierTypeNames.isEmpty()) {
            return Collections.emptyList();
        }

        return permittedProviderQualifierTypeNames.stream()
                .map(DefaultTypeName::createFromTypeName)
                .collect(Collectors.toList());
    }

    protected Set<TypeName> toNames(List<ServiceProvider<Object>> services) {
        Map<TypeName, ServiceProvider<Object>> result = new LinkedHashMap<>();
        services.forEach(sp -> {
            sp = ServiceProviderBindable.toRootProvider(sp);
            String serviceType = sp.serviceInfo().serviceTypeName();
            TypeName name = DefaultTypeName.createFromTypeName(serviceType);
            ServiceProvider<Object> prev = result.put(name, sp);
            if (Objects.nonNull(prev)) {
                if (!(prev instanceof ServiceProviderProvider)) {
                    throw new ToolsException("there are two registrations for the same service type: " + prev + " and " + sp);
                }
                getLog().debug("there are two registrations for the same service type: " + prev + " and " + sp);
            }
        });
        return new TreeSet<>(result.keySet());
    }

    protected String getThisPackageName(ServiceProvider<Module> moduleSp,
                                        Collection<TypeName> typeNames,
                                        SimpleModuleDescriptor descriptor) {
        final File packageFileName = new File(getPicoScratchDir(), APPLICATION_PACKAGE_FILE_NAME);

        String packageName = getPackageName();
        if (Objects.isNull(packageName)) {
            // check for the existence of the file...

            if (packageFileName.exists()) {
                try {
                    packageName = Files.readString(packageFileName.toPath(), Charset.defaultCharset());
                } catch (IOException e) {
                    throw new ToolsException("unable to load: " + packageFileName, e);
                }

                if (AnnotationAndValue.hasNonBlankValue(packageName)) {
                    return packageName;
                }
            }

            if (Objects.nonNull(moduleSp)) {
                packageName = DefaultTypeName.createFromTypeName(moduleSp.serviceInfo().serviceTypeName())
                        .packageName();
            } else {
                packageName = ModuleUtils
                        .getSuggestedGeneratedPackageName(descriptor, typeNames, PicoServicesConfig.NAME);
            }
        }

        // record it to scratch file for later consumption (during test build for example)...
        try {
            packageFileName.getParentFile().mkdirs();
            Files.writeString(packageFileName.toPath(), packageName);
        } catch (IOException e) {
            throw new ToolsException("unable to save: " + packageFileName, e);
        }

        return Objects.requireNonNull(packageName);
    }

    protected String getThisModuleName() {
        return ModuleUtils
                .toSuggestedModuleName(null,
                                       Path.of(getProject().getBuild().getSourceDirectory()), true);
    }

    protected ServiceProvider<Module> getThisModule(String name, Services services) {
        return services.lookupFirst(Module.class, name, false);
    }

    protected String getClassPrefixName() {
        return null;
    }

    protected abstract String getGeneratedClassName();

    protected abstract File getOutputDirectory();

    protected List<Path> getSourceRootPaths() {
        return getNonTestSourceRootPaths();
    }

    protected List<Path> getNonTestSourceRootPaths() {
        MavenProject project = getProject();
        List<Path> result = new ArrayList<>(project.getCompileSourceRoots().size());
        for (Object a : project.getCompileSourceRoots()) {
            result.add(Path.of(a.toString()));
        }
        return result;
    }

    protected List<Path> getTestSourceRootPaths() {
        MavenProject project = getProject();
        List<Path> result = new ArrayList<>(project.getTestCompileSourceRoots().size());
        for (Object a : project.getTestCompileSourceRoots()) {
            result.add(Path.of(a.toString()));
        }
        return result;
    }

    protected LinkedHashSet<Path> getModulepathElements() {
        return getSourceClasspathElements();
    }

    protected boolean hasModuleInfo() {
        return getSourceRootPaths().stream()
                .anyMatch(p -> new File(p.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME).exists());
    }

    /**
     * Favors the 'test' module-info if available, and falls back to 'main' module-info.
     *
     * @param location the location for the located module-info
     * @return the module-info descriptor to return or null if none is available.
     */
    protected SimpleModuleDescriptor getAnyModuleInfo(AtomicReference<File> location) {
        File file = getNonTestSourceRootPaths().stream()
                .map(p -> new File(p.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME))
                .filter(File::exists)
                .findFirst()
                .orElse(null);

        if (Objects.isNull(file)) {
            file = getTestSourceRootPaths().stream()
                    .map(p -> new File(p.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME))
                    .filter(File::exists)
                    .findFirst()
                    .orElse(null);
        }

        if (Objects.nonNull(file) && Objects.nonNull(location)) {
            location.set(file);
            return SimpleModuleDescriptor.uncheckedLoad(file);
        }

        return null;
    }

    /**
     * @return This represents the set of services that we already code-gen'ed
     */
    protected Set<TypeName> getServiceTypeNamesForExclusion() {
        getLog().info("excluding service type names: []");
        return Collections.emptySet();
    }

}
