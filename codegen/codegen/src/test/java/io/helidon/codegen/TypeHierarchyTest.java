/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;

class TypeHierarchyTest {
    private static final TypeName NOT_BLANK = TypeName.create("io.helidon.validation.Validation.String.NotBlank");
    private static final CodegenContext CTX = new EmptyCodegenContext();

    @Test
    void nestedAnnotationsIncludeDeepGenericTypeArgumentAnnotations() {
        TypeName mapType = TypeName.builder(TypeNames.MAP)
                .addTypeArgument(TypeNames.STRING)
                .addTypeArgument(TypeName.builder(TypeNames.LIST)
                                         .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                                                  .addAnnotation(Annotation.create(NOT_BLANK))
                                                                  .build())
                                         .build())
                .build();
        TypeInfo typeInfo = TypeInfo.builder()
                .typeName(TypeName.create("io.helidon.codegen.test.NestedContract"))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(TypedElementInfo.builder()
                                        .kind(ElementKind.METHOD)
                                        .elementName("validate")
                                        .typeName(TypeNames.PRIMITIVE_VOID)
                                        .addParameterArgument(TypedElementInfo.builder()
                                                                      .kind(ElementKind.PARAMETER)
                                                                      .elementName("value")
                                                                      .typeName(mapType)
                                                                      .build())
                                        .build())
                .build();

        assertThat(TypeHierarchy.nestedAnnotations(CTX, typeInfo), hasItem(NOT_BLANK));
    }

    @Test
    void typeNameAnnotationsIncludeWildcardBoundsAndArrayComponents() {
        Annotation notBlank = Annotation.create(NOT_BLANK);
        TypeName upperBoundType = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(notBlank)
                                                        .build())
                                         .build())
                .build();
        TypeName lowerBoundType = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addLowerBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(notBlank)
                                                        .build())
                                         .build())
                .build();
        TypeName componentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(notBlank)
                .build();
        TypeName arrayType = TypeName.builder(componentType)
                .array(true)
                .componentType(componentType)
                .build();

        assertThat(TypeHierarchy.typeNameAnnotations(upperBoundType), hasItem(notBlank));
        assertThat(TypeHierarchy.typeNameAnnotations(lowerBoundType), hasItem(notBlank));
        assertThat(TypeHierarchy.typeNameAnnotations(arrayType), hasItem(notBlank));
    }

    private static final class EmptyCodegenContext implements CodegenContext {
        @Override
        public Optional<ModuleInfo> module() {
            return Optional.empty();
        }

        @Override
        public CodegenFiler filer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodegenLogger logger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodegenScope scope() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodegenOptions options() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName) {
            return Optional.empty();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
            return Optional.empty();
        }

        @Override
        public List<ElementMapper> elementMappers() {
            return List.of();
        }

        @Override
        public List<TypeMapper> typeMappers() {
            return List.of();
        }

        @Override
        public List<AnnotationMapper> annotationMappers() {
            return List.of();
        }

        @Override
        public Set<TypeName> mapperSupportedAnnotations() {
            return Set.of();
        }

        @Override
        public Set<String> mapperSupportedAnnotationPackages() {
            return Set.of();
        }

        @Override
        public Set<Option<?>> supportedOptions() {
            return Set.of();
        }

        @Override
        public String uniqueName(TypeInfo typeInfo, TypedElementInfo elementInfo) {
            throw new UnsupportedOperationException();
        }
    }
}
