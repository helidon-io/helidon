/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;

import static io.helidon.builder.codegen.Types.LINKED_HASH_SET;
import static io.helidon.common.types.TypeNames.SET;

class TypeHandlerSet extends TypeHandlerCollection {

    TypeHandlerSet(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option) {
        super(extensions,
              prototypeInfo,
              option,
              SET,
              LINKED_HASH_SET,
              content -> content.addContent("collect(")
                      .addContent(Collectors.class)
                      .addContent(".toSet())"),
              Optional.of(content -> content.addContent(".map(")
                      .addContent(SET)
                      .addContent("::copyOf)")));
    }

    @Override
    protected String decoratorSetMethodName() {
        return "decorateSetSet";
    }

    @Override
    protected String decoratorAddMethodName() {
        return "decorateAddSet";
    }
}
