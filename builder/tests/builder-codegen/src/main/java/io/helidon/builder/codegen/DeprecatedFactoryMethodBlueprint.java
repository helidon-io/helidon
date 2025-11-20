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

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Factory methods for the deprecated annotation.
 * <p>
 * The following method types can be hidden behind this method:
 * <ul>
 *     <li>Factory method that creates an option type from Config</li>
 *     <li>Factory method that creates an option type from a prototype (to handle third party types)</li>
 *     <li>Factory method to be copied to the generated prototype interface</li>
 * </ul>
 *
 * @deprecated this is only present for backward compatibility and will be removed in a future version
 */
@Prototype.Blueprint(detach = true)
@Deprecated(forRemoval = true, since = "4.4.0")
interface DeprecatedFactoryMethodBlueprint {
    /**
     * Referenced method.
     *
     * @return referenced method definition
     */
    TypedElementInfo method();

    /**
     * Type declaring the (static) factory method.
     *
     * @return declaring type
     */
    TypeName declaringType();
}
