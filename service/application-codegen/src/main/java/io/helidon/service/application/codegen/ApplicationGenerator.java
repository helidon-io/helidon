/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.service.application.codegen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoSourceParser;
import io.helidon.codegen.compiler.Compiler;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.Api;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.ServiceInfo;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

/**
 * Generates and compiles declarative application bindings using the complete application runtime classpath.
 * Both Maven and Gradle integrations use this generator.
 */
@Api.Incubating
public final class ApplicationGenerator {
    private final ApplicationGeneratorConfig config;
    private final CodegenLogger logger;

    private ApplicationGenerator(ApplicationGeneratorConfig config, CodegenLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Generate application bindings and optional application main class.
     *
     * @param config generation configuration
     */
    public static void generate(ApplicationGeneratorConfig config) {
        generate(config, CodegenLogger.create(System.getLogger(ApplicationGenerator.class.getName())));
    }

    /**
     * Generate application bindings with a build-tool logger.
     *
     * @param config generation configuration
     * @param logger generation diagnostics
     */
    public static void generate(ApplicationGeneratorConfig config, CodegenLogger logger) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(logger);
        try {
            new ApplicationGenerator(config, logger).generate();
        } catch (RuntimeException e) {
            if (config.failOnError()) {
                throw e;
            }
            logger.log(System.Logger.Level.WARNING, "Failed to generate application binding: " + e.getMessage());
        }
    }

