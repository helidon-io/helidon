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

package io.helidon.builder.tests.common.types;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class TypeInfoBuilderTest {
    private static final TypeName SOME_TYPE = TypeName.create("io.helidon.SomeType");

    @Test
    void defaultsAccessModifier() {
        TypeInfo build = TypeInfo.builder()
                .typeName(SOME_TYPE)
                .kind(ElementKind.CLASS)
                .build();

        assertThat(build.kind(), is(ElementKind.CLASS));
        assertThat(build.accessModifier(), is(AccessModifier.PACKAGE_PRIVATE));
    }

    @Test
    void supportsElementModifiers() {
        TypeInfo build = TypeInfo.builder()
                .addElementModifier(Modifier.ABSTRACT)
                .addElementModifier(Modifier.STATIC)
                .accessModifier(AccessModifier.PUBLIC)
                .typeName(SOME_TYPE)
                .kind(ElementKind.CLASS)
                .build();

        assertThat(build.accessModifier(), is(AccessModifier.PUBLIC));
        assertThat(build.elementModifiers(), contains(Modifier.ABSTRACT, Modifier.STATIC));
    }
}
