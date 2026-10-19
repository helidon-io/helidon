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
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.helidon.common.Api;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Stages compiled classes and generates application bindings in a separate JVM using the Java toolchain.
 * Each execution replaces its own output, so deleted services and renamed bindings cannot remain in application artifacts.
 * JavaBean accessors and the injected constructor are Gradle task and managed-property contracts.
 */
@Api.Incubating
@DisableCachingByDefault(because = "Only local up-to-date checking is supported for the isolated compiler and classpath scan")
public abstract class GenerateBindings extends DefaultTask {
    private final BindingOptions options;
    private final ExecOperations execOperations;
    private final FileSystemOperations fileOperations;

    /**
     * Constructor used by Gradle service injection.
     * @param objects managed object factory
     * @param execOperations process execution service
     * @param fileOperations file system service
     */
    @Inject
    public GenerateBindings(ObjectFactory objects, ExecOperations execOperations, FileSystemOperations fileOperations) {
        this.options = objects.newInstance(BindingOptions.class);
        this.execOperations = execOperations;
        this.fileOperations = fileOperations;
        getCompilerArgs().convention(List.of());
        getGenerateApplication().convention(true);
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
     * Initial compiler output, before application binding replacement.
     * @return initial classes
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getRawClasses();

    /**
     * Application dependencies and resources to scan, excluding this task's output.
     * @return scan classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getApplicationClasspath();

    /**
     * Isolated tool implementation and dependencies.
     * @return generator classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getGeneratorClasspath();

    /**
     * Sources of the scope being generated, including module descriptors and annotation processor options.
     * @return source roots
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    /**
     * Application source roots, also provided when generating test bindings.
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
     * Java release used for generated classes.
     * @return release
     */
    @Input
    public abstract Property<String> getRelease();

    /**
     * Scope, {@code main} or {@code test}.
     * @return scope
     */
    @Input
    public abstract Property<String> getScope();

    /**
     * Whether generation should run after staging initial classes.
     * @return generation switch
     */
    @Input
    public abstract Property<Boolean> getGenerateApplication();

    /**
     * Java launcher selected from the project's toolchain.
     * @return launcher
     */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Final classes consumed by packaging, test compilation and execution.
     * @return final classes directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getClassesDirectory();

    /**
     * Generated Java sources, separate from annotation processor sources and initial compilation.
     * @return generated sources directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourceDirectory();

    /**
     * Replaces staged output and runs the isolated binding generator.
     */
    @TaskAction
    public void generate() {
        File classes = getClassesDirectory().get().getAsFile();
        File sources = getGeneratedSourceDirectory().get().getAsFile();
        fileOperations.sync(copy -> {
            copy.from(getRawClasses());
            copy.into(classes);
        });
        fileOperations.delete(delete -> delete.delete(sources));
        try {
            Files.createDirectories(classes.toPath());
            Files.createDirectories(sources.toPath());
            if (!getGenerateApplication().get()
                    || (!options.getPackageName().isPresent()
                    && getSourceRoots().getAsFileTree().matching(patterns -> patterns.include("**/*.java")).isEmpty())) {
                return;
            }
            Properties request = request(classes, sources);
            File requestFile = new File(getTemporaryDir(), "generator.properties");
            try (var output = Files.newOutputStream(requestFile.toPath())) {
                request.store(output, "Helidon application generation");
            }
            execOperations.javaexec(exec -> {
                exec.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
                exec.classpath(getGeneratorClasspath());
                exec.getMainClass().set("io.helidon.service.application.codegen.ApplicationGenerator");
                exec.args(requestFile.getAbsolutePath());
            }).assertNormalExitValue();
        } catch (IOException e) {
            throw new GradleException("Cannot prepare application binding generation", e);
        }
    }

    private static String paths(FileCollection files) {
        return files.getFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private Properties request(File classes, File sources) {
        Properties request = new Properties();
        String dependencies = paths(getApplicationClasspath());
        request.setProperty("classpath", classes.getAbsolutePath()
                + (dependencies.isEmpty() ? "" : File.pathSeparator + dependencies));
        request.setProperty("modulepath", request.getProperty("classpath"));
        request.setProperty("sourceRoots", paths(getSourceRoots()));
        request.setProperty("mainSourceRoots", paths(getMainSourceRoots()));
        request.setProperty("testSourceRoots", paths(getTestSourceRoots()));
        request.setProperty("generatedSourceDirectory", sources.getAbsolutePath());
        request.setProperty("outputDirectory", classes.getAbsolutePath());
        request.setProperty("release", getRelease().get());
        request.setProperty("scope", getScope().get());
        request.setProperty("bindingClassName", options.getBindingClassName().get());
        request.setProperty("mainClassName", options.getMainClassName().get());
        if (options.getPackageName().isPresent()) {
            request.setProperty("packageName", options.getPackageName().get());
        }
        if (options.getModuleName().isPresent()) {
            request.setProperty("moduleName", options.getModuleName().get());
        }
        request.setProperty("generateBinding", options.getGenerateBinding().get().toString());
        request.setProperty("generateMain", options.getGenerateMain().get().toString());
        request.setProperty("validate", options.getValidate().get().toString());
        request.setProperty("failOnError", options.getFailOnError().get().toString());
        request.setProperty("failOnWarning", options.getFailOnWarning().get().toString());
        var compilerArgs = getCompilerArgs().get();
        request.setProperty("compilerArgs.count", Integer.toString(compilerArgs.size()));
        for (int i = 0; i < compilerArgs.size(); i++) {
            request.setProperty("compilerArgs." + i, compilerArgs.get(i));
        }
        return request;
    }
}
