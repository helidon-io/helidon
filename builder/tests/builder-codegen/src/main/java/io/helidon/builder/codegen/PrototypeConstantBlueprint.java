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

import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypeName;

/**
 * Custom constant definition. This constant will be code generated on the prototype interface.
 */
@Prototype.Blueprint(detach = true)
interface PrototypeConstantBlueprint {
    /**
     * Name of the constant.
     *
     * @return field name
     */
    String name();

    /**
     * Type of the constant.
     *
     * @return field type
     */
    TypeName type();

    /**
     * Javadoc for the constant.
     *
     * @return javadoc
     */
    Javadoc javadoc();

    /**
     * Consumer of the content to generate the constant.
     *
     * @return content builder consumer to generate the constant value
     */
    Consumer<ContentBuilder<?>> content();
}
