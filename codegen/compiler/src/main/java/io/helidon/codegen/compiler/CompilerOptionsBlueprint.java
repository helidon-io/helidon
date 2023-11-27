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

package io.helidon.codegen.compiler;

import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.codegen.CodegenLogger;

/**
 * Provides configuration to the javac compiler.
 */
@Prototype.Blueprint
interface CompilerOptionsBlueprint {

    /**
     * The classpath to pass to the compiler.
     *
     * @return classpath
     */
    @Option.Singular
    List<Path> classpath();

    /**
     * The modulepath to pass to the compiler.
     *
     * @return the module path
     */
    @Option.Singular
    List<Path> modulepath();

    /**
     * The source path to pass to the compiler.
     *
     * @return the source path
     */
    @Option.Singular
    List<Path> sourcepath();

    /**
     * The command line arguments to pass to the compiler.
     *
     * @return arguments
     */
    @Option.Singular
    List<String> commandLineArguments();

    /**
     * The compiler source version.
     *
     * @return source version
     */
    @Option.Default("21")
    String source();

    /**
     * The compiler target version.
     *
     * @return target version
     */
    @Option.Default("21")
    String target();

    /**
     * Target directory to generate class files to.
     *
     * @return output directory
     */
    Path outputDirectory();

    /**
     * Logger to use, falls back to system logger.
     *
     * @return logger
     */
    @Option.DefaultCode("@io.helidon.codegen.CodegenLogger@.create(System.getLogger(\"io.helidon.codegen.compiler.Compiler\"))")
    CodegenLogger logger();
}
