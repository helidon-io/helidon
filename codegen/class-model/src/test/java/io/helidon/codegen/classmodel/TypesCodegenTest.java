/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class TypesCodegenTest {
    @Test
    void testIt() {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .property("string", "value1")
                .property("boolean", true)
                .property("long", 49L)
                .property("double", 49.0D)
                .property("integer", 49)
                .property("byte", (byte) 49)
                .property("char", 'x')
                .property("short", (short) 49)
                .property("float", 49.0F)
                .property("class", TypesCodegenTest.class)
                .property("type", TypeName.create(TypesCodegenTest.class))
                .property("enum", ElementType.FIELD)
                .property("lstring", List.of("value1", "value2"))
                .property("lboolean", List.of(true, false))
                .property("llong", List.of(49L, 50L))
                .property("ldouble", List.of(49.0, 50.0))
                .property("linteger", List.of(49, 50))
                .property("lbyte", List.of((byte) 49, (byte) 50))
                .property("lchar", List.of('x', 'y'))
                .property("lshort", List.of((short) 49, (short) 50))
                .property("lfloat", List.of(49.0F, 50.0F))
                .property("lclass", List.of(TypesCodegenTest.class, TypesCodegenTest.class))
                .property("ltype",
                          List.of(TypeName.create(TypesCodegenTest.class), TypeName.create(TypesCodegenTest.class)))
                .property("lenum", List.of(ElementType.FIELD, ElementType.MODULE))
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateAnnotation(contentBuilder, annotation);
        String createString = contentBuilder.generatedString();

        assertThat(createString.replaceAll(" {4}", ""),
                   is("""
                              @io.helidon.common.types.Annotation@.builder()
                              .typeName(@io.helidon.common.types.TypeName@.create("io.helidon.RandomAnnotation"))
                              .property("string", "value1")
                              .property("boolean", true)
                              .property("long", 49L)
                              .property("double", 49.0D)
                              .property("integer", 49)
                              .property("byte", (byte)49)
                              .property("char", 'x')
                              .property("short", (short)49)
                              .property("float", 49.0F)
                              .property("class", @io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"))
                              .property("type", @io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"))
                              .property("enum", @io.helidon.common.types.EnumValue@.create(@io.helidon.common.types.TypeName@.create("java.lang.annotation.ElementType"),"FIELD"))
                              .property("lstring", @java.util.List@.of("value1","value2"))
                              .property("lboolean", @java.util.List@.of(true,false))
                              .property("llong", @java.util.List@.of(49L,50L))
                              .property("ldouble", @java.util.List@.of(49.0D,50.0D))
                              .property("linteger", @java.util.List@.of(49,50))
                              .property("lbyte", @java.util.List@.of((byte)49,(byte)50))
                              .property("lchar", @java.util.List@.of('x','y'))
                              .property("lshort", @java.util.List@.of((short)49,(short)50))
                              .property("lfloat", @java.util.List@.of(49.0F,50.0F))
                              .property("lclass", @java.util.List@.of(@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"),@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest")))
                              .property("ltype", @java.util.List@.of(@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest"),@io.helidon.common.types.TypeName@.create("io.helidon.codegen.classmodel.TypesCodegenTest")))
                              .property("lenum", @java.util.List@.of(@io.helidon.common.types.EnumValue@.create(@io.helidon.common.types.TypeName@.create("java.lang.annotation.ElementType"),"FIELD"),@io.helidon.common.types.EnumValue@.create(@io.helidon.common.types.TypeName@.create("java.lang.annotation.ElementType"),"MODULE")))
                              .build()"""));
    }

    @Test
    void testAnnotatedNestedTypeName() {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .property("value", "quoted \" backslash \\ tab \t")
                .build();
        TypeName typeName = TypeName.builder(TypeNames.MAP)
                .addTypeArgument(TypeNames.STRING)
                .addTypeArgument(TypeName.builder(TypeNames.MAP)
                                         .addTypeArgument(TypeNames.STRING)
                                         .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                                                  .addAnnotation(annotation)
                                                                  .build())
                                         .build())
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateTypeName(contentBuilder, typeName);
        String createString = contentBuilder.generatedString();

        assertThat(createString,
                   not(containsString("@io.helidon.common.types.TypeName@.create(\"java.util.Map<java.lang.String,")));
        assertThat(createString, containsString(".className(\"Map\")"));
        assertThat(createString, containsString(".addTypeArgument(@io.helidon.common.types.TypeName@.builder()"));
        assertThat(createString, containsString(".addAnnotation(@io.helidon.common.types.Annotation@.builder()"));
        assertThat(createString, containsString(".property(\"value\", \"quoted \\\" backslash \\\\ tab \\t\")"));
    }

    @Test
    void testAnnotatedBoundTypeName() {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .build();
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(annotation)
                                                        .build())
                                         .build())
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateTypeName(contentBuilder, typeName);
        String createString = contentBuilder.generatedString();

        assertThat(createString,
                   not(containsString("@io.helidon.common.types.TypeName@.create(\"java.util.List<? extends")));
        assertThat(createString, containsString(".wildcard(true)"));
        assertThat(createString, containsString(".addUpperBound(@io.helidon.common.types.TypeName@.builder()"));
        assertThat(createString, containsString(".addAnnotation(@io.helidon.common.types.Annotation@.create("));
    }

    @Test
    void testAnnotatedLowerBoundTypeName() {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .build();
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addLowerBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(annotation)
                                                        .build())
                                         .build())
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateTypeName(contentBuilder, typeName);
        String createString = contentBuilder.generatedString();

        assertThat(createString,
                   not(containsString("@io.helidon.common.types.TypeName@.create(\"java.util.List<? super")));
        assertThat(createString, containsString(".wildcard(true)"));
        assertThat(createString, containsString(".addLowerBound(@io.helidon.common.types.TypeName@.builder()"));
        assertThat(createString, containsString(".addAnnotation(@io.helidon.common.types.Annotation@.create("));
    }

    @Test
    void testAnnotatedComponentTypeName() {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .build();
        TypeName componentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(annotation)
                .build();
        TypeName typeName = TypeName.builder(TypeNames.STRING)
                .array(true)
                .componentType(componentType)
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateTypeName(contentBuilder, typeName);
        String createString = contentBuilder.generatedString();

        assertThat(createString,
                   not(containsString("@io.helidon.common.types.TypeName@.create(\"java.lang.String[]")));
        assertThat(createString, containsString(".array(true)"));
        assertThat(createString, containsString(".componentType(@io.helidon.common.types.TypeName@.builder()"));
        assertThat(createString, containsString(".addAnnotation(@io.helidon.common.types.Annotation@.create("));
    }

    @Test
    void testTypedElementEnclosingType() {
        TypeName enclosingType = TypeName.create("io.helidon.codegen.classmodel.EnclosingType");
        TypedElementInfo elementInfo = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("method")
                .typeName(TypeNames.STRING)
                .enclosingType(enclosingType)
                .build();

        TestContentBuilder contentBuilder = new TestContentBuilder();
        ContentSupport.addCreateElement(contentBuilder, elementInfo);
        String createString = contentBuilder.generatedString();

        assertThat(createString, containsString(".enclosingType("));
        assertThat(createString,
                   containsString(".enclosingType(@io.helidon.common.types.TypeName@.create(\""
                                          + enclosingType.fqName()
                                          + "\"))"));
    }
}
