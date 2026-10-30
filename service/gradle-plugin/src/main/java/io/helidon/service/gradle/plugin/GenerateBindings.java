/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.service.gradle.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenScope;
import io.helidon.common.Api;
import io.helidon.service.application.codegen.ApplicationGenerator;
import io.helidon.service.application.codegen.ApplicationGeneratorConfig;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.work.DisableCachingByDefault;

/**
 * Generates application binding classes separately from compiler outputs using the Gradle JVM's Java compiler.
 */
@Api.Incubating
@DisableCachingByDefault(because = "Only local up-to-date checking is supported for generation and classpath scanning")
public abstract class GenerateBindings extends DefaultTask {
    private final BindingOptions options;
    private final FileSystemOperations fileOperations;

    /**
     * Constructor used by Gradle service injection.
     * @param objects managed object factory
     * @param fileOperations file system service
     */
    @Inject
    public GenerateBindings(ObjectFactory objects, FileSystemOperations fileOperations) {
        this.options = Objects.requireNonNull(objects, "objects").newInstance(BindingOptions.class);
        this.fileOperations = Objects.requireNonNull(fileOperations, "fileOperations");
        getCompilerArgs().convention(List.of());
        getGenerateApplication().convention(true);
        getScope().convention("main");
        getRelease().convention(String.valueOf(Runtime.version().feature()));
        getMainSourceRoots().convention(getSourceRoots());
        var buildDir = getProject().getLayout().getBuildDirectory();
        getClassesDirectory().convention(buildDir.dir("classes/helidon/" + getName()));
        getGeneratedSourceDirectory().convention(buildDir.dir("generated/sources/helidon/" + getName()));
    }

    static FileCollection prependClasses(Project project, SourceSet sourceSet, FileCollection generatedClasses) {
        if (!(sourceSet.getOutput().getClassesDirs() instanceof ConfigurableFileCollection classes)) {
            throw new GradleException("The Helidon service plugin requires configurable Java class directories: "
                                              + sourceSet.getName());
        }
        // Preserve compiler outputs and give completed bindings precedence on consumer classpaths.
        var contributions = new ArrayList<>(classes.getFrom());
        classes.setFrom(generatedClasses, contributions);
        // Follow later additive registrations without feeding this generator's output back into its own inputs.
        return project.files(project.provider(() -> classes.getFrom().stream()
                        .filter(source -> source != generatedClasses)
                        .toList()))
                .builtBy(project.provider(classes::getBuiltBy));
    }

    static void excludePlaceholders(Jar jar, Provider<Directory> generatedClasses, FileCollection classpath) {
        var generatedEntries = new HashSet<String>();
        jar.eachFile(details -> {
            if (details.isDirectory() || !details.getName().endsWith(".class")) {
                return;
            }
            Path generated = generatedClasses.get().getAsFile().toPath();
            Path file = details.getFile().toPath();
            String entry = details.getRelativePath().getPathString();
            if (file.startsWith(generated)) {
                generatedEntries.add(entry);
            } else if (generatedEntries.contains(entry)) {
                for (var input : classpath) {
                    Path root = input.toPath();
                    if (file.startsWith(root) && Files.isRegularFile(generated.resolve(root.relativize(file)))) {
                        // Resolve duplicate final paths after later copy actions have transformed or excluded entries.
                        details.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
                        break;
                    }
                }
            }
        });
    }

    /**
     * Generator options.
     * @return options
     */
    @Nested
    public BindingOptions getOptions() {
        return options;
    }

    /**
     * Compiler outputs, application runtime dependencies and resources to scan, excluding this task's output.
     * Use {@code classpath.from(...)} to add inputs without replacing the plugin's defaults.
     * @return scan classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Sources of the scope being generated, including module descriptors and annotation processor options.
     * @return source roots
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    /**
     * Application source roots, also provided when generating test bindings.
     * Defaults to {@link #getSourceRoots() sourceRoots} for independently registered tasks.
     * @return main source roots
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainSourceRoots();

    /**
     * Test source roots, empty for application generation.
     * @return test source roots
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestSourceRoots();

    /**
     * Additional compiler arguments inherited from Java compilation.
     * @return compiler arguments
     */
    @Input
    public abstract ListProperty<String> getCompilerArgs();

    /**
     * Java release used for generated classes, defaulting to the Gradle JVM's Java feature version.
     * The full plugin uses the corresponding {@code JavaCompile} task's configured release.
     * @return release
     */
    @Input
    public abstract Property<String> getRelease();

    /**
     * Scope, {@code main} or {@code test}, defaulting to {@code main}.
     * @return scope
     */
    @Input
    public abstract Property<String> getScope();

    /**
     * Whether application generation should run.
     * @return generation switch
     */
    @Input
    public abstract Property<Boolean> getGenerateApplication();

