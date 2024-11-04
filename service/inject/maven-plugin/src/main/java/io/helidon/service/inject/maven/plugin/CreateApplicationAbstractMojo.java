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

package io.helidon.service.inject.maven.plugin;

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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoSourceParser;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.codegen.ApplicationMainGenerator;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Abstract base for the Injection {@code maven-plugin} responsible for creating
 * {@code Binding}, Test {@code Binding}, and application Main class.
 */
abstract class CreateApplicationAbstractMojo extends CodegenAbstractMojo {
    /**
     * Class name of the binding class generated by Maven plugin (for end user application).
     */
    protected static final String BINDING_CLASS_NAME = "Injection__Binding";
    /**
     * Name of the generated main class.
     */
    @Parameter(property = "helidon.inject.application.main.class.name",
               defaultValue = ApplicationMainGenerator.CLASS_NAME)
    private String mainClassName;
    /**
     * The -source argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.source",
               defaultValue = "21")
    private String source;
    /**
     * The -target argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.target",
               defaultValue = "21")
    private String target;
    /**
     * Whether to validate the application when creating its bindings.
     */
    @Parameter(property = "helidon.inject.application.validate",
               defaultValue = "true")
    private boolean validate;
    /**
     * Whether to generate binding class (provides generated injection plan for all services).
     */
    @Parameter(property = "helidon.inject.application.binding.generate",
               defaultValue = "true")
    private boolean generateBinding;

    /**
     * Default constructor.
     */
    CreateApplicationAbstractMojo() {
    }

