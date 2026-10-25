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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;

import io.helidon.common.Api;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.jvm.tasks.ProcessResources;

/**
 * Native Gradle integration for declarative application binding generation.
 * Application and test outputs contain completed bindings before packaging or execution.
 * Apply the plugin using the {@code io.helidon.service} plugin ID and configure the
 * {@link HelidonServiceExtension helidonService extension}. This implementation type is internal;
 * its public visibility and constructor support Gradle plugin instantiation.
 */
@Api.Internal
public final class HelidonServicePlugin implements Plugin<Project> {
    /**
     * Constructor used by Gradle plugin instantiation.
     */
    public HelidonServicePlugin() {
    }

    @Override
    public void apply(Project project) {
        Objects.requireNonNull(project, "project");
        var extension = project.getExtensions().create("helidonService", HelidonServiceExtension.class);
        extension.getToolVersion().convention(pluginVersion());
        Configuration generator = project.getConfigurations().create("helidonServiceGenerator", configuration -> {
            configuration.setDescription("Isolated Helidon application binding generator");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                     project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            });
            configuration.defaultDependencies(dependencies -> dependencies.add(project.getDependencies().create(
                    "io.helidon.service:helidon-service-application-codegen:" + extension.getToolVersion().get())));
        });
        project.getPluginManager().withPlugin("java", _ -> configureJava(project, extension, generator));
    }

    private static String pluginVersion() {
        Properties properties = new Properties();
        try (var input = HelidonServicePlugin.class.getResourceAsStream("plugin.properties")) {
            if (input == null) {
                throw new GradleException("Missing Helidon service Gradle plugin version resource");
            }
            properties.load(input);
            return Objects.requireNonNull(properties.getProperty("version"), "plugin version");
        } catch (IOException e) {
            throw new GradleException("Cannot read Helidon service Gradle plugin version", e);
        }
    }

    private static void configureJava(Project project, HelidonServiceExtension extension, Configuration generator) {
        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        var test = java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        var application = register(project, "generateApplicationBindings", main, main, extension, generator, java);
        var tests = register(project, "generateTestApplicationBindings", test, main, extension, generator, java);
        tests.configure(task -> {
            task.getApplicationClasspath().from(main.getOutput());
            task.getGenerateApplication().convention(extension.getGenerateTestBinding());
            task.getOptions().getGenerateBinding().convention(extension.getGenerateTestBinding());
            task.getOptions().getGenerateMain().set(false);
            task.getOptions().getBindingClassName().convention(extension.getTestBindingClassName());
            task.getOptions().getPackageName().convention(extension.getTestPackageName());
            task.getTestSourceRoots().from(test.getAllJava().getSourceDirectories());
        });
        classes(project, main, application);
        classes(project, test, tests);
        project.getTasks().named(main.getJarTaskName(), Jar.class).configure(jar ->
                excludePlaceholders(jar,
                                    application.flatMap(GenerateBindings::getClassesDirectory),
                                    project.files(application.map(GenerateBindings::getRawClasses))));
        project.getTasks().named(main.getClassesTaskName()).configure(task -> task.dependsOn(application));
        project.getTasks().named(test.getClassesTaskName()).configure(task -> task.dependsOn(tests));
    }

    private static void classes(Project project, SourceSet sourceSet, TaskProvider<GenerateBindings> generator) {
        var compile = project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        if (!(sourceSet.getOutput().getClassesDirs() instanceof ConfigurableFileCollection classes)) {
            throw new GradleException("The Helidon service plugin requires configurable Java class directories: "
                                              + sourceSet.getName());
        }
        // Keep all compiler outputs and give completed bindings precedence on consumer classpaths.
        var contributions = new ArrayList<>(classes.getFrom());
        var completedClasses = generator.flatMap(GenerateBindings::getClassesDirectory);
        // Task-level compiler destinations can differ from the source directory set's convention.
        var compilerClasses = project.files(compile.flatMap(JavaCompile::getDestinationDirectory))
                .minus(project.files(sourceSet.getJava().getDestinationDirectory()));
        classes.setFrom(completedClasses, contributions, compilerClasses);
        // Follow later additive registrations without feeding this generator's output back into its own inputs.
        var additionalClasses = project.files(project.provider(() -> classes.getFrom().stream()
                        .filter(source -> source != completedClasses)
                        .toList()))
                .builtBy(project.provider(classes::getBuiltBy));
        generator.configure(task -> {
            task.getRawClasses().from(compile.flatMap(JavaCompile::getDestinationDirectory));
            task.getApplicationClasspath().from(additionalClasses);
        });
    }

    private static void excludePlaceholders(Jar jar, Provider<Directory> generatedClasses, FileCollection rawClasses) {
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
                for (var raw : rawClasses) {
                    Path root = raw.toPath();
                    if (file.startsWith(root) && Files.isRegularFile(generated.resolve(root.relativize(file)))) {
                        details.exclude();
                        break;
                    }
                }
            }
        });
    }

    private static TaskProvider<GenerateBindings> register(Project project,
                                                           String taskName,
                                                           SourceSet sourceSet,
                                                           SourceSet main,
                                                           HelidonServiceExtension extension,
                                                           Configuration generator,
                                                           JavaPluginExtension java) {
        var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        TaskProvider<JavaCompile> compile = project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        TaskProvider<ProcessResources> resources = project.getTasks().named(sourceSet.getProcessResourcesTaskName(),
                                                                          ProcessResources.class);
        Configuration runtime = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
        return project.getTasks().register(taskName, GenerateBindings.class, task -> {
            task.setGroup("build");
            task.setDescription("Generates Helidon declarative bindings for " + sourceSet.getName());
            task.dependsOn(compile, resources);
            task.getApplicationClasspath().from(resources.map(ProcessResources::getDestinationDir), runtime);
            task.getGeneratorClasspath().from(generator);
            task.getSourceRoots().from(sourceSet.getAllJava().getSourceDirectories());
            task.getMainSourceRoots().from(main.getAllJava().getSourceDirectories());
            task.getScope().set(sourceSet.getName());
            task.getCompilerArgs().set(compile.map(compilation -> compilation.getOptions().getAllCompilerArgs()));
            task.getRelease().set(compile.flatMap(compilation -> compilation.getOptions().getRelease())
                                          .map(String::valueOf)
                                          .orElse(compile.map(JavaCompile::getTargetCompatibility)));
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            task.getClassesDirectory().convention(project.getLayout().getBuildDirectory()
                                                          .dir("classes/helidon/" + sourceSet.getName()));
            task.getGeneratedSourceDirectory().convention(project.getLayout().getBuildDirectory()
                                                                  .dir("generated/sources/helidon/" + sourceSet.getName()));
            options(task.getOptions(), extension);
        });
    }

    private static void options(BindingOptions target, BindingOptions source) {
        target.getPackageName().convention(source.getPackageName());
        target.getModuleName().convention(source.getModuleName());
        target.getBindingClassName().convention(source.getBindingClassName());
        target.getMainClassName().convention(source.getMainClassName());
        target.getGenerateBinding().convention(source.getGenerateBinding());
        target.getGenerateMain().convention(source.getGenerateMain());
        target.getValidate().convention(source.getValidate());
        target.getFailOnError().convention(source.getFailOnError());
        target.getFailOnWarning().convention(source.getFailOnWarning());
    }
}
