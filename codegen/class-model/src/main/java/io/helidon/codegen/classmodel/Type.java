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
package io.helidon.codegen.classmodel;

import java.util.List;
import java.util.Optional;

import io.helidon.common.types.TypeName;

abstract class Type extends ModelComponent {

    Type(Builder<?, ?> builder) {
        super(builder);
    }

    static Type fromTypeName(TypeName typeName) {
        if (typeName instanceof TypeArgument argument) {
            return argument;
        }
        if (typeName.typeArguments().isEmpty()) {
            if (typeName.array()
                    || Optional.class.getName().equals(typeName.declaredName())) {
                return ConcreteType.builder()
                        .type(typeName)
                        .build();
            } else if (typeName.wildcard()) {
                List<TypeName> upperBounds = typeName.upperBounds();
                if (upperBounds.isEmpty()) {
                    if (typeName.lowerBounds().isEmpty()) {
                        return TypeArgument.create("?");
                    }
                    return TypeArgument.builder()
                            .token("?")
                            .bound(typeName.lowerBounds().getFirst())
                            .lowerBound(true)
                            .build();
                }

                return TypeArgument.builder()
                        .token("?")
                        .bound(upperBounds.getFirst())
                        .build();
            }
            return ConcreteType.builder()
                    .type(typeName)
                    .build();
        }
        ConcreteType.Builder typeBuilder = ConcreteType.builder()
                .type(typeName);
        typeName.typeArguments()
                .forEach(typeBuilder::addParam);
        return typeBuilder.build();
    }

    abstract TypeName typeName();

    abstract String fqTypeName();
    abstract String resolvedTypeName();

    abstract String packageName();

    abstract String simpleTypeName();

    abstract boolean isArray();

    abstract boolean innerClass();

    abstract Optional<Type> declaringClass();

    abstract TypeName genericTypeName();

}
