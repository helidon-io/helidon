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
import java.nio.file.Path;

import io.helidon.codegen.CodegenScope;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static io.helidon.inject.codegen.InjectionCodegenContext.APPLICATION_NAME;

/**
 * A mojo wrapper to {@link ApplicationCreator}.
 */
@Mojo(name = "application-create", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class ApplicationCreatorMojo extends AbstractApplicationCreatorMojo {

    /**
     * The classname to use for the {@link io.helidon.inject.Application} class.
     * If not found the classname will be inferred.
     */
    @Parameter(property = "inject.application.class.name",
               defaultValue = APPLICATION_NAME)
    private String className;

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
     * Default constructor.
     */
    public ApplicationCreatorMojo() {
    }

    @Override
    protected String getGeneratedClassName() {
        return className;
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
}
