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
import java.nio.file.Path;
import java.util.List;

import javax.inject.Inject;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenScope;
import io.helidon.service.application.codegen.ApplicationGenerator;
import io.helidon.service.application.codegen.ApplicationGeneratorConfig;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.workers.WorkAction;

abstract class GenerateBindingsWorkAction implements WorkAction<GenerateBindingsParameters> {
    private static final Logger LOGGER = Logging.getLogger(GenerateBindingsWorkAction.class);

    /**
     * Constructor used by Gradle worker instantiation.
     */
    @Inject
    @SuppressWarnings("checkstyle:RedundantModifier") // Gradle generates constructors from Class.getConstructors().
    public GenerateBindingsWorkAction() {
    }

    @Override
    public void execute() {
        var parameters = getParameters();
        List<Path> classpath = paths(parameters.getApplicationClasspath());
        var builder = ApplicationGeneratorConfig.builder()
                .classpath(classpath)
                .modulepath(classpath)
                .sourceRoots(paths(parameters.getSourceRoots()))
                .mainSourceRoots(paths(parameters.getMainSourceRoots()))
                .testSourceRoots(paths(parameters.getTestSourceRoots()))
                .generatedSourceDirectory(parameters.getGeneratedSourceDirectory().get().getAsFile().toPath())
                .outputDirectory(parameters.getClassesDirectory().get().getAsFile().toPath())
                .release(parameters.getRelease().get())
                .scope(switch (parameters.getScope().get()) {
                    case "main" -> CodegenScope.PRODUCTION;
                    case "test" -> new CodegenScope("test");
                    default -> throw new GradleException("Unsupported generation scope: " + parameters.getScope().get());
                })
                .bindingClassName(parameters.getBindingClassName().get())
                .mainClassName(parameters.getMainClassName().get())
                .generateBinding(parameters.getGenerateBinding().get())
                .generateMain(parameters.getGenerateMain().get())
                .validate(parameters.getValidate().get())
                .failOnError(parameters.getFailOnError().get())
                .failOnWarning(parameters.getFailOnWarning().get())
                .compilerArgs(parameters.getCompilerArgs().get());
        if (parameters.getPackageName().isPresent()) {
            builder.packageName(parameters.getPackageName().get());
        }
        if (parameters.getModuleName().isPresent()) {
            builder.moduleName(parameters.getModuleName().get());
        }
        ApplicationGenerator.generate(builder.build(), GenerateBindingsWorkAction::log);
    }

    private static List<Path> paths(FileCollection files) {
        return files.getFiles().stream().map(File::toPath).toList();
    }

    private static void log(CodegenEvent event) {
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

    private static void log(LogLevel level, CodegenEvent event) {
        if (event.throwable().isPresent()) {
            LOGGER.log(level, event.message(), event.throwable().get());
        } else {
            LOGGER.log(level, event.message());
        }
    }
}
