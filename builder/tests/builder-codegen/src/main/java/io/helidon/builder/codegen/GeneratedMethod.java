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

import io.helidon.codegen.classmodel.ContentBuilder;
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
public interface GeneratedMethod {
    /**
     * Definition of this method, including annotations.
     *
     * @return method definition
     */
    TypedElementInfo methodDefinition();

    /**
     * Update the method content.
     *
     * @param contentBuilder builder of content
     */
    void accept(ContentBuilder<?> contentBuilder);

    /**
     * Whether this method overrides an existing method from a super class/interface.
     *
     * @return whether this is an override
     */
    default boolean override() {
        return false;
    }
}
