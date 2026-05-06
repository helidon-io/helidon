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
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
        TypeName directAnnotatedType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(notBlank)
                .build();
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
        TypeName arrayType = TypeName.builder(TypeNames.STRING)
                .array(true)
                .componentType(componentType)
                .build();

        assertThat(TypeHierarchy.typeNameAnnotations(directAnnotatedType), hasItem(notBlank));
        assertThat(TypeHierarchy.typeNameAnnotations(upperBoundType), hasItem(notBlank));
        assertThat(TypeHierarchy.typeNameAnnotations(lowerBoundType), hasItem(notBlank));
        assertThat(TypeHierarchy.typeNameAnnotations(arrayType), hasItem(notBlank));
    }

    @Test
    void mergeHierarchyAnnotationsIncludesContractTypeUseAnnotations() {
        Annotation notBlank = Annotation.create(NOT_BLANK);
        TypeName annotatedList = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                         .addAnnotation(notBlank)
                                         .build())
                .build();
        TypeName plainList = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypedElementInfo contractMethod = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PUBLIC)
                .elementName("validate")
                .typeName(TypeNames.PRIMITIVE_VOID)
                .addParameterArgument(TypedElementInfo.builder()
                                              .kind(ElementKind.PARAMETER)
                                              .elementName("value")
                                              .typeName(annotatedList)
                                              .build())
                .build();
        TypeInfo contract = TypeInfo.builder()
                .typeName(TypeName.create("io.helidon.codegen.test.Contract"))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(contractMethod)
                .build();
        TypedElementInfo implementationMethod = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PUBLIC)
                .elementName("validate")
                .typeName(TypeNames.PRIMITIVE_VOID)
                .addParameterArgument(TypedElementInfo.builder()
                                              .kind(ElementKind.PARAMETER)
                                              .elementName("value")
                                              .typeName(plainList)
                                              .build())
                .build();
        TypeInfo implementation = TypeInfo.builder()
                .typeName(TypeName.create("io.helidon.codegen.test.Implementation"))
                .kind(ElementKind.CLASS)
                .addInterfaceTypeInfo(contract)
                .addElementInfo(implementationMethod)
                .build();

        TypedElementInfo merged = TypeHierarchy.mergeHierarchyAnnotations(implementation, implementationMethod);

        TypeName mergedType = merged.parameterArguments().getFirst().typeName();
        assertThat(mergedType.typeArguments().getFirst().annotations(), hasItem(notBlank));
        assertThat(TypeHierarchy.nestedAnnotations(CTX, implementation), hasItem(NOT_BLANK));
    }

    @Test
    void nestedAnnotationsDoNotMergeHierarchyAnnotationsIntoGeneratedTypes() {
        Annotation notBlank = Annotation.create(NOT_BLANK);
        TypeName annotatedList = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                         .addAnnotation(notBlank)
                                         .build())
                .build();
        TypeName plainList = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypedElementInfo serviceMethod = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .elementName("validate")
                .typeName(TypeNames.PRIMITIVE_VOID)
                .addParameterArgument(TypedElementInfo.builder()
                                              .kind(ElementKind.PARAMETER)
                                              .elementName("value")
                                              .typeName(annotatedList)
                                              .build())
                .build();
        TypeInfo service = TypeInfo.builder()
                .typeName(TypeName.create("io.helidon.codegen.test.ValidatedService"))
                .kind(ElementKind.CLASS)
                .addElementInfo(serviceMethod)
                .build();
        TypedElementInfo generatedMethod = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .elementName("validate")
                .typeName(TypeNames.PRIMITIVE_VOID)
                .addParameterArgument(TypedElementInfo.builder()
                                              .kind(ElementKind.PARAMETER)
                                              .elementName("value")
                                              .typeName(plainList)
                                              .build())
                .build();
        TypeInfo generatedSubtype = TypeInfo.builder()
                .typeName(TypeName.create("io.helidon.codegen.test.ValidatedService__Intercepted"))
                .kind(ElementKind.CLASS)
                .superTypeInfo(service)
                .addAnnotation(Annotation.create(TypeNames.GENERATED))
                .addElementInfo(generatedMethod)
                .build();

        Set<TypeName> annotations = TypeHierarchy.nestedAnnotations(CTX, generatedSubtype);

        assertThat(annotations, hasItem(TypeNames.GENERATED));
        assertThat(annotations, not(hasItem(NOT_BLANK)));
    }

    @Test
    void mergeTypeNameAnnotationsPreservesTargetStructure() {
        Annotation notBlank = Annotation.create(NOT_BLANK);
        Annotation targetAnnotation = Annotation.create(TypeName.create("io.helidon.TargetAnnotation"));
        TypeName targetComponentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(targetAnnotation)
                .build();
        TypeName sourceComponentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(notBlank)
                .build();
        TypeName targetType = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(targetAnnotation)
                                                        .build())
                                         .build())
                .array(true)
                .componentType(targetComponentType)
                .build();
        TypeName sourceType = TypeName.builder(TypeNames.LIST)
                .addAnnotation(notBlank)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(notBlank)
                                                        .build())
                                         .build())
                .array(true)
                .componentType(sourceComponentType)
                .build();

        TypeName merged = TypeHierarchy.mergeTypeNameAnnotations(targetType, sourceType);

        assertThat(merged.annotations(), hasItem(notBlank));
        assertThat(merged.typeArguments().getFirst().upperBounds().getFirst().annotations(), hasItem(notBlank));
        assertThat(merged.componentType().orElseThrow().annotations(), hasItem(notBlank));
        assertThat(merged.componentType().orElseThrow().annotations(), hasItem(targetAnnotation));

        TypeName lowerBoundTargetType = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addLowerBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(targetAnnotation)
                                                        .build())
                                         .build())
                .build();
        TypeName lowerBoundSourceType = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addLowerBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(notBlank)
                                                        .build())
                                         .build())
                .build();

        TypeName lowerBoundMerged = TypeHierarchy.mergeTypeNameAnnotations(lowerBoundTargetType, lowerBoundSourceType);

        assertThat(lowerBoundMerged.typeArguments().getFirst().lowerBounds().getFirst().annotations(), hasItem(notBlank));

        TypeName varargTargetType = TypeName.builder(TypeNames.STRING)
                .array(true)
                .vararg(true)
                .componentType(TypeNames.STRING)
                .build();
        TypeName arraySourceType = TypeName.builder(TypeNames.STRING)
                .array(true)
                .componentType(TypeName.builder(TypeNames.STRING)
                                       .addAnnotation(notBlank)
                                       .build())
                .build();

        TypeName varargMerged = TypeHierarchy.mergeTypeNameAnnotations(varargTargetType, arraySourceType);

        assertThat(varargMerged.vararg(), is(true));
        assertThat(varargMerged.componentType().orElseThrow().annotations(), hasItem(notBlank));

        TypeName rawList = TypeNames.LIST;
        TypeName mergedRawList = TypeHierarchy.mergeTypeNameAnnotations(rawList, sourceType);
        assertThat(mergedRawList.typeArguments().isEmpty(), is(true));
        assertThat(mergedRawList.componentType().isEmpty(), is(true));
        assertThat(mergedRawList.annotations(), not(hasItem(notBlank)));

        TypeName mismatchedTargetType = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.create("io.helidon.codegen.Foo"))
                                         .build())
                .build();
        TypeName mismatchedSourceType = TypeName.builder(TypeNames.LIST)
                .addAnnotation(notBlank)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addAnnotation(notBlank)
                                         .addUpperBound(TypeName.create("io.helidon.codegen.Bar"))
                                         .build())
                .build();

        TypeName mismatchedMerged = TypeHierarchy.mergeTypeNameAnnotations(mismatchedTargetType, mismatchedSourceType);

        assertThat(mismatchedMerged.annotations(), not(hasItem(notBlank)));
        assertThat(mismatchedMerged.typeArguments().getFirst().annotations(), not(hasItem(notBlank)));
    }

    @Test
    void mergeTypeNameAnnotationsKeepsTargetAnnotationValueForSameAnnotationType() {
        TypeName annotationType = TypeName.create("io.helidon.codegen.Annotation");
        Annotation targetAnnotation = Annotation.create(annotationType, "target");
        Annotation sourceAnnotation = Annotation.create(annotationType, "source");
        TypeName targetType = TypeName.builder(TypeNames.LIST)
                .addAnnotation(targetAnnotation)
                .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                         .addAnnotation(targetAnnotation)
                                         .build())
                .build();
        TypeName sourceType = TypeName.builder(TypeNames.LIST)
                .addAnnotation(sourceAnnotation)
                .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                         .addAnnotation(sourceAnnotation)
                                         .build())
                .build();

        TypeName merged = TypeHierarchy.mergeTypeNameAnnotations(targetType, sourceType);

        assertThat(merged.annotations(), hasItem(targetAnnotation));
        assertThat(merged.annotations(), not(hasItem(sourceAnnotation)));
        assertThat(merged.typeArguments().getFirst().annotations(), hasItem(targetAnnotation));
        assertThat(merged.typeArguments().getFirst().annotations(), not(hasItem(sourceAnnotation)));
    }

    @Test
    void mergeTypeNameAnnotationsIgnoresFormalTypeParameterNames() {
        Annotation notBlank = Annotation.create(NOT_BLANK);
        TypeName targetType = TypeName.builder(TypeNames.LIST)
                .addTypeParameter("T")
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeName sourceType = TypeName.builder(TypeNames.LIST)
                .addTypeParameter("E")
                .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                         .addAnnotation(notBlank)
                                         .build())
                .build();

        TypeName merged = TypeHierarchy.mergeTypeNameAnnotations(targetType, sourceType);

        assertThat(merged.typeParameters(), is(List.of("T")));
        assertThat(merged.typeArguments().getFirst().annotations(), hasItem(notBlank));
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
