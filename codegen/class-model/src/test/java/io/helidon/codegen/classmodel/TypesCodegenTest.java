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

package io.helidon.codegen.classmodel;

import java.lang.annotation.ElementType;
import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TypesCodegenTest {
    @Test
    void testIt() {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .putValue("string", "value1")
                .putValue("boolean", true)
                .putValue("long", 49L)
                .putValue("double", 49.0D)
                .putValue("integer", 49)
                .putValue("byte", (byte) 49)
                .putValue("char", 'x')
                .putValue("short", (short) 49)
                .putValue("float", 49.0F)
                .putValue("class", TypesCodegenTest.class)
                .putValue("type", TypeName.create(TypesCodegenTest.class))
                .putValue("enum", ElementType.FIELD)
                .putValue("lstring", List.of("value1", "value2"))
                .putValue("lboolean", List.of(true, false))
                .putValue("llong", List.of(49L, 50L))
                .putValue("ldouble", List.of(49.0, 50.0))
                .putValue("linteger", List.of(49, 50))
                .putValue("lbyte", List.of((byte) 49, (byte) 50))
                .putValue("lchar", List.of('x', 'y'))
                .putValue("lshort", List.of((short) 49, (short) 50))
                .putValue("lfloat", List.of(49.0F, 50.0F))
                .putValue("lclass", List.of(TypesCodegenTest.class, TypesCodegenTest.class))
                .putValue("ltype",
                          List.of(TypeName.create(TypesCodegenTest.class), TypeName.create(TypesCodegenTest.class)))
                .putValue("lenum", List.of(ElementType.FIELD, ElementType.MODULE))
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateAnnotation(contentBuilder, annotation);
        String createString = contentBuilder.generatedString();

        assertThat(createString.replaceAll(" {4}", ""),
                   is("""
                              @io.helidon.common.types.Annotation@.builder()
                              .typeName(@io.helidon.common.types.TypeName@.create("io.helidon.RandomAnnotation"))
                              .putValue("string", "value1")
                              .putValue("boolean", true)
                              .putValue("long", 49L)
                              .putValue("double", 49.0D)
                              .putValue("integer", 49)
                              .putValue("byte", (byte)49)
                              .putValue("char", 'x')
                              .putValue("short", (short)49)
                              .putValue("float", 49.0F)
                              .putValue("class", @io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"))
                              .putValue("type", @io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"))
                              .putValue("enum", @io.helidon.common.types.EnumValue@.create(@io.helidon.common.types.TypeName@.create("java.lang.annotation.ElementType"),"FIELD"))
                              .putValue("lstring", @java.util.List@.of("value1","value2"))
                              .putValue("lboolean", @java.util.List@.of(true,false))
                              .putValue("llong", @java.util.List@.of(49L,50L))
                              .putValue("ldouble", @java.util.List@.of(49.0D,50.0D))
                              .putValue("linteger", @java.util.List@.of(49,50))
                              .putValue("lbyte", @java.util.List@.of((byte)49,(byte)50))
                              .putValue("lchar", @java.util.List@.of('x','y'))
                              .putValue("lshort", @java.util.List@.of((short)49,(short)50))
                              .putValue("lfloat", @java.util.List@.of(49.0F,50.0F))
                              .putValue("lclass", @java.util.List@.of(@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"),@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest")))
                              .putValue("ltype", @java.util.List@.of(@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"),@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest")))
                              .putValue("lenum", @java.util.List@.of(@io.helidon.common.types.EnumValue@.create(@io.helidon.common.types.TypeName@.create("java.lang.annotation.ElementType"),"FIELD"),@io.helidon.common.types.EnumValue@.create(@io.helidon.common.types.TypeName@.create("java.lang.annotation.ElementType"),"MODULE")))
                              .build()"""));
    }
}
