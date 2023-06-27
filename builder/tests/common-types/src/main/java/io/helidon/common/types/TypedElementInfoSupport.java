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

package io.helidon.common.types;

import java.util.stream.Collectors;

import io.helidon.builder.api.Prototype;

final class TypedElementInfoSupport {
    private TypedElementInfoSupport() {
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override")
    static String toString(TypedElementInfo me) {
        StringBuilder builder = new StringBuilder();
        if (!TypeValues.KIND_PARAMETER.equals(me.elementTypeKind())) {
            me.enclosingType()
                    .ifPresent(enclosingTypeName -> builder.append(enclosingTypeName).append("::"));
        }
        builder.append(me.toDeclaration());
        return builder.toString();
    }

    /**
     * Provides a description for this instance.
     *
     * @return provides the {typeName}{space}{elementName}
     */
    @Prototype.PrototypeMethod
    static String toDeclaration(TypedElementInfo me) {
        StringBuilder builder = new StringBuilder();
        builder.append(me.typeName()).append(" ").append(me.elementName());
        String params = me.parameterArguments().stream()
                .map(it -> it.typeName() + " " + it.elementName())
                .collect(Collectors.joining(", "));
        if (!params.isBlank()) {
            builder.append("(").append(params).append(")");
        }
        return builder.toString();
    }

}
