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

package io.helidon.service.maven.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

import io.helidon.codegen.CodegenScope;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * A mojo wrapper to {@link BindingGenerator} for test specific types.
 * For test scope, we only generate binding, as main class would not be useful.
 */
@Mojo(name = "test-application-create", defaultPhase = LifecyclePhase.TEST_COMPILE, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.TEST)
@SuppressWarnings("unused")
public class CreateTestApplicationMojo extends CreateApplicationAbstractMojo {

    /**
     * Name of the generated binding class.
     */
    @Parameter(property = "helidon.service.registry.binding.classname",
               defaultValue = "Test" + BINDING_CLASS_NAME)
    private String bindingClassName;

    /**
     * Specify where to place generated source files created by annotation processing.
     * Only applies to JDK 1.6+
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/test-annotations")
    private File generatedTestSourcesDirectory;

    /**
     * The directory where compiled test classes go.
     */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true, readonly = true)
    private File testOutputDirectory;

    /**
     * Default constructor.
     */
    public CreateTestApplicationMojo() {
    }

    @Override
    boolean generateMain() {
        return false;
    }

    @Override
    protected Path generatedSourceDirectory() {
        return generatedTestSourcesDirectory.toPath();
    }

    @Override
    protected Path outputDirectory() {
        return testOutputDirectory.toPath();
    }

    @Override
    protected List<Path> sourceRootPaths() {
        return testSourceRootPaths();
    }

    @Override
    protected LinkedHashSet<Path> getClasspathElements() {
        MavenProject project = mavenProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>(project.getTestArtifacts().size());
        result.add(new File(project.getBuild().getTestOutputDirectory()).toPath());
        for (Object a : project.getTestArtifacts()) {
            result.add(((Artifact) a).getFile().toPath());
        }
        result.addAll(super.getClasspathElements());
        return result;
    }

    @Override
    LinkedHashSet<Path> getModulepathElements() {
        return getClasspathElements();
    }

    @Override
    protected String bindingClassName() {
        return bindingClassName;
    }

    @Override
    protected CodegenScope scope() {
        return new CodegenScope("test");
    }
}
