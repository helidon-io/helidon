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

package io.helidon.service.codegen.spi;

import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;

/**
 * Assignment for code generation. The original intended purpose is to support {@code Provider} from javax and jakarta
 * without a dependency (or need to understand it) in the generator code.
 *
 * @param usedType      type to use as the dependency type using only Helidon supported types
 *                      (i.e. {@link java.util.function.Supplier} instead of jakarta {@code Provider}
 * @param codeGenerator code generator that creates appropriate type required by the target
 */
record AssignmentImpl(TypeName usedType, Consumer<ContentBuilder<?>> codeGenerator)
        implements InjectAssignment.Assignment {
}
