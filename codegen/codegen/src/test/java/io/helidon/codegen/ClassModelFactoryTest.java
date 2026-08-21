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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class ClassModelFactoryTest {

    @Test
    void exposesInProgressRecordFieldsAsRecordComponents() {
        TypeName recordType = TypeName.create("example.GeneratedRecord");
        ClassModel record = ClassModel.builder()
                .type(recordType)
                .classType(ElementKind.RECORD)
                .addField(field -> field.name("value").type(TypeNames.STRING))
                .build();

        RoundContext roundContext = new EmptyRoundContext();
        TypeInfo recordInfo = ClassModelFactory.create(roundContext, recordType, record);

        assertThat(element(recordInfo, "value").kind(), is(ElementKind.RECORD_COMPONENT));
    }

    /**
     * Verifies that a concrete lookup of an in-progress generic record retains its formal declaration metadata and
     * generic component shapes before javac has created an element for the record.
     */
    @Test
    void preservesInProgressGenericRecordDeclaration() {
        TypeName recordType = TypeName.create("example.GeneratedRecord");
        TypeArgument typeParameter = TypeArgument.builder()
                .token("T")
                .bound(CharSequence.class)
                .build();
        TypeName concreteRecordType = TypeName.builder(recordType)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeName optionalType = TypeName.builder(TypeNames.OPTIONAL)
                .addTypeArgument(typeParameter)
                .build();
        ClassModel.Builder record = ClassModel.builder()
                .type(recordType)
                .classType(ElementKind.RECORD)
                .addGenericArgument(typeParameter)
                .addField(field -> field.name("value").type(typeParameter))
                .addField(field -> field.name("optionalValue").type(optionalType));
        RoundContextImpl roundContext = newRoundContext();
        roundContext.addGeneratedType(recordType, record, TypeNames.OBJECT);

        TypeInfo recordInfo = roundContext.typeInfo(concreteRecordType).orElseThrow();

        assertThat(recordInfo.typeName().typeArguments(), is(List.of(TypeNames.STRING)));
        assertThat(recordInfo.rawType().typeArguments(), is(List.of()));
        assertThat(recordInfo.declaredType().typeArguments().size(), is(1));
        TypeName declaredParameter = recordInfo.declaredType().typeArguments().getFirst();
        assertThat(declaredParameter.className(), is("T"));
        assertThat(declaredParameter.upperBounds(), is(List.of(TypeName.create(CharSequence.class))));
        assertThat(element(recordInfo, "value").typeName().className(), is("T"));
        assertThat(element(recordInfo, "optionalValue").typeName().typeArguments().getFirst().className(), is("T"));
    }

    /**
     * Verifies that a round reuses its hierarchy resolver without freezing the lookup view before later generated types
     * are registered.
     */
    @Test
    void reusesResolverWithoutFreezingSameRoundTypes() {
        TypeName contractType = TypeName.create("example.GeneratedContract");
        TypeName implementationType = TypeName.create("example.GeneratedImplementation");
        RoundContextImpl roundContext = newRoundContext();
        TypeHierarchyResolver resolver = roundContext.typeHierarchyResolver();

        roundContext.addGeneratedType(contractType,
                                      ClassModel.builder()
                                              .type(contractType)
                                              .classType(ElementKind.INTERFACE),
                                      TypeNames.OBJECT);
        roundContext.addGeneratedType(implementationType,
                                      ClassModel.builder()
                                              .type(implementationType)
                                              .addInterface(contractType),
                                      TypeNames.OBJECT);

        assertThat(roundContext.typeHierarchyResolver(), sameInstance(resolver));
        assertThat(resolver.resolveSupertype(implementationType, contractType).orElseThrow(), is(contractType));
    }

    @Test
    void exposesInProgressClassFieldsAsFields() {
        TypeName classType = TypeName.create("example.GeneratedClass");
        ClassModel classModel = ClassModel.builder()
                .type(classType)
                .classType(ElementKind.CLASS)
                .addField(field -> field.name("value").type(TypeNames.STRING))
                .build();

        RoundContext roundContext = new EmptyRoundContext();
        TypeInfo classInfo = ClassModelFactory.create(roundContext, classType, classModel);

        assertThat(element(classInfo, "value").kind(), is(ElementKind.FIELD));
    }

    @Test
    void resolvesInProgressRecordFromSimpleName() {
        TypeName recordType = TypeName.create("example.GeneratedRecord");
        RoundContextImpl roundContext = newRoundContext();
        roundContext.addGeneratedType(recordType,
                                      record(recordType),
                                      TypeNames.OBJECT);

        TypeInfo recordInfo = roundContext.typeInfo(TypeName.create("GeneratedRecord"))
                .orElseThrow();

        assertThat(recordInfo.typeName(), is(recordType));
        assertThat(recordInfo.kind(), is(ElementKind.RECORD));
        assertThat(element(recordInfo, "value").kind(), is(ElementKind.RECORD_COMPONENT));
    }

    @Test
    void preservesQualifiedInProgressRecordName() {
        TypeName recordType = TypeName.create("example.GeneratedRecord");
        RoundContextImpl roundContext = newRoundContext();
        roundContext.addGeneratedType(recordType,
                                      record(recordType),
                                      TypeNames.OBJECT);

        TypeInfo recordInfo = roundContext.typeInfo(recordType)
                .orElseThrow();

        assertThat(recordInfo.typeName(), is(recordType));
        assertThat(element(recordInfo, "value").kind(), is(ElementKind.RECORD_COMPONENT));
    }

    private static ClassModel.Builder record(TypeName recordType) {
        return ClassModel.builder()
                .type(recordType)
                .classType(ElementKind.RECORD)
                .addField(field -> field.name("value").type(TypeNames.STRING));
    }

    private static RoundContextImpl newRoundContext() {
        return new RoundContextImpl(new EmptyCodegenContext(),
                                    List.of(),
                                    Set.of(),
                                    Map.of(),
                                    Map.of(),
                                    List.of(),
                                    List.of());
    }

    private static TypedElementInfo element(TypeInfo typeInfo, String name) {
        return typeInfo.elementInfo()
                .stream()
                .filter(element -> name.equals(element.elementName()))
                .findFirst()
                .orElseThrow();
    }

    private record EmptyRoundContext() implements RoundContext {
        @Override
        public Collection<TypeName> availableAnnotations() {
            return List.of();
        }

        @Override
        public Collection<TypeInfo> types() {
            return List.of();
        }

        @Override
        public Collection<TypeInfo> annotatedTypes(TypeName annotationType) {
            return List.of();
        }

        @Override
        public Collection<TypedElementInfo> annotatedElements(TypeName annotationType) {
            return List.of();
        }

        @Override
        public void addGeneratedType(TypeName type,
                                     ClassModel.Builder newClass,
                                     TypeName mainTrigger,
                                     Object... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ClassModel.Builder> generatedType(TypeName type) {
            return Optional.empty();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName) {
            return Optional.empty();
        }
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
        public String uniqueName(TypeInfo type, TypedElementInfo element) {
            throw new UnsupportedOperationException();
        }
    }
}