    /**
     * Generated binding and main classes, separate from compiler outputs.
     * Defaults to {@code build/classes/helidon/<taskName>}; the full plugin uses {@code main} and {@code test}.
     * @return generated classes directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getClassesDirectory();

    /**
     * Generated Java sources, separate from annotation processor sources and initial compilation.
     * Defaults to {@code build/generated/sources/helidon/<taskName>}; the full plugin uses {@code main} and {@code test}.
     * @return generated sources directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourceDirectory();

    /**
     * Prepends this task's generated classes to a source set and adds its other class directories to this task's classpath.
     *
     * @param sourceSet source set receiving the generated classes
     */
    public void prependClassesTo(SourceSet sourceSet) {
        Objects.requireNonNull(sourceSet, "sourceSet");
        var project = getProject();
        var generatedClasses = project.files(getClassesDirectory()).builtBy(this);
        getClasspath().from(prependClasses(project, sourceSet, generatedClasses));
    }

    /**
     * Configures an archive to omit classpath-directory placeholders shadowed by this task's generated classes.
     * The archive must include generated classes before compiler outputs, for example through a source set
     * configured with {@link #prependClassesTo(org.gradle.api.tasks.SourceSet)}.
     * This method adds a dependency on this task, but does not add archive contents.
     * Call from the archive's configuration action so selecting only that archive realizes the generator.
     * Other duplicate entries retain the archive's normal duplicate policy.
     *
     * @param jar archive receiving placeholder exclusions
     */
    public void addExclusionsTo(Jar jar) {
        Objects.requireNonNull(jar, "jar");
        jar.dependsOn(this);
        excludePlaceholders(jar, getClassesDirectory(), getClasspath());
    }

    /**
     * Replaces generated output and invokes the shared binding generator in the Gradle JVM.
     */
    @TaskAction
    public void generate() {
        File classes = getClassesDirectory().get().getAsFile();
        File sources = getGeneratedSourceDirectory().get().getAsFile();
        try {
            fileOperations.delete(delete -> delete.delete(classes, sources));
            Files.createDirectories(classes.toPath());
            Files.createDirectories(sources.toPath());
            if (!getGenerateApplication().get()
                    || (!options.getPackageName().isPresent()
                    && getSourceRoots().getAsFileTree().matching(patterns -> patterns.include("**/*.java")).isEmpty())) {
                return;
            }
            generateApplication(classes.toPath(), sources.toPath());
        } catch (IOException e) {
            throw new GradleException("Cannot prepare application binding generation", e);
        }
    }

    private void generateApplication(Path classes, Path sources) {
        List<Path> classpath = paths(getClasspath());
        List<Path> sourceRoots = new ArrayList<>();
        sourceRoots.add(sources);
        sourceRoots.addAll(paths(getSourceRoots()));
        var builder = ApplicationGeneratorConfig.builder()
                .classpath(classpath)
                .modulepath(classpath)
                .sourceRoots(sourceRoots)
                .mainSourceRoots(paths(getMainSourceRoots()))
                .testSourceRoots(paths(getTestSourceRoots()))
                .generatedSourceDirectory(sources)
                .outputDirectory(classes)
                .release(getRelease().get())
                .scope(switch (getScope().get()) {
                    case "main" -> CodegenScope.PRODUCTION;
                    case "test" -> new CodegenScope("test");
                    default -> throw new GradleException("Unsupported generation scope: " + getScope().get());
                })
                .bindingClassName(options.getBindingClassName().get())
                .mainClassName(options.getMainClassName().get())
                .generateBinding(options.getGenerateBinding().get())
                .generateMain(options.getGenerateMain().get())
                .validate(options.getValidate().get())
                .failOnError(options.getFailOnError().get())
                .failOnWarning(options.getFailOnWarning().get())
                .compilerArgs(getCompilerArgs().get());
        if (options.getPackageName().isPresent()) {
            builder.packageName(options.getPackageName().get());
        }
        if (options.getModuleName().isPresent()) {
            builder.moduleName(options.getModuleName().get());
        }
        ApplicationGenerator.generate(builder.build(), this::log);
    }

    private void log(CodegenEvent event) {
        switch (event.level()) {
        case ALL, TRACE, DEBUG -> log(LogLevel.DEBUG, event);
        case INFO -> log(LogLevel.INFO, event);
        case WARNING -> log(LogLevel.WARN, event);
        case ERROR -> log(LogLevel.ERROR, event);
        case OFF -> {
        }
        default -> throw new IllegalStateException("Unsupported code generation log level: " + event.level());
        }
    }

    private void log(LogLevel level, CodegenEvent event) {
        if (event.throwable().isPresent()) {
            getLogger().log(level, event.message(), event.throwable().get());
        } else {
            getLogger().log(level, event.message());
        }
    }

    private static List<Path> paths(FileCollection files) {
        return files.getFiles().stream().map(File::toPath).toList();
    }
}
