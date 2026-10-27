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

import java.util.Objects;

import io.helidon.common.Api;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
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
        project.getPluginManager().withPlugin("java", _ -> configureJava(project, extension));
    }

    private static void configureJava(Project project, HelidonServiceExtension extension) {
        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        var test = java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        var application = register(project, "generateApplicationBindings", main, main, extension);
        var tests = register(project, "generateTestApplicationBindings", test, main, extension);
        tests.configure(task -> {
            task.getClasspath().from(main.getOutput());
            task.getGenerateApplication().convention(extension.getGenerateTestBinding());
            task.getOptions().getGenerateBinding().convention(extension.getGenerateTestBinding());
            task.getOptions().getGenerateMain().set(false);
            task.getOptions().getBindingClassName().convention(extension.getTestBindingClassName());
            task.getOptions().getPackageName().convention(extension.getTestPackageName());
            task.getTestSourceRoots().from(test.getAllJava().getSourceDirectories());
        });
        classes(project, main, application);
        classes(project, test, tests);
        project.getTasks().named(main.getJarTaskName(), Jar.class).configure(jar -> {
            jar.dependsOn(application);
            GenerateBindings.excludePlaceholders(jar,
                                                 application.flatMap(GenerateBindings::getClassesDirectory),
                                                 project.files(application.map(GenerateBindings::getClasspath)));
        });
        project.getTasks().named(main.getClassesTaskName()).configure(task -> task.dependsOn(application));
        project.getTasks().named(test.getClassesTaskName()).configure(task -> task.dependsOn(tests));
    }

    private static void classes(Project project, SourceSet sourceSet, TaskProvider<GenerateBindings> generator) {
        var generatedClasses = project.files(generator.flatMap(GenerateBindings::getClassesDirectory));
        var compilerClasses = GenerateBindings.prependClasses(project, sourceSet, generatedClasses);
        generator.configure(task -> task.getClasspath().from(compilerClasses));
    }

    private static TaskProvider<GenerateBindings> register(Project project,
                                                           String taskName,
                                                           SourceSet sourceSet,
                                                           SourceSet main,
                                                           HelidonServiceExtension extension) {
        TaskProvider<JavaCompile> compile = project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        TaskProvider<ProcessResources> resources = project.getTasks().named(sourceSet.getProcessResourcesTaskName(),
                                                                          ProcessResources.class);
        Configuration runtime = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
        return project.getTasks().register(taskName, GenerateBindings.class, task -> {
            task.setGroup("build");
            task.setDescription("Generates Helidon declarative bindings for " + sourceSet.getName());
            task.dependsOn(compile, resources);
            task.getClasspath().from(compile.flatMap(JavaCompile::getDestinationDirectory),
                                     resources.map(ProcessResources::getDestinationDir),
                                     runtime);
            task.getSourceRoots().from(sourceSet.getAllJava().getSourceDirectories());
            task.getMainSourceRoots().from(main.getAllJava().getSourceDirectories());
            task.getScope().set(sourceSet.getName());
            task.getCompilerArgs().set(compile.map(compilation -> compilation.getOptions().getAllCompilerArgs()));
            task.getRelease().set(compile.flatMap(compilation -> compilation.getOptions().getRelease())
                                          .map(String::valueOf)
                                          .orElse(compile.map(JavaCompile::getTargetCompatibility)));
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
