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

/**
 * Code generation and processing support.
 * <p>
 * The main type to start with is {@link io.helidon.codegen.Codegen}, that is responsible for discovering all extensions on the
 * classpath, to understand what annotations they are interested in, and then invoking them as needed.
 * This type is expected to be called from an annotation processor, Maven plugin, or a command line tool (or any other tool
 * capable of analyzing sources or byte code and/or generating new types.
 *
 * @see io.helidon.codegen.CodegenUtil
 */
package io.helidon.codegen;
