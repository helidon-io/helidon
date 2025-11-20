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

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

final class Utils {
    private Utils() {
    }

    static List<OptionInfo> options(List<OptionHandler> options) {
        return options.stream()
                .map(OptionHandler::option)
                .collect(Collectors.toUnmodifiableList());
    }

    static void addGeneratedMethod(ClassBase.Builder<?, ?> classModel,
                                   GeneratedMethod generatedMethod) {
        var methodInfo = generatedMethod.method();
        var javadoc = generatedMethod.javadoc();
        var contentBuilder = generatedMethod.contentBuilder();

        classModel.addMethod(method -> {
            method.from(methodInfo)
                    .update(contentBuilder::accept);
            javadoc.ifPresent(method::javadoc);
        });
    }

    /*
        Will only create wildcards where it makes at least some sense.
        We do not analyze the actual class if it is final though.
         */
    static TypeName toWildcard(TypeName typeName) {
        if (typeName.wildcard()) {
            return typeName;
        }

        if (typeName.generic()) {
            return TypeName.builder()
                    .className(typeName.className())
                    .wildcard(true)
                    .build();
        }

        if (TypeNames.STRING.equals(typeName) || typeName.unboxed().primitive() || typeName.array()) {
            return typeName;
        }

        return TypeName.builder(typeName).wildcard(true).build();
    }

    static TypeName builderReturnType() {
        return TypeName.createFromGenericDeclaration("BUILDER");
    }

    static String deCapitalize(String string) {
        if (string.isBlank()) {
            return string;
        }
        return Character.toLowerCase(string.charAt(0)) + string.substring(1);
    }

    static TypeName prototypeBuilderType(TypeName prototypeType) {
        return TypeName.builder()
                .packageName(prototypeType.packageName())
                .className("Builder")
                .addEnclosingName(prototypeType.className())
                .build();
    }

    /**
     * A few types that we support are "containers" that contain another type.
     * This method returns the underlying type.
     * <p>
     * Supported "container" types:
     * <ul>
     *     <li>{@link java.util.Optional}</li>
     *     <li>{@link java.util.List}</li>
     *     <li>{@link java.util.Map}</li>
     *     <li>{@link java.util.Set}</li>
     *     <li>{@link java.util.function.Supplier}</li>
     * </ul>
     *
     * @param declaredType type as declared on the blueprint
     * @return the real type of the property
     */
    static TypeName realType(TypeName declaredType) {
        // implementation of this method MUST match TypeHandler implementations

        if (declaredType.isOptional()
                || declaredType.isList()
                || declaredType.isSet()
                || declaredType.isSupplier()) {
            return declaredType.typeArguments().getFirst();
        }

        if (declaredType.isMap()) {
            return declaredType.typeArguments().get(1);
        }

        return declaredType;
    }

    /**
     * Check if the provided types are equal.
     * <p>
     * As we may not have full package information for types created as part of this
     * annotation processing round, if one type has empty package, we ignore package.
     *
     * @param first  first type
     * @param second second type
     * @return true if the types are equal
     */
    static boolean typesEqual(TypeName first, TypeName second) {
        var usedFirst = first;
        var usedSecond = second;

        if (first.packageName().isEmpty()) {
            usedSecond = TypeName.builder(second)
                    .packageName("")
                    .build();
        } else if (second.packageName().isEmpty()) {
            usedFirst = TypeName.builder(first)
                    .packageName("")
                    .build();
        }

        return usedFirst.equals(usedSecond);
    }

    /**
     * Check if the provided types are equal including generic information.
     * <p>
     * As we may not have full package information for types created as part of this
     * annotation processing round, if one type has empty package, we ignore package.
     *
     * @param first  first type
     * @param second second type
     * @return true if the types are equal
     */
    static boolean resolvedTypesEqual(TypeName first, TypeName second) {
        var usedFirst = first;
        var usedSecond = second;

        if (first.packageName().isEmpty()) {
            usedSecond = TypeName.builder(second)
                    .packageName("")
                    .build();
        } else if (second.packageName().isEmpty()) {
            usedFirst = TypeName.builder(first)
                    .packageName("")
                    .build();
        }
        return ResolvedType.create(usedFirst)
                .equals(ResolvedType.create(usedSecond));
    }
}
