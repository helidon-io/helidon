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

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class TypedElementInfoDefaultTest {

    @Test
    void declarations() {
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(boolean.class))
                           .build().toString(),
                   is("boolean arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(byte.class))
                           .build().toString(),
                   is("byte arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(short.class))
                           .build().toString(),
                   is("short arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(int.class))
                           .build().toString(),
                   is("int arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(long.class))
                           .build().toString(),
                   is("long arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(char.class))
                           .build().toString(),
                   is("char arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(float.class))
                           .build().toString(),
                   is("float arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(double.class))
                           .build().toString(),
                   is("double arg"));
        assertThat(TypedElementInfoDefault.builder()
                           .elementName("arg")
                           .typeName(create(void.class))
                           .build().toString(),
                   is("void arg"));

        assertThat(TypedElementInfoDefault.builder()
                           .enclosingTypeName(createFromTypeName("MyClass"))
                           .elementName("hello")
                           .typeName(create(void.class))
                           .elementKind(TypeInfo.KIND_METHOD)
                           .addParameterArgument(TypedElementInfoDefault.builder()
                                                         .elementName("arg1")
                                                         .typeName(create(String.class))
                                                         .elementKind(TypeInfo.KIND_PARAMETER)
                                                         .build())
                           .addParameterArgument(TypedElementInfoDefault.builder()
                                                         .elementName("arg2")
                                                         .typeName(create(int.class))
                                                         .elementKind(TypeInfo.KIND_PARAMETER)
                                                         .build())
                           .build().toString(),
                   is("MyClass::void hello(java.lang.String arg1, int arg2)"));
    }

    @Test
    void allElementInfo() {
        TypedElementInfo m1 = TypedElementInfoDefault.builder()
                .typeName(getClass())
                .elementKind(TypeInfo.KIND_METHOD)
                .elementName("m1")
                .build();
        TypedElementInfo m2 = TypedElementInfoDefault.builder()
                .typeName(getClass())
                .elementKind(TypeInfo.KIND_METHOD)
                .elementName("m2")
                .build();

        TypeInfo typeInfo = TypeInfoDefault.builder()
                .addInterestingElementInfo(m1)
                .build();
        assertThat(typeInfo.allElementInfo(),
                   sameInstance(typeInfo.interestingElementInfo()));

        typeInfo = TypeInfoDefault.builder()
                .addOtherElementInfo(m1)
                .build();
        assertThat(typeInfo.allElementInfo(),
                   sameInstance(typeInfo.otherElementInfo()));

        typeInfo = TypeInfoDefault.builder()
                .addInterestingElementInfo(m1)
                .otherElementInfo(List.of(m2, m1))
                .build();
        assertThat(typeInfo.allElementInfo(),
                   contains(m1, m2));
    }

}
