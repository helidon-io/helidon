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

package io.helidon.inject.maven.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoSourceParser;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.InjectionCodegenContext;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Abstract base for the Injection {@code maven-plugin} responsible for creating {@code Application} and Test
 * {@code Application}'s.
 */
abstract class AbstractApplicationCreatorMojo extends AbstractCreatorMojo {
    private static final String MODULE_COMPONENT_CLASS_FILE_NAME = InjectionCodegenContext.MODULE_NAME + ".class";
    /**
     * The approach for handling providers.
     * See {@code ApplicationCreatorConfigOptions#permittedProviderTypes()}.
     */
    @Parameter(property = "helidon.inject.permitted.provider.types", defaultValue = "ALL")
    private PermittedProviderType permittedProviderType;

    /**
     * The -source argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.source", defaultValue = "21")
    private String source;

    /**
     * The -target argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.target", defaultValue = "21")
    private String target;

    /**
     * Sets the named types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = "helidon.inject.permitted.provider.type.names")
    private List<String> permittedProviderTypeNames;

    /**
     * Sets the named qualifier types permitted for providers, assuming use of
     * {@link PermittedProviderType#NAMED}.
     */
    @Parameter(property = "helidon.inject.permitted.provider.qualifier.type.names", readonly = true)
    private List<String> permittedProviderQualifierTypeNames;

    /**
     * Default constructor.
     */
    protected AbstractApplicationCreatorMojo() {
    }