    /**
     * Run the generator in an isolated Java process using a Java properties file.
     * The file accepts paths for {@code classpath}, {@code modulepath}, {@code sourceRoots},
     * {@code mainSourceRoots}, and {@code testSourceRoots}, separated by the platform path separator.
     * It requires {@code generatedSourceDirectory} and {@code outputDirectory}.
     * Other keys correspond to the generation configuration, with {@code scope} set to
     * {@code main} or {@code test}. Compiler arguments use {@code compilerArgs.count}
     * and indexed keys {@code compilerArgs.0}, {@code compilerArgs.1}, and so on.
     *
     * @param args exactly one properties-file path
     */
    public static void main(String[] args) {
        Objects.requireNonNull(args);
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one application generator properties file");
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) {
            properties.load(input);
        } catch (IOException e) {
            throw new CodegenException("Unable to read application generator properties", e);
        }
        var builder = ApplicationGeneratorConfig.builder()
                .classpath(paths(properties, "classpath"))
                .modulepath(paths(properties, "modulepath"))
                .sourceRoots(paths(properties, "sourceRoots"))
                .mainSourceRoots(paths(properties, "mainSourceRoots"))
                .testSourceRoots(paths(properties, "testSourceRoots"))
                .generatedSourceDirectory(Path.of(required(properties, "generatedSourceDirectory")))
                .outputDirectory(Path.of(required(properties, "outputDirectory")))
                .release(properties.getProperty("release", "27"))
                .bindingClassName(properties.getProperty("bindingClassName", "ApplicationBinding"))
                .mainClassName(properties.getProperty("mainClassName", "ApplicationMain"))
                .generateBinding(Boolean.parseBoolean(properties.getProperty("generateBinding", "true")))
                .generateMain(Boolean.parseBoolean(properties.getProperty("generateMain", "false")))
                .validate(Boolean.parseBoolean(properties.getProperty("validate", "true")))
                .failOnError(Boolean.parseBoolean(properties.getProperty("failOnError", "true")))
                .failOnWarning(Boolean.parseBoolean(properties.getProperty("failOnWarning", "false")));
        String scope = properties.getProperty("scope", "main");
        if (!scope.equals("main") && !scope.equals("test")) {
            throw new IllegalArgumentException("Unsupported generation scope: " + scope);
        }
        builder.scope(scope.equals("main") ? CodegenScope.PRODUCTION : new CodegenScope("test"));
        Optional.ofNullable(properties.getProperty("packageName")).ifPresent(builder::packageName);
        Optional.ofNullable(properties.getProperty("moduleName")).ifPresent(builder::moduleName);
        int argumentCount = Integer.parseInt(properties.getProperty("compilerArgs.count", "0"));
        List<String> arguments = new ArrayList<>(argumentCount);
        for (int i = 0; i < argumentCount; i++) {
            arguments.add(required(properties, "compilerArgs." + i));
        }
        generate(builder.compilerArgs(arguments).build());
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing generator property: " + name);
        }
        return value;
    }

    private static List<Path> paths(Properties properties, String name) {
        String value = properties.getProperty(name, "");
        if (value.isBlank()) {
            return List.of();
        }
        return Pattern.compile(Pattern.quote(File.pathSeparator))
                .splitAsStream(value)
                .filter(Predicate.not(String::isBlank))
                .map(Path::of)
                .toList();
    }

    // to dot separated path
    private static String toDotSeparated(Path relativePath) {
        return StreamSupport.stream(relativePath.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("."));
    }

    private void generate() {
        boolean hasModuleInfo = findModuleInfo(config.sourceRoots()).isPresent();
        Set<Path> modulepath = hasModuleInfo ? new LinkedHashSet<>(config.modulepath()) : Set.of();
        Set<Path> classpath = new LinkedHashSet<>(config.classpath());
        ClassLoader prev = Thread.currentThread().getContextClassLoader();

        logger.log(System.Logger.Level.DEBUG, "Service registry classpath: " + classpath);

        Optional<ModuleInfo> nonTestModuleInfo = findModuleInfo(config.mainSourceRoots())
                .map(ModuleInfoSourceParser::parse);

        /*
        We may have module info both in sources and in tests
         */
        Optional<ModuleInfo> myModuleInfo = findModuleInfo(config.sourceRoots())
                .map(ModuleInfoSourceParser::parse);
        CodegenOptions codegenOptions = ApplicationOptions.create(toOptions());
        CodegenScope scope = config.scope();
        codegenOptions.validate(Set.of());

        // package name to use (should be the same as ModuleComponent package)
        String packageName = packageName(codegenOptions, myModuleInfo, nonTestModuleInfo);
        // module name to use to define application name (should be the same as ModuleComponent uses for this module)
        String moduleName = moduleName(codegenOptions, myModuleInfo, packageName, scope);

        try (URLClassLoader loader = createClassLoader(classpath, prev);
                ScanResult scan = new ClassGraph()
                .overrideClasspath(classpath)
                .enableAllInfo()
                .scan()) {
            ApplicationCodegenContext scanContext = ApplicationCodegenContext.create(codegenOptions,
                                                                                     scan,
                                                                                     scope,
                                                                                     config.generatedSourceDirectory(),
                                                                                     config.outputDirectory(),
                                                                                     logger,
                                                                                     myModuleInfo.orElse(null));

            Thread.currentThread().setContextClassLoader(loader);

            CompilerOptions compilerOptions = CompilerOptions.builder()
                    .classpath(List.copyOf(classpath))
                    .modulepath(List.copyOf(modulepath))
                    .sourcepath(config.sourceRoots())
                    .release(config.release())
                    .commandLineArguments(config.compilerArgs())
                    .outputDirectory(config.outputDirectory())
                    .build();

            applicationBinding(loader,
                               logger,
                               scanContext,
                               compilerOptions,
                               moduleName,
                               packageName);
        } catch (IOException e) {
            throw new CodegenException("Unable to close application classloader", e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private void applicationBinding(ClassLoader loader,
                                    CodegenLogger logger,
                                    ApplicationCodegenContext scanContext,
                                    CompilerOptions compilerOptions,
                                    String moduleName,
                                    String packageName) {
        try (WrappedServices services = WrappedServices.create(loader, logger, false)) {
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

    private void applicationBinding(ApplicationCodegenContext scanContext,
                                    WrappedServices services,
                                    CompilerOptions compilerOptions,
                                    String moduleName,
                                    String packageName) {

        // retrieves all the services in the registry
        Set<TypeName> allServices = services.all()
                .stream()
                .map(ServiceInfo::serviceType)
                .collect(Collectors.toCollection(TreeSet::new));

        if (allServices.isEmpty()) {
            warn("Binding generator found no services to process");
            return;
        }

        logger.log(System.Logger.Level.DEBUG, "All services to be processed: " + allServices);

        String className = config.bindingClassName();

        if (config.validate()) {
            // validate the application
            ApplicationValidator validator = new ApplicationValidator(scanContext, config.failOnWarning());
            validator.validate(services);
        }

        TypeName bindingTypeName = TypeName.create(packageName + "." + className);
        List<Path> sourcesToCompile = new ArrayList<>();

        if (config.generateBinding()) {
            // get the binding generator only after services are initialized (we need to ignore any existing apps)
            BindingGenerator generator = new BindingGenerator(scanContext, config.failOnError());

            logger.log(System.Logger.Level.INFO, "Generating application binding: " + bindingTypeName.fqName());

            generator.createBinding(services,
                                    allServices,
                                    bindingTypeName,
                                    moduleName,
                                    sourcesToCompile);
        }

        if (config.scope().isProduction() && config.generateMain()) {
            TypeName mainTypeName = TypeName.builder()
                    .packageName(packageName)
                    .className(config.mainClassName())
                    .build();

            MainGenerator generator = new MainGenerator(scanContext);
            generator.createMain(services,
                                 config.generateBinding(),
                                 bindingTypeName,
                                 mainTypeName,
                                 sourcesToCompile);
        }

        if (!sourcesToCompile.isEmpty()) {
            Compiler.compile(compilerOptions, sourcesToCompile.toArray(new Path[0]));
        }

    }

    private Optional<Path> findModuleInfo(List<Path> sourcePaths) {
        return sourcePaths.stream()
                .map(it -> it.resolve(ModuleInfo.FILE_NAME))
                .filter(Files::exists)
                .findFirst();
    }

    private void warn(String msg) {
        logger.log(System.Logger.Level.WARNING, msg);

        if (config.failOnWarning()) {
            throw new CodegenException(msg);
        }
    }

    /**
     * Creates a new classloader.
     *
     * @param classPath the classpath to use
     * @param parent    the parent loader
     * @return the loader
     */
    private URLClassLoader createClassLoader(Collection<Path> classPath,
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
        return firstUsedPackage(config.mainSourceRoots())
                .or(() -> firstUsedPackage(config.testSourceRoots()));
    }

    private Optional<String> firstUsedPackage(List<Path> sourceRoots) {
        Set<String> found = new TreeSet<>(Comparator.comparing(String::length));

        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try {
                try (Stream<Path> pathStream = Files.walk(sourceRoot)) {
                    pathStream
                            .filter(it -> {
                                Path fileName = it.getFileName();
                                return fileName != null && fileName.toString().endsWith(".java");
                            })
                            .map(it -> packageName(sourceRoot, it))
                            .filter(Predicate.not(String::isBlank))
                            .forEach(found::add);
                }
            } catch (IOException _) {
                logger.log(System.Logger.Level.DEBUG, "Failed to walk source root: " + sourceRoot.toAbsolutePath());
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

    private String moduleName(CodegenOptions codegenOptions,
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
        Set<String> options = new HashSet<>(config.compilerArgs());

        config.moduleName().ifPresent(it -> options.add("-A" + CodegenOptions.TAG_CODEGEN_MODULE + "=" + it));
        config.packageName().ifPresent(it -> options.add("-A" + CodegenOptions.TAG_CODEGEN_PACKAGE + "=" + it));

        return options;
    }
}