    @Override
    void innerExecute() {
        MavenLogger mavenLogger = MavenLogger.create(getLog(), failOnWarning());

        boolean hasModuleInfo = hasModuleInfo();
        Set<Path> modulepath = hasModuleInfo ? getModulepathElements() : Set.of();
        Set<Path> classpath = getClasspathElements();
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = createClassLoader(classpath, prev);
        getLog().debug("Service registry classpath: " + classpath);

        Optional<ModuleInfo> nonTestModuleInfo = findModuleInfo(nonTestSourceRootPaths())
                .map(ModuleInfoSourceParser::parse);

        /*
        We may have module info both in sources and in tests
         */
        Optional<ModuleInfo> myModuleInfo = findModuleInfo(sourceRootPaths())
                .map(ModuleInfoSourceParser::parse);
        CodegenOptions codegenOptions = MavenOptions.create(toOptions());
        CodegenScope scope = scope();
        codegenOptions.validate(Set.of());

        // package name to use (should be the same as ModuleComponent package)
        String packageName = packageName(codegenOptions, myModuleInfo, nonTestModuleInfo);
        // module name to use to define application name (should be the same as ModuleComponent uses for this module)
        String moduleName = moduleName(loader, codegenOptions, myModuleInfo, packageName, scope);

        try (ScanResult scan = new ClassGraph()
                .overrideClasspath(classpath)
                .enableAllInfo()
                .scan()) {
            MavenCodegenContext scanContext = MavenCodegenContext.create(codegenOptions,
                                                                         scan,
                                                                         scope,
                                                                         generatedSourceDirectory(),
                                                                         outputDirectory(),
                                                                         mavenLogger,
                                                                         myModuleInfo.orElse(null));

            Thread.currentThread().setContextClassLoader(loader);

            CompilerOptions compilerOptions = CompilerOptions.builder()
                    .classpath(List.copyOf(classpath))
                    .modulepath(List.copyOf(modulepath))
                    .sourcepath(sourceRootPaths())
                    .source(getSource())
                    .target(getTarget())
                    .commandLineArguments(getCompilerArgs())
                    .outputDirectory(outputDirectory())
                    .build();

            applicationBinding(loader,
                               mavenLogger,
                               scanContext,
                               compilerOptions,
                               moduleName,
                               packageName);

            if (createMain()) {
                createMain(loader,
                           mavenLogger,
                           scanContext,
                           compilerOptions,
                           packageName);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    void createMain(ClassLoader loader,
                    MavenLogger mavenLogger,
                    MavenCodegenContext scanContext,
                    CompilerOptions compilerOptions,
                    String packageName) {
        try (WrappedServices services = WrappedServices.create(loader, mavenLogger, false)) {
            createMainClass(compilerOptions,
                            scanContext,
                            services,
                            packageName);
        } catch (CodegenException e) {
            throw e;
        } catch (Exception e) {
            throw new CodegenException("An error occurred creating the main class in " + getClass().getName(), e);
        }
    }

    void applicationBinding(ClassLoader loader,
                            MavenLogger mavenLogger,
                            MavenCodegenContext scanContext,
                            CompilerOptions compilerOptions,
                            String moduleName,
                            String packageName) {
        try (WrappedServices services = WrappedServices.create(loader, mavenLogger, false)) {
            applicationBinding(scanContext,
                               services,
                               compilerOptions,
                               moduleName,
                               packageName);
        } catch (CodegenException e) {
            throw e;
        } catch (Exception e) {
            throw new CodegenException("An error occurred creating the binding in " + getClass().getName(), e);
        }
    }

    void createMainClass(CompilerOptions compilerOptions,
                         MavenCodegenContext scanContext,
                         WrappedServices services,
                         String packageName) {
        TypeName generatedType = TypeName.builder()
                .packageName(packageName)
                .className(mainClassName)
                .build();

        MainClassCreator creator = new MainClassCreator(scanContext, failOnError());
        creator.create(scanContext, compilerOptions, services, generatedType);
    }

    void applicationBinding(MavenCodegenContext scanContext,
                            WrappedServices services,
                            CompilerOptions compilerOptions,
                            String moduleName,
                            String packageName) {

        // retrieves all the services in the registry
        Set<TypeName> allServices = services.all()
                .stream()
                .map(InjectServiceInfo::serviceType)
                .collect(Collectors.toCollection(TreeSet::new));

        if (allServices.isEmpty()) {
            warn("Binding generator found no services to process");
            return;
        }

        getLog().debug("All services to be processed: " + allServices);

        String className = bindingClassName();

        if (validate) {
            // validate the application
            ApplicationValidator validator = new ApplicationValidator(scanContext, failOnWarning());
            validator.validate(services);
        }

        if (generateBinding) {
            // get the binding generator only after services are initialized (we need to ignore any existing apps)
            BindingGenerator creator = new BindingGenerator(scanContext, failOnError());

            creator.createBinding(services,
                                  allServices,
                                  TypeName.create(packageName + "." + className),
                                  moduleName,
                                  compilerOptions);
        }
    }

    /**
     * Where to generate sources. As this directory differs between production code and test code, it must be provided
     * by a subclass.
     *
     * @return where to generate sources
     */
    abstract Path generatedSourceDirectory();

    /**
     * Binding class name to be generated.
     *
     * @return binding class name
     */
    abstract String bindingClassName();

    /**
     * Output directory for this {@link #scope()}.
     *
     * @return output directory
     */
    abstract Path outputDirectory();

    abstract boolean createMain();

    /**
     * Source roots for this {@link #scope()}.
     *
     * @return source roots
     */
    List<Path> sourceRootPaths() {
        return nonTestSourceRootPaths();
    }

    /**
     * Production source roots for this project.
     *
     * @return source roots for production code
     */
    List<Path> nonTestSourceRootPaths() {
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

    Optional<Path> findModuleInfo(List<Path> sourcePaths) {
        return sourcePaths.stream()
                .map(it -> it.resolve(ModuleInfo.FILE_NAME))
                .filter(Files::exists)
                .findFirst();
    }

    void warn(String msg) {
        getLog().warn(msg);

        if (failOnWarning()) {
            throw new CodegenException(msg);
        }
    }

    /**
     * The scope of the code generation (production, test etc.).
     *
     * @return codegen scope
     */
    abstract CodegenScope scope();

    /**
     * Creates a new classloader.
     *
     * @param classPath the classpath to use
     * @param parent    the parent loader
     * @return the loader
     */
    URLClassLoader createClassLoader(Collection<Path> classPath,
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

    String getSource() {
        return source;
    }

    String getTarget() {
        return target;
    }

    LinkedHashSet<Path> getSourceClasspathElements() {
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
    LinkedHashSet<Path> getClasspathElements() {
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
                .or(() -> myModuleInfo.flatMap(this::exportedPackage))
                .or(() -> srcModuleInfo.flatMap(this::exportedPackage))
                .or(this::firstUsedPackage)
                .orElseThrow(() -> new CodegenException("Unable to determine package for binding class."));
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
                            .filter(Predicate.not(String::isBlank))
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
                .orElseGet(() -> "unnamed/"
                        + packageName
                        + (scope.isProduction() ? "" : "/" + scope.name()));
    }

    private String packageName(Path rootPath, Path filePath) {
        Path parent = filePath.getParent();
        if (parent == null) {
            return "";
        }
        return toDotSeparated(rootPath.relativize(parent));
    }

    private Set<String> toOptions() {
        Set<String> options = new HashSet<>(getCompilerArgs());

        moduleNameFromMavenConfig().ifPresent(it -> options.add("-A" + CodegenOptions.TAG_CODEGEN_MODULE + "=" + it));
        packageNameFromMavenConfig().ifPresent(it -> options.add("-A" + CodegenOptions.TAG_CODEGEN_PACKAGE + "=" + it));

        return options;
    }
}
