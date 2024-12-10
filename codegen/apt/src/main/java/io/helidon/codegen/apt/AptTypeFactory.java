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

package io.helidon.codegen.apt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.common.types.TypeName.createFromGenericDeclaration;

/**
 * Factory for types.
 *
 * @deprecated this is intended for internal use. This will be a package private type in the future.
 */
@Deprecated(forRemoval = true, since = "4.2.0")
public final class AptTypeFactory {
    private static final Pattern NESTED_TYPES = Pattern.compile("(?<!\\$)\\$(?!\\$)");
    private AptTypeFactory() {
    }

    /**
     * Creates a name from a declared type during annotation processing.
     *
     * @param type the element type
     * @return the associated type name instance
     */
    public static Optional<TypeName> createTypeName(DeclaredType type) {
        return createTypeName(type.asElement());
    }

    /**
     * Create type from type mirror.
     *
     * @param typeMirror annotation processing type mirror
     * @return type name
     * @throws IllegalArgumentException when the mirror cannot be resolved into a name (such as when it represents
     *                                            none or error)
     */
    public static Optional<TypeName> createTypeName(TypeMirror typeMirror) {
        return createTypeName(new HashSet<>(), typeMirror);
    }

    private static Optional<TypeName> createTypeName(Set<TypeMirror> inProgress, TypeMirror typeMirror) {
        TypeKind kind = typeMirror.getKind();
        if (kind.isPrimitive()) {
            Class<?> type = switch (kind) {
                case BOOLEAN -> boolean.class;
                case BYTE -> byte.class;
                case SHORT -> short.class;
                case INT -> int.class;
                case LONG -> long.class;
                case CHAR -> char.class;
                case FLOAT -> float.class;
                case DOUBLE -> double.class;
                default -> throw new IllegalStateException("Unknown primitive type: " + kind);
            };

            return Optional.of(TypeName.create(type));
        }

        switch (kind) {
        case VOID -> {
            return Optional.of(TypeName.create(void.class));
        }
        case TYPEVAR -> {
            if (!inProgress.add(typeMirror)) {
                return Optional.empty(); // prevent infinite loop
            }

            try {
                var builder = TypeName.builder(createFromGenericDeclaration(typeMirror.toString()));

                var typeVar = ((TypeVariable) typeMirror);
                handleBounds(inProgress, typeVar.getUpperBound(), builder::addUpperBound);
                handleBounds(inProgress, typeVar.getLowerBound(), builder::addLowerBound);

                return Optional.of(builder.build());
            } finally {
                inProgress.remove(typeMirror);
            }
        }
        case WILDCARD -> {
            WildcardType vt = ((WildcardType) typeMirror);
            var builder = TypeName.builder()
                    .generic(true)
                    .wildcard(true)
                    .className("?");

            handleBounds(inProgress, vt.getExtendsBound(), builder::addUpperBound);
            handleBounds(inProgress, vt.getSuperBound(), builder::addLowerBound);

            return Optional.of(builder.build());
        }
        case ERROR -> {
            return Optional.of(TypeName.create(typeMirror.toString()));
        }
        // this is most likely a type that is code generated as part of this round, best effort
        case NONE -> {
            return Optional.empty();
        }
        default -> {
        }
        // fall through
        }

        if (typeMirror instanceof ArrayType arrayType) {
            return Optional.of(TypeName.builder(createTypeName(inProgress, arrayType.getComponentType()).orElseThrow())
                                       .array(true)
                                       .build());
        }

        if (typeMirror instanceof DeclaredType declaredType) {
            List<TypeName> typeParams = declaredType.getTypeArguments()
                    .stream()
                    .map(it -> createTypeName(inProgress, it))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());

            TypeName result = createTypeName(inProgress, declaredType.asElement()).orElse(null);
            if (typeParams.isEmpty() || result == null) {
                return Optional.ofNullable(result);
            }

            if (!inProgress.add(typeMirror)) {
                return Optional.empty(); // prevent infinite loop
            }
            return Optional.of(TypeName.builder(result).typeArguments(typeParams).build());
        }

