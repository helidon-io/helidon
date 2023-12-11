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
package io.helidon.codegen.classmodel;

import java.util.Optional;
import java.util.stream.Collectors;

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
                boolean isObject = typeName.name().equals("?") || Object.class.getName().equals(typeName.name());
                if (isObject) {
                    return TypeArgument.create("?");
                } else {
                    return TypeArgument.builder()
                            .token("?")
                            .bound(extractBoundTypeName(typeName.genericTypeName()))
                            .build();
                }
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

    private static String extractBoundTypeName(TypeName instance) {
        String name = calcName(instance);
        StringBuilder nameBuilder = new StringBuilder(name);

        if (!instance.typeArguments().isEmpty()) {
            nameBuilder.append('<')
                    .append(instance.typeArguments()
                                    .stream()
                                    .map(TypeName::resolvedName)
                                    .collect(Collectors.joining(", ")))
                    .append('>');
        }

        if (instance.array()) {
            nameBuilder.append("[]");
        }

        return nameBuilder.toString();
    }

    private static String calcName(TypeName instance) {
        String className;
        if (instance.enclosingNames().isEmpty()) {
            className = instance.className();
        } else {
            className = String.join(".", instance.enclosingNames()) + "." + instance.className();
        }

        return (instance.primitive() || instance.packageName().isEmpty())
                ? className : instance.packageName() + "." + className;
    }

    abstract String fqTypeName();
    abstract String resolvedTypeName();

    abstract String packageName();

    abstract String simpleTypeName();

    abstract boolean isArray();

    abstract boolean innerClass();

    abstract Optional<Type> declaringClass();

    abstract TypeName genericTypeName();

}
