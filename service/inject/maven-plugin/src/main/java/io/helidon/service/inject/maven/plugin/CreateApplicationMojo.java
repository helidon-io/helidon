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

import java.io.File;
import java.nio.file.Path;

import io.helidon.codegen.CodegenScope;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Maven goal to create application bindings (a mapping of services that satisfy injection points),
 * and to create application main class (reflection-free registration of services).
 */
@Mojo(name = "create-application",
      defaultPhase = LifecyclePhase.COMPILE,
      threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CreateApplicationMojo extends CreateApplicationAbstractMojo {

    /**
     * Specify where to place generated source files created by annotation processing.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations")
    private File generatedSourcesDirectory;

    /**
     * The directory for compiled classes.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private File outputDirectory;

    /**
     * Whether to generate main class. Default name is {@code ApplicationMain} in the same package as
     * the generated application.
     */
    @Parameter(property = "helidon.inject.application.main.generate",
               defaultValue = "true")
    private boolean generateMain;

    /**
     * Name of the generated binding class.
     */
    @Parameter(property = "helidon.inject.application.binding.class.name",
               defaultValue = BINDING_CLASS_NAME)
    private String bindingClassName;

    /**
     * Default constructor.
     */
    public CreateApplicationMojo() {
    }

    @Override
    protected Path generatedSourceDirectory() {
        return generatedSourcesDirectory.toPath();
    }

    @Override
    protected Path outputDirectory() {
        return outputDirectory.toPath();
    }

    @Override
    protected CodegenScope scope() {
        return CodegenScope.PRODUCTION;
    }

    @Override
    String bindingClassName() {
        return bindingClassName;
    }

    @Override
    boolean createMain() {
        return generateMain;
    }
}