        throw new IllegalStateException("Unknown type mirror: " + typeMirror);
    }

    private static void handleBounds(Set<TypeMirror> processed, TypeMirror boundMirror, Consumer<TypeName> boundHandler) {
        if (boundMirror == null) {
            return;
        }
        if (boundMirror.getKind() != TypeKind.NULL) {
            if (boundMirror.getKind() == TypeKind.INTERSECTION) {
                IntersectionType it = (IntersectionType) boundMirror;
                it.getBounds()
                        .stream()
                        .filter(Predicate.not(processed::equals))
                        .map(typeMirror -> createTypeName(processed, typeMirror))
                        .flatMap(Optional::stream)
                        .filter(Predicate.not(TypeNames.OBJECT::equals))
                        .forEach(boundHandler);

            } else {
                createTypeName(processed, boundMirror)
                        .filter(Predicate.not(TypeNames.OBJECT::equals))
                        .ifPresent(boundHandler);
            }
        }
    }

    /**
     * Create type from type mirror. The element is needed to correctly map
     * type arguments to type parameters.
     *
     * @param element the type element of the type mirror
     * @param mirror the type mirror as declared in source code
     * @return type for the provided values
     */
    public static Optional<TypeName> createTypeName(TypeElement element, TypeMirror mirror) {
        Optional<TypeName> result = createTypeName(new HashSet<>(), mirror);
        if (result.isEmpty()) {
            return result;
        }

        TypeName mirrorName = result.get();
        int typeArgumentSize = mirrorName.typeArguments().size();

        List<String> typeParameters = element.getTypeParameters()
                .stream()
                .map(TypeParameterElement::toString)
                .toList();
        if (typeArgumentSize > typeParameters.size()) {
            throw new IllegalStateException("Found " + typeArgumentSize + " type arguments, but only " + typeParameters.size()
                                                    + " type parameters on: " + mirror);
        }
        return Optional.of(TypeName.builder(mirrorName)
                                   .typeParameters(typeParameters)
                                   .build());
    }

    /**
     * Creates a name from an element type during annotation processing.
     *
     * @param type the element type
     * @return the associated type name instance
     */
    public static Optional<TypeName> createTypeName(Element type) {
        return createTypeName(new HashSet<>(), type);
    }

    private static Optional<TypeName> createTypeName(Set<TypeMirror> processed, Element type) {
        if (type instanceof VariableElement) {
            return createTypeName(processed, type.asType());
        }

        if (type instanceof ExecutableElement ee) {
            return createTypeName(processed, ee.getReturnType());
        }

        if (type.getKind() == ElementKind.TYPE_PARAMETER) {
            TypeMirror mirror = type.asType();
            return createTypeName(processed, mirror);
        }

        List<String> classNames = new ArrayList<>();
        String simpleName = type.getSimpleName().toString();

        // if there is a single $, we consider that to be an inner class
        String[] split = NESTED_TYPES.split(simpleName);
        if (split.length > 1) {
            classNames.addAll(Arrays.asList(split)
                                      .subList(0, split.length - 1));
            simpleName = split[split.length - 1];
        }

        Element enclosing = type.getEnclosingElement();
        while (enclosing != null && ElementKind.PACKAGE != enclosing.getKind()) {
            if (enclosing.getKind() == ElementKind.CLASS
                    || enclosing.getKind() == ElementKind.INTERFACE
                    || enclosing.getKind() == ElementKind.ANNOTATION_TYPE
                    || enclosing.getKind() == ElementKind.RECORD) {
                classNames.add(enclosing.getSimpleName().toString());
            }
            enclosing = enclosing.getEnclosingElement();
        }

        Collections.reverse(classNames);

        // try to find the package
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        String packageName = enclosing == null ? "" : ((PackageElement) enclosing).getQualifiedName().toString();

        // the package name may be our enclosing type, if the type in question is created as part of annotation processing
        // in this round; as we want to support this (e.g. production classes depend on generated classes), we need to resolve it
        if (!packageName.isEmpty() && Character.isUpperCase(packageName.charAt(0))) {
            classNames = List.of(packageName.split("\\."));
            packageName = "";
        }

        return Optional.of(TypeName.builder()
                                   .packageName(packageName)
                                   .className(simpleName)
                                   .enclosingNames(classNames)
                                   .build());
    }
}
