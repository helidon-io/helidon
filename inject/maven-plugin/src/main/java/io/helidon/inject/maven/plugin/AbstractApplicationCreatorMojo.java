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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoSourceParser;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.types.TypeName;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Lookup;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.ServiceProviderBindable;
import io.helidon.inject.ServiceProviderProvider;
import io.helidon.inject.Services;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;

import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.helidon.inject.maven.plugin.MavenUtil.toBasePath;
import static io.helidon.inject.maven.plugin.MavenUtil.toSuggestedModuleName;

/**
 * Abstract base for the Injection {@code maven-plugin} responsible for creating {@code Application} and Test
 * {@code Application}'s.
 *
 * @see io.helidon.inject.Application
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public abstract class AbstractApplicationCreatorMojo extends AbstractCreatorMojo {
    /**
     * The default permitted provider type.
     */
    private static final PermittedProviderType DEFAULT_PERMITTED_PROVIDER_TYPE = PermittedProviderType.ALL;
    private static final String MODULE_INFO_FILE = "module-info.java";

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

    /**
     * Returns true if the given module name is unnamed.
     *
     * @param moduleName the module name to check
     * @return true if the provided module name is unnamed
     */
    public static boolean isUnnamedModuleName(String moduleName) {
        return !moduleName.isBlank()
                || moduleName.equals(ModuleInfo.DEFAULT_MODULE_NAME)
                || moduleName.equals(ModuleInfo.DEFAULT_MODULE_NAME + "/test");
    }

    String getThisModuleName() {
        Build build = getProject().getBuild();
        Path basePath = toBasePath(build.getSourceDirectory());
        String moduleName = toSuggestedModuleName(basePath, Path.of(build.getSourceDirectory()), true).orElseThrow();
        if (isUnnamedModuleName(moduleName)) {
            // try to recover it from a previous tooling step
            String appPackageName = loadAppPackageName().orElse(null);
            if (appPackageName == null) {
                getLog().info("Unable to determine the name of the current module - "
                                      + "was APT run and do you have a module-info?");
            } else {
                moduleName = appPackageName;
            }
        }
        return moduleName;
    }

    Optional<ServiceProvider<ModuleComponent>> lookupThisModule(String name,
                                                                Services services) {
        return services.firstProvider(
                Lookup.builder()
                        .addContract(ModuleComponent.class)
                        .addQualifier(Qualifier.createNamed(name))
                        .build());
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
                .anyMatch(p -> new File(p.toFile(), MODULE_INFO_FILE).exists());
    }

    /**
     * Favors the 'test' module-info if available, and falls back to 'main' module-info.
     *
     * @param location the location for the located module-info
     * @return the module-info descriptor to return or null if none is available
     */
    ModuleInfo getAnyModuleInfo(AtomicReference<File> location) {
        File file = getNonTestSourceRootPaths().stream()
                .map(p -> new File(p.toFile(), MODULE_INFO_FILE))
                .filter(File::exists)
                .findFirst()
                .orElse(null);

        if (file == null) {
            file = getTestSourceRootPaths().stream()
                    .map(p -> new File(p.toFile(), MODULE_INFO_FILE))
                    .filter(File::exists)
                    .findFirst()
                    .orElse(null);
        }

        if (file != null && location != null) {
            location.set(file);
            return ModuleInfoSourceParser.parse(file.toPath());
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
                        ? DEFAULT_PERMITTED_PROVIDER_TYPE
                        : PermittedProviderType.valueOf(permittedProviderTypes.toUpperCase());

        // we MUST get the exclusion list prior to building the next loader, since it will reset the service registry
        Set<TypeName> serviceNamesForExclusion = getServiceTypeNamesForExclusion();
        boolean hasModuleInfo = hasModuleInfo();
        Set<Path> modulepath = (hasModuleInfo) ? getModulepathElements() : Collections.emptySet();
        Set<Path> classpath = getClasspathElements();
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = MavenUtil.createClassLoader(classpath, prev);
        AtomicReference<File> moduleInfoPathRef = new AtomicReference<>();
        String moduleInfoPath = (moduleInfoPathRef.get() != null)
                ? moduleInfoPathRef.get().getPath()
                : null;
        ModuleInfo moduleInfo = getAnyModuleInfo(moduleInfoPathRef);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            InjectionServices injectionServices = MavenPluginUtils.injectionServices(false);
            if (injectionServices.config().useApplication()) {
                throw new IllegalStateException("Maven plugin service registry must not be using 'application' bindings");
            }
            Services services = injectionServices.services();

            MavenLogger mavenLogger = MavenLogger.create(getLog(), isFailOnWarning());
            MavenCodegenContext scanContext = MavenCodegenContext.create(MavenOptions.create(toOptions()),
                                                                         scope(),
                                                                         getGeneratedSourceDirectory().toPath(),
                                                                         getOutputDirectory().toPath(),
                                                                         mavenLogger,
                                                                         moduleInfo);

            List<ServiceProvider<Object>> allModules = services
                    .allProviders(Lookup.builder()
                                              .addContract(ModuleComponent.class)
                                              .build());
            getLog().debug("Processing modules: " + MavenPluginUtils.toDescriptions(allModules));

            if (allModules.isEmpty()) {
                warn("Application creator found no modules to process");
            }

            // retrieves all the services in the registry
            List<ServiceProvider<Object>> allServices = services
                    .allProviders(Lookup.EMPTY);
            if (allServices.isEmpty()) {
                warn("Application creator found no services to process");
                return;
            }

            Set<TypeName> serviceTypeNames = toNames(allServices);
            serviceTypeNames.removeAll(serviceNamesForExclusion);

            String moduleInfoModuleName = getThisModuleName();
            Optional<ServiceProvider<ModuleComponent>> moduleSp = lookupThisModule(moduleInfoModuleName, services);

            String packageName = determinePackageName(moduleSp, serviceTypeNames, moduleInfo, getNonTestSourceRootPaths(), true);
            String className = getGeneratedClassName();

            // get the application creator only after services are initialized (we need to ignore any existing apps)
            ApplicationCreator creator = new ApplicationCreator(scanContext, isFailOnError());

            List<String> compilerArgs = getCompilerArgs();
            CompilerOptions compilerOptions = CompilerOptions.builder()
                    .classpath(List.copyOf(classpath))
                    .modulepath(List.copyOf(modulepath))
                    .sourcepath(getSourceRootPaths())
                    .source(getSource())
                    .target(getTarget())
                    .commandLineArguments((compilerArgs != null) ? compilerArgs : List.of())
                    .outputDirectory(getOutputDirectory().toPath())
                    .build();

            creator.createApplication(injectionServices,
                                      serviceTypeNames,
                                      TypeName.create(packageName + "." + className),
                                      compilerOptions);

        } catch (CodegenException e) {
            throw e;
        } catch (Exception e) {
            throw new CodegenException("An error occurred creating the Application in " + getClass().getName(), e);
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

    Set<TypeName> toNames(List<ServiceProvider<Object>> services) {
        Map<TypeName, ServiceProvider<?>> result = new LinkedHashMap<>();
        services.forEach(sp -> {
            ServiceProvider<?> rootProvider = toRootProvider(sp);
            TypeName serviceType = rootProvider.serviceType();
            ServiceProvider<?> prev = result.put(serviceType, rootProvider);
            if (prev != null) {
                if (!(prev instanceof ServiceProviderProvider)) {
                    throw new CodegenException("There are two registrations for the same service type: " + prev + " and " + sp);
                }
                getLog().debug("There are two registrations for the same service type: " + prev + " and " + sp);
            }
        });
        return new TreeSet<>(result.keySet());
    }

    void warn(String msg) {
        getLog().warn(msg);

        if (isFailOnWarning()) {
            throw new CodegenException(msg);
        }
    }

    protected abstract CodegenScope scope();

    /**
     * Returns the root provider of the service provider passed.
     *
     * @param sp the service provider
     * @return the root provider of the service provider, falling back to the service provider passed
     */
    private static ServiceProvider<?> toRootProvider(ServiceProvider<?> sp) {
        Optional<? extends ServiceProviderBindable<?>> bindable = sp.serviceProviderBindable();
        if (bindable.isPresent()) {
            sp = bindable.get().rootProvider().orElse(sp);
        }
        if (sp instanceof ServiceProviderBindable<?> spb) {
            return spb.rootProvider().orElse(sp);
        }
        return sp;
    }

    private Set<String> toOptions() {

        Set<String> options = new HashSet<>(getCompilerArgs());

        String moduleName = moduleName();
        if (moduleName != null) {
            options.add("-A" + CodegenOptions.CODEGEN_MODULE.name() + "=" + moduleName);
        }

        options.add("-A" + ApplicationOptions.PERMITTED_PROVIDER_TYPE + "=" + permittedProviderType);
        if (!permittedProviderTypeNames.isEmpty()) {
            options.add("-A" + ApplicationOptions.PERMITTED_PROVIDER_TYPE_NAMES
                                + "=" + String.join(",", permittedProviderTypeNames));
        }
        if (!permittedProviderQualifierTypeNames.isEmpty()) {
            options.add("-A" + ApplicationOptions.PERMITTED_PROVIDER_QUALIFIER_TYPE_NAMES
                                + "=" + String.join(",", permittedProviderQualifierTypeNames));
        }

        return options;
    }
}
