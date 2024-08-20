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

package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.common.types.TypeNames.SET;

class TypeHandlerSet extends TypeHandlerCollection {

    TypeHandlerSet(TypeName blueprintType,
                   TypedElementInfo annotatedMethod,
                   String name, String getterName, String setterName, TypeName declaredType) {
        super(blueprintType,
              annotatedMethod,
              name,
              getterName,
              setterName,
              declaredType,
              SET,
              "collect(java.util.stream.Collectors.toSet())",
              Optional.of(".map(java.util.Set::copyOf)"));
    }
}
