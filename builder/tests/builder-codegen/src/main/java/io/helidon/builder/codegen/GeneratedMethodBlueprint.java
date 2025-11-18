/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypedElementInfo;

/**
 * A method to be generated.
 * <p>
 * Rules for referenced static custom methods:
 * <ul>
 *     <li>The first parameter must be the Prototype type for custom prototype methods</li>
 *     <li>The first parameter must be the BuilderBase type for custom builder methods</li>
 *     <li>Custom factory methods are simply referenced</li>
 * </ul>
 */
@Prototype.Blueprint(detach = true)
interface GeneratedMethodBlueprint {
    /**
     * Definition of this method, including annotations (such as {@link java.lang.Override}).
     *
     * @return method definition
     */
    TypedElementInfo method();

    /**
     * Generator for the method content.

     * @return content builder consumer
     */
    Consumer<ContentBuilder<?>> contentBuilder();

    /**
     * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
     * complicated to update it.
     * <p>
     * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
     *
     * @return javadoc for this method if defined
     */
    Optional<Javadoc> javadoc();
}
