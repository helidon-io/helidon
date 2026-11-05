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

package io.helidon.service.application.codegen;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.codegen.CodegenScope;
import io.helidon.common.Api;

/**
 * Configuration for build-time discovery, application binding generation, and compilation.
 */
@Api.Incubating
@Prototype.Blueprint
interface ApplicationGeneratorConfigBlueprint {

    /**
     * Application runtime classpath.
     *
     * @return classpath
     */
    @Option.Singular
    List<Path> classpath();

    /**
     * Application module path, when compiling a named module.
     *
     * @return modulepath
     */
    @Option.Singular
    List<Path> modulepath();

    /**
     * Source roots for the generation scope.
     *
     * @return sourceRoots
     */
    @Option.Singular
    List<Path> sourceRoots();

    /**
     * Production source roots used to infer the generated package.
     *
     * @return mainSourceRoots
     */
    @Option.Singular
    List<Path> mainSourceRoots();

    /**
     * Test source roots used as a fallback to infer the generated package.
     *
     * @return testSourceRoots
     */
    @Option.Singular
    List<Path> testSourceRoots();

    /**
     * Additional Java compiler arguments, including annotation processor options.
     *
     * @return compilerArgs
     */
    @Option.Singular
    List<String> compilerArgs();

    /**
     * Directory for generated Java source files.
     *
     * @return generatedSourceDirectory
     */
    Path generatedSourceDirectory();

    /**
     * Directory for generated classes and resources.
     *
     * @return outputDirectory
     */
    Path outputDirectory();

    /**
     * Java release used to compile generated sources.
     *
     * @return release
     */
    @Option.Default("27")
    String release();

    /**
     * Generation scope.
     *
     * @return scope
     */
    @Option.Default("PRODUCTION")
    CodegenScope scope();

    /**
     * Simple name of the generated binding class.
     *
     * @return bindingClassName
     */
    @Option.Default("ApplicationBinding")
    String bindingClassName();

    /**
     * Simple name of the optional generated application main class.
     *
     * @return mainClassName
     */
    @Option.Default("ApplicationMain")
    String mainClassName();

    /**
     * Explicit package name, otherwise inferred from processor options, module exports, or source roots.
     *
     * @return packageName
     */
    Optional<String> packageName();

    /**
     * Explicit module name, otherwise inferred from processor options, module descriptor, or package.
     *
     * @return moduleName
     */
    Optional<String> moduleName();

    /**
     * Whether to generate an application binding.
     *
     * @return generateBinding
     */
    @Option.DefaultBoolean(true)
    boolean generateBinding();

    /**
     * Whether to generate an application main class for production scope.
     *
     * @return generateMain
     */
    @Option.DefaultBoolean(false)
    boolean generateMain();

    /**
     * Whether to validate application dependencies before generating bindings.
     *
     * @return validate
     */
    @Option.DefaultBoolean(true)
    boolean validate();

    /**
     * Whether generation errors fail the build.
     *
     * @return failOnError
     */
    @Option.DefaultBoolean(true)
    boolean failOnError();

    /**
     * Whether validation warnings fail the build.
     *
     * @return failOnWarning
     */
    @Option.DefaultBoolean(false)
    boolean failOnWarning();
}
