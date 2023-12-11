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

package io.helidon.common.types;

import org.junit.jupiter.api.Test;

import static io.helidon.common.types.TypeName.create;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TypedElementInfoTest {
    @Test
    void declarations() {
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(boolean.class))
                           .build()
                           .toString(),
                   is("boolean arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(byte.class))
                           .build().toString(),
                   is("byte arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(short.class))
                           .build().toString(),
                   is("short arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(int.class))
                           .build().toString(),
                   is("int arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(long.class))
                           .build().toString(),
                   is("long arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(char.class))
                           .build().toString(),
                   is("char arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(float.class))
                           .build().toString(),
                   is("float arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(double.class))
                           .build().toString(),
                   is("double arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .kind(ElementKind.PARAMETER)
                           .typeName(create(void.class))
                           .build().toString(),
                   is("void arg"));

        assertThat(TypedElementInfo.builder()
                           .enclosingType(create("MyClass"))
                           .elementName("hello")
                           .typeName(create(void.class))
                           .kind(ElementKind.METHOD)
                           .addParameterArgument(TypedElementInfo.builder()
                                                         .elementName("arg1")
                                                         .typeName(create(String.class))
                                                         .kind(ElementKind.PARAMETER)
                                                         .build())
                           .addParameterArgument(TypedElementInfo.builder()
                                                         .elementName("arg2")
                                                         .typeName(create(int.class))
                                                         .kind(ElementKind.PARAMETER)
                                                         .build())
                           .build().toString(),
                   is("MyClass::void hello(java.lang.String arg1, int arg2)"));
    }

    @Test
    void declarationsToBeRemoved() {
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(boolean.class))
                           .build()
                           .toString(),
                   is("boolean arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(byte.class))
                           .build().toString(),
                   is("byte arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(short.class))
                           .build().toString(),
                   is("short arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(int.class))
                           .build().toString(),
                   is("int arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(long.class))
                           .build().toString(),
                   is("long arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(char.class))
                           .build().toString(),
                   is("char arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(float.class))
                           .build().toString(),
                   is("float arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(double.class))
                           .build().toString(),
                   is("double arg"));
        assertThat(TypedElementInfo.builder()
                           .elementName("arg")
                           .elementTypeKind(TypeValues.KIND_PARAMETER)
                           .typeName(create(void.class))
                           .build().toString(),
                   is("void arg"));

        assertThat(TypedElementInfo.builder()
                           .enclosingType(create("MyClass"))
                           .elementName("hello")
                           .typeName(create(void.class))
                           .elementTypeKind(TypeValues.KIND_METHOD)
                           .addParameterArgument(TypedElementInfo.builder()
                                                         .elementName("arg1")
                                                         .typeName(create(String.class))
                                                         .elementTypeKind(TypeValues.KIND_PARAMETER)
                                                         .build())
                           .addParameterArgument(TypedElementInfo.builder()
                                                         .elementName("arg2")
                                                         .typeName(create(int.class))
                                                         .elementTypeKind(TypeValues.KIND_PARAMETER)
                                                         .build())
                           .build().toString(),
                   is("MyClass::void hello(java.lang.String arg1, int arg2)"));
    }

}
