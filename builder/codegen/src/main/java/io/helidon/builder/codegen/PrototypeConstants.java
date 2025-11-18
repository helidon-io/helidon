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

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypeName;

final class PrototypeConstants {
    private PrototypeConstants() {
    }

    public static PrototypeConstant create(TypeName declaringType,
                                           TypeName constantType,
                                           String constantName,
                                           Javadoc javadoc) {
        Consumer<ContentBuilder<?>> consumer = content -> {
            content.addContent(declaringType)
                    .addContent(".")
                    .addContent(constantName);
        };

        return PrototypeConstant.builder()
                .content(consumer)
                .name(constantName)
                .type(constantType)
                .javadoc(javadoc)
                .build();
    }

    public static PrototypeConstant create(TypeName constantType,
                                           String constantName,
                                           Javadoc javadoc,
                                           Consumer<ContentBuilder<?>> consumer) {

        return PrototypeConstant.builder()
                .content(consumer)
                .name(constantName)
                .type(constantType)
                .javadoc(javadoc)
                .build();
    }
}
