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
package io.helidon.data.jdbc.codegen;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcTypeHierarchyTest {

    @Test
    void implementsJavaArrayReturnCovariance() {
        TypeName bytes = TypeName.create(byte[].class);

        assertThat(JdbcTypeHierarchy.returnTypeSubtype(bytes, TypeNames.OBJECT, null), is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(bytes, TypeName.create(Cloneable.class), null), is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(bytes, TypeName.create(Serializable.class), null), is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(TypeName.create(String[].class),
                                                       TypeName.create(Object[].class),
                                                       null),
                   is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(TypeName.create(String[][].class),
                                                       TypeName.create(Object[].class),
                                                       null),
                   is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(bytes, TypeName.create(int[].class), null), is(false));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(TypeName.create(int[].class), bytes, null), is(false));
    }

    @Test
    void preservesTypeUseAnnotationsDuringSubstitution() {
        Annotation annotation = Annotation.create(TypeName.create("example.ResultType"));
        TypeName variable = TypeName.builder()
                .className("T")
                .generic(true)
                .addAnnotation(annotation)
                .build();

        TypeName resolved = JdbcTypeHierarchy.substitute(variable, Map.of("T", TypeNames.STRING));

        assertThat(resolved, is(TypeNames.STRING));
        assertThat(resolved.annotations(), hasItem(annotation));
    }

    @Test
    void preservesEncodedTypeUseAnnotationsDuringSubstitution() {
        TypeName variable = TypeName.createFromGenericDeclaration("@example.ResultType(\"mapped\") T");

        TypeName resolved = JdbcTypeHierarchy.substitute(variable, Map.of("T", TypeNames.STRING));

        Annotation annotation = resolved.findAnnotation(TypeName.create("example.ResultType")).orElseThrow();
        assertThat(annotation.stringValue().orElseThrow(), is("mapped"));
    }

    @Test
    void doesNotSubstituteAConcreteDefaultPackageType() {
        TypeName concreteType = TypeName.builder()
                .className("T")
                .build();

        TypeName resolved = JdbcTypeHierarchy.substitute(concreteType, Map.of("T", TypeNames.STRING));

        assertThat(resolved, is(concreteType));
    }

    @Test
    void recognizesImplicitSupertypesOfRoundVisibleTypes() {
        TypeName recordType = TypeName.create("example.RoundRecord");
        TypeName enumType = TypeName.create("example.RoundEnum");
        TypeName annotationType = TypeName.create("example.RoundAnnotation");
        RoundContext context = new TypesRoundContext(Map.of(recordType, typeInfo(recordType, ElementKind.RECORD),
                                                             enumType, typeInfo(enumType, ElementKind.ENUM),
                                                             annotationType,
                                                             typeInfo(annotationType, ElementKind.ANNOTATION_TYPE)));

        assertThat(JdbcTypeHierarchy.returnTypeSubtype(recordType, TypeName.create(Record.class), context), is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(enumType, TypeName.create(Enum.class), context), is(true));
        assertThat(JdbcTypeHierarchy.returnTypeSubtype(annotationType,
                                                       TypeName.create(java.lang.annotation.Annotation.class),
                                                       context),
                   is(true));
    }

    private static TypeInfo typeInfo(TypeName typeName, ElementKind kind) {
        return TypeInfo.builder()
                .typeName(typeName)
                .kind(kind)
                .build();
    }

    private record TypesRoundContext(Map<TypeName, TypeInfo> typesByName) implements RoundContext {
        @Override
        public Collection<TypeName> availableAnnotations() {
            return List.of();
        }

        @Override
        public Collection<TypeInfo> types() {
            return typesByName.values();
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
            return Optional.ofNullable(typesByName.get(typeName));
        }
    }
}
