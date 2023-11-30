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

package io.helidon.builder.tests.common.types;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * This test can be removed when we remove all deprecated methods.
 */
@SuppressWarnings("removal")
class TypeInfoBackCompatTest {
    private static final TypeName SOME_TYPE = TypeName.create("io.helidon.SomeType");

    @Test
    void testAccessModifiersOld() {
        TypeInfo build = TypeInfo.builder()
                .addModifier(TypeValues.MODIFIER_PUBLIC)
                .typeName(SOME_TYPE)
                .kind(ElementKind.CLASS)
                .build();

        assertThat(build.modifiers(), contains(TypeValues.MODIFIER_PUBLIC));
        assertThat(build.accessModifier(), is(AccessModifier.PUBLIC));
    }

    @Test
    void testAccessModifiersNew() {
        TypeInfo build = TypeInfo.builder()
                .accessModifier(AccessModifier.PUBLIC)
                .typeName(SOME_TYPE)
                .kind(ElementKind.CLASS)
                .build();

        assertThat(build.modifiers(), contains(TypeValues.MODIFIER_PUBLIC));
        assertThat(build.accessModifier(), is(AccessModifier.PUBLIC));
    }

    @Test
    void testModifiersOld() {
        TypeInfo build = TypeInfo.builder()
                .addModifier(TypeValues.MODIFIER_ABSTRACT)
                .addModifier(TypeValues.MODIFIER_STATIC)
                .accessModifier(AccessModifier.PUBLIC)
                .typeName(SOME_TYPE)
                .kind(ElementKind.CLASS)
                .build();

        assertThat(build.accessModifier(), is(AccessModifier.PUBLIC));
        assertThat(build.modifiers(), contains(TypeValues.MODIFIER_ABSTRACT,
                                               TypeValues.MODIFIER_STATIC,
                                               TypeValues.MODIFIER_PUBLIC));
        assertThat(build.elementModifiers(), contains(Modifier.ABSTRACT, Modifier.STATIC));
    }

    @Test
    void testModifiersNew() {
        TypeInfo build = TypeInfo.builder()
                .addElementModifier(Modifier.ABSTRACT)
                .addElementModifier(Modifier.STATIC)
                .accessModifier(AccessModifier.PUBLIC)
                .typeName(SOME_TYPE)
                .kind(ElementKind.CLASS)
                .build();

        assertThat(build.accessModifier(), is(AccessModifier.PUBLIC));
        assertThat(build.modifiers(), contains(TypeValues.MODIFIER_ABSTRACT,
                                               TypeValues.MODIFIER_STATIC,
                                               TypeValues.MODIFIER_PUBLIC));
        assertThat(build.elementModifiers(), contains(Modifier.ABSTRACT, Modifier.STATIC));
    }

    @Test
    void testTypeKindOld() {
        TypeInfo build = TypeInfo.builder()
                .typeKind(TypeValues.KIND_ENUM)
                .typeName(SOME_TYPE)
                .build();

        assertThat(build.typeKind(), is(TypeValues.KIND_ENUM));
        assertThat(build.kind(), is(ElementKind.ENUM));
    }

    @Test
    void testTypeKindNew() {
        TypeInfo build = TypeInfo.builder()
                .kind(ElementKind.ANNOTATION_TYPE)
                .typeName(SOME_TYPE)
                .build();

        assertThat(build.typeKind(), is(TypeValues.KIND_ANNOTATION_TYPE));
        assertThat(build.kind(), is(ElementKind.ANNOTATION_TYPE));
    }
}
