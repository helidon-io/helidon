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

package io.helidon.pico.tools;

import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.Builder;

/**
 * Provides configuration to the javac compiler.
 */
@Builder
public interface CompilerOptions {

    /**
     * The classpath to pass to the compiler.
     *
     * @return classpath
     */
    List<Path> classpath();

    /**
     * The modulepath to pass to the compiler.
     *
     * @return the module path
     */
    List<Path> modulepath();

    /**
     * The source path to pass to the compiler.
     *
     * @return the source path
     */
    List<Path> sourcepath();

    /**
     * The command line arguments to pass to the compiler.
     *
     * @return arguments
     */
    List<String> commandLineArguments();

    /**
     * The compiler source version.
     *
     * @return source version
     */
    String source();

    /**
     * The compiler target version.
     *
     * @return target version
     */
    String target();

}