    @Override
    protected void innerExecute() {

        MavenLogger mavenLogger = MavenLogger.create(getLog(), failOnWarning());

        // we MUST get the exclusion list prior to building the next loader, since it will reset the service registry
        Set<TypeName> serviceNamesForExclusion = serviceTypeNamesForExclusion(mavenLogger);
        getLog().debug("Type names for exclusion: " + serviceNamesForExclusion);

        boolean hasModuleInfo = hasModuleInfo();
        Set<Path> modulepath = hasModuleInfo ? getModulepathElements() : Set.of();
        Set<Path> classpath = getClasspathElements();
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = createClassLoader(classpath, prev);

        Optional<ModuleInfo> nonTestModuleInfo = findModuleInfo(nonTestSourceRootPaths())
                .map(ModuleInfoSourceParser::parse);

        /*
        We may have module info both in sources and in tests
         */
        Optional<ModuleInfo> myModuleInfo = findModuleInfo(sourceRootPaths())
                .map(ModuleInfoSourceParser::parse);
        CodegenOptions codegenOptions = MavenOptions.create(toOptions());
        CodegenScope scope = scope();
        codegenOptions.validate(Set.of(ApplicationOptions.PERMITTED_PROVIDER_TYPE,
                                       ApplicationOptions.PERMITTED_PROVIDER_TYPES,
                                       ApplicationOptions.PERMITTED_PROVIDER_QUALIFIER_TYPES));

        // package name to use (should be the same as ModuleComponent package)
        String packageName = packageName(codegenOptions, myModuleInfo, nonTestModuleInfo);
        // module name to use to define application name (should be the same as ModuleComponent uses for this module)
        String moduleName = moduleName(loader, codegenOptions, myModuleInfo, packageName, scope);

        MavenCodegenContext scanContext = MavenCodegenContext.create(codegenOptions,
                                                                     scope,
                                                                     generatedSourceDirectory(),
                                                                     outputDirectory(),
                                                                     mavenLogger,
                                                                     myModuleInfo.orElse(null));

        try {
            Thread.currentThread().setContextClassLoader(loader);

            try (WrappedServices services = WrappedServices.create(loader, mavenLogger, false)) {
                CompilerOptions compilerOptions = CompilerOptions.builder()
                        .classpath(List.copyOf(classpath))
                        .modulepath(List.copyOf(modulepath))
                        .sourcepath(sourceRootPaths())
                        .source(getSource())
                        .target(getTarget())
                        .commandLineArguments(getCompilerArgs())
                        .outputDirectory(outputDirectory())
                        .build();

                createApplication(scanContext,
                                  services,
                                  compilerOptions,
                                  serviceNamesForExclusion,
                                  moduleName,
                                  packageName);
            } catch (CodegenException e) {
                throw e;
            } catch (Exception e) {
                throw new CodegenException("An error occurred creating the Application in " + getClass().getName(), e);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    protected void createApplication(MavenCodegenContext scanContext,
                                     WrappedServices services,
                                     CompilerOptions compilerOptions,
                                     Set<TypeName> serviceNamesForExclusion,
                                     String moduleName,
                                     String packageName) {

        // retrieves all the services in the registry
        Set<TypeName> allServices = services.all()
                .stream()
                .map(WrappedProvider::toRootProvider)
                .map(WrappedProvider::serviceType)
                .collect(Collectors.toCollection(TreeSet::new));

        if (allServices.isEmpty()) {
            warn("Application creator found no services to process");
            return;
        } else {
            getLog().debug("All services to be processed (no exclusions applied): " + allServices);
        }

        allServices.removeAll(serviceNamesForExclusion);

        getLog().debug("All services to be processed (exclusions applied): " + allServices);

        String className = getGeneratedClassName();

        // get the application creator only after services are initialized (we need to ignore any existing apps)
        ApplicationCreator creator = new ApplicationCreator(scanContext, failOnError());

        creator.createApplication(services,
                                  allServices,
                                  TypeName.create(packageName + "." + className),
                                  moduleName,
                                  compilerOptions);
    }

    /**
     * Where to generate sources. As this directory differs between production code and test code, it must be provided
     * by a subclass.
     *
     * @return where to generate sources
     */
    protected abstract Path generatedSourceDirectory();

    /**
     * Class name to be generated.
     *
     * @return application class name
     */

    protected abstract String getGeneratedClassName();

    /**
     * Output directory for this {@link #scope()}.
     *
     * @return output directory
     */
    protected abstract Path outputDirectory();

    /**
     * Source roots for this {@link #scope()}.
     *
     * @return source roots
     */
    protected List<Path> sourceRootPaths() {
        return nonTestSourceRootPaths();
    }

    /**
     * Production source roots for this project.
     *
     * @return source roots for production code
     */
    protected List<Path> nonTestSourceRootPaths() {
        MavenProject project = mavenProject();
        List<Path> result = new ArrayList<>(project.getCompileSourceRoots().size());
        for (Object a : project.getCompileSourceRoots()) {
            result.add(Path.of(a.toString()));
        }
        return result;
    }

    /**
     * Test source roots for this project.
     *
     * @return source roots for test code
     */
    protected List<Path> testSourceRootPaths() {
        MavenProject project = mavenProject();
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
        return sourceRootPaths()
                .stream()
                .anyMatch(p -> Files.exists(p.resolve(ModuleInfo.FILE_NAME)));
    }

    protected Optional<Path> findModuleInfo(List<Path> sourcePaths) {
        return sourcePaths.stream()
                .map(it -> it.resolve(ModuleInfo.FILE_NAME))
                .filter(Files::exists)
                .findFirst();
    }

    /**
     * @return This represents the set of services that we already code-gen'ed
     */
    protected Set<TypeName> serviceTypeNamesForExclusion(CodegenLogger logger) {
        return Set.of();
    }

    void warn(String msg) {
        getLog().warn(msg);

        if (failOnWarning()) {
            throw new CodegenException(msg);
        }
    }

    protected abstract CodegenScope scope();

    /**
     * Creates a new classloader.
     *
     * @param classPath the classpath to use
     * @param parent    the parent loader
     * @return the loader
     */
    protected URLClassLoader createClassLoader(Collection<Path> classPath,
                                               ClassLoader parent) {
        List<URL> urls = new ArrayList<>(classPath.size());
        for (Path dependency : classPath) {
            try {
                urls.add(dependency.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new CodegenException("Unable to build the classpath. Dependency cannot be converted to URL: "
                                                   + dependency,
                                           e);
            }
        }

        if (parent == null) {
            parent = Thread.currentThread().getContextClassLoader();
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }

    protected String getSource() {
        return source;
    }

    protected String getTarget() {
        return target;
    }

    protected LinkedHashSet<Path> getSourceClasspathElements() {
        MavenProject project = mavenProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>(project.getCompileArtifacts().size());
        result.add(Paths.get(project.getBuild().getOutputDirectory()));
        for (Object a : project.getCompileArtifacts()) {
            result.add(((Artifact) a).getFile().toPath());
        }
        return result;
    }

    /**
     * Provides a convenient way to handle test scope. Returns the classpath for source files (or test sources) only.
     */
    protected LinkedHashSet<Path> getClasspathElements() {
        return getSourceClasspathElements();
    }

    // to dot separated path
    private static String toDotSeparated(Path relativePath) {
        return StreamSupport.stream(relativePath.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("."));
    }

    private String packageName(CodegenOptions codegenOptions,
                               Optional<ModuleInfo> myModuleInfo,
                               Optional<ModuleInfo> srcModuleInfo) {
        return CodegenOptions.CODEGEN_PACKAGE
                .findValue(codegenOptions)
                .or(this::packageFromModuleComponent)
                .or(() -> myModuleInfo.flatMap(this::exportedPackage))
                .or(() -> srcModuleInfo.flatMap(this::exportedPackage))
                .or(this::firstUsedPackage)
                .orElseThrow(() -> new CodegenException("Unable to determine package for application class."));
    }

    private Optional<String> firstUsedPackage() {
        // we expect at least some source code. If none found, try test source, if none found, must be configured
        return firstUsedPackage(nonTestSourceRootPaths())
                .or(() -> firstUsedPackage(testSourceRootPaths()));
    }

    private Optional<String> firstUsedPackage(List<Path> sourceRoots) {
        Set<String> found = new TreeSet<>(Comparator.comparing(String::length));

        for (Path sourceRoot : sourceRoots) {
            try {
                try (Stream<Path> pathStream = Files.walk(sourceRoot)) {
                    pathStream
                            .filter(it -> it.getFileName().toString().endsWith(".java"))
                            .map(it -> packageName(sourceRoot, it))
                            .forEach(found::add);
                }
            } catch (IOException e) {
                getLog().debug("Failed to walk path tree for source root: " + sourceRoot.toAbsolutePath(),
                               e);
            }
        }
        return found.stream()
                .findFirst();
    }

    private Optional<String> exportedPackage(ModuleInfo moduleInfo) {
        Set<String> unqualifiedExports = new TreeSet<>(Comparator.comparing(String::length));
        moduleInfo.exports()
                .forEach((export, to) -> {
                    if (to.isEmpty()) {
                        unqualifiedExports.add(export);
                    }
                });
        return unqualifiedExports.stream().findFirst();
    }

    private String moduleName(ClassLoader loader,
                              CodegenOptions codegenOptions,
                              Optional<ModuleInfo> myModuleInfo,
                              String packageName,
                              CodegenScope scope) {
        return CodegenOptions.CODEGEN_MODULE
                .findValue(codegenOptions)
                .or(() -> myModuleInfo.map(ModuleInfo::name))
                .or(() -> this.moduleFromModuleComponent(loader))
                .orElseGet(() -> "unnamed/"
                        + packageName
                        + (scope.isProduction() ? "" : "/" + scope.name()));
    }

    private Optional<String> packageFromModuleComponent() {
        try (Stream<Path> stream = Files.walk(outputDirectory())) {
            return stream
                    .filter(it -> it.endsWith(MODULE_COMPONENT_CLASS_FILE_NAME))
                    .map(it -> packageName(outputDirectory(), it))
                    .findFirst();
        } catch (IOException e) {
            // ignored, as we do not want to fail just for this reason
            getLog().debug("Failed to find module component in build directory", e);
            return Optional.empty();
        }
    }

    private String packageName(Path rootPath, Path filePath) {
        Path parent = filePath.getParent();
        if (parent == null) {
            return "";
        }
        return toDotSeparated(rootPath.relativize(parent));
    }

    private Optional<String> moduleFromModuleComponent(ClassLoader loader) {
        try (Stream<Path> stream = Files.walk(outputDirectory())) {
            return stream
                    .filter(it -> it.endsWith(InjectionCodegenContext.MODULE_NAME + ".class"))
                    .map(it -> {
                        // instantiate the class, call name() on it
                        String fqn = toDotSeparated(outputDirectory().relativize(it));
                        fqn = fqn.substring(0, fqn.length() - 6); // remove .class suffix
                        try {
                            Class<?> moduleComponentClass = loader.loadClass(fqn);
                            Object moduleComponent = moduleComponentClass.getConstructor()
                                    .newInstance();
                            // there should be a method `String name()`
                            return (String) moduleComponent.getClass().getMethod("name")
                                    .invoke(moduleComponent);
                        } catch (ReflectiveOperationException e) {
                            throw new CodegenException("Failed to instantiate " + fqn + ", needed to get module name", e);
                        }
                    })
                    .findFirst();
        } catch (IOException e) {
            // ignored, as we do not want to fail just for this reason
            getLog().debug("Failed to find module component in build directory", e);
            return Optional.empty();
        }
    }

    private Set<String> toOptions() {

        Set<String> options = new HashSet<>(getCompilerArgs());

        moduleNameFromMavenConfig().ifPresent(it -> options.add("-A" + CodegenOptions.TAG_CODEGEN_MODULE + "=" + it));
        packageNameFromMavenConfig().ifPresent(it -> options.add("-A" + CodegenOptions.TAG_CODEGEN_PACKAGE + "=" + it));

        options.add("-A" + ApplicationOptions.PERMITTED_PROVIDER_TYPE.name() + "=" + permittedProviderType);
        if (!permittedProviderTypeNames.isEmpty()) {
            options.add("-A" + ApplicationOptions.PERMITTED_PROVIDER_TYPES.name()
                                + "=" + String.join(",", permittedProviderTypeNames));
        }
        if (!permittedProviderQualifierTypeNames.isEmpty()) {
            options.add("-A" + ApplicationOptions.PERMITTED_PROVIDER_QUALIFIER_TYPES.name()
                                + "=" + String.join(",", permittedProviderQualifierTypeNames));
        }

        return options;
    }
}
