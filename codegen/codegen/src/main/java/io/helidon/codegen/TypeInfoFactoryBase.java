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

package io.helidon.codegen;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Common code for type info factories.
 */
public abstract class TypeInfoFactoryBase {
    private static final Set<TypeName> IGNORED_ANNOTATIONS = Set.of(TypeName.create(SuppressWarnings.class),
                                                                    TypeName.create(Override.class),
                                                                    TypeName.create(Target.class),
                                                                    TypeName.create(Retention.class),
                                                                    TypeName.create(Repeatable.class));

    /**
     * There are no side effects of this constructor.
     * All provided methods are static.
     */
    protected TypeInfoFactoryBase() {
    }

    /**
     * Map a type using context type mappers.
     *
     * @param ctx  code generation context
     * @param type type to map
     * @return result of mapping
     */
    protected static Optional<TypeInfo> mapType(CodegenContext ctx, TypeInfo type) {
        TypeInfo toReturn = type;
        for (TypeMapper typeMapper : ctx.typeMappers()) {
            if (typeMapper.supportsType(toReturn)) {
                Optional<TypeInfo> mapped = typeMapper.map(ctx, toReturn);
                if (mapped.isEmpty()) {
                    // type was removed
                    return mapped;
                }
                toReturn = mapped.get();
            }
        }
        return Optional.of(toReturn);
    }

    /**
     * Map an element using context type mappers.
     *
     * @param ctx     code generation context
     * @param element element to map
     * @return result of mapping
     */
    protected static Optional<TypedElementInfo> mapElement(CodegenContext ctx, TypedElementInfo element) {
        TypedElementInfo toReturn = element;
        for (ElementMapper elementMapper : ctx.elementMappers()) {
            if (elementMapper.supportsElement(toReturn)) {
                Optional<TypedElementInfo> mapped = elementMapper.mapElement(ctx, toReturn);
                if (mapped.isEmpty()) {
                    return mapped;
                }
                toReturn = mapped.get();
            }
        }
        return Optional.of(toReturn);
    }

    /**
     * Map an annotation using context type mappers.
     *
     * @param ctx        code generation context
     * @param annotation annotation to map
     * @param kind       element kind of the annotated element
     * @return result of mapping
     */
    protected static List<Annotation> mapAnnotation(CodegenContext ctx, Annotation annotation, ElementKind kind) {
        List<Annotation> toProcess = new ArrayList<>();
        toProcess.add(annotation);

        for (AnnotationMapper annotationMapper : ctx.annotationMappers()) {
            List<Annotation> nextToProcess = new ArrayList<>();
            for (Annotation annot : toProcess) {
                if (annotationMapper.supportsAnnotation(annot)) {
                    nextToProcess.addAll(annotationMapper.mapAnnotation(ctx, annot, kind));
                } else {
                    nextToProcess.add(annot);
                }
            }
            toProcess = nextToProcess;
        }
        return toProcess;
    }

    /**
     * A filter for annotations to exclude ones we are not interested in ({@link java.lang.SuppressWarnings},
     * {@link java.lang.Override}, {@link java.lang.annotation.Target}, {@link java.lang.annotation.Retention},
     * {@link java.lang.annotation.Repeatable}.
     *
     * @param annotation annotation to check
     * @return whether the annotation should be included
     */
    protected static boolean annotationFilter(Annotation annotation) {
        return !IGNORED_ANNOTATIONS.contains(annotation.typeName());
    }

    /**
     * Map a string representation of a modifier to its Helidon counterpart.
     *
     * @param ctx             code generation context
     * @param stringModifiers set of modifiers
     * @return set of Helidon modifiers (without visibility modifiers)
     */
    protected static Set<io.helidon.common.types.Modifier> modifiers(CodegenContext ctx, Set<String> stringModifiers) {
        Set<io.helidon.common.types.Modifier> result = new HashSet<>();

        for (String stringModifier : stringModifiers) {
            try {
                result.add(io.helidon.common.types.Modifier.valueOf(stringModifier.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                // we do not care about modifiers we do not understand - either access modifier, or something new
                ctx.logger().log(System.Logger.Level.TRACE,
                                 "Modifier " + stringModifier + " not understood by type info factory.");
            }
        }

        return result;
    }

    /**
     * Check if the provided type is either a primitive type, or is from the {@code java} package namespace.
     *
     * @param type type to check
     * @return {@code true} if the type is a primitive type, or its package starts with {@code java.}
     */
    protected static boolean isBuiltInJavaType(TypeName type) {
        return type.primitive() || type.packageName().startsWith("java.");
    }

}
