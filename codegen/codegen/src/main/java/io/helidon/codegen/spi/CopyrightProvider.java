/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.spi;

import io.helidon.common.types.TypeName;

/**
 * Extension point to customize copyright headers for generated types.
 */
public interface CopyrightProvider {
    /**
     * Create a copyright header, including comment begin/end, or line comments.
     *
     * @param generator type of the generator (annotation processor)
     * @param trigger type of the class that caused this type to be generated
     * @param generatedType type that is going to be generated
     * @return copyright string (can be multiline)
     */
    String copyright(TypeName generator, TypeName trigger, TypeName generatedType);
}
