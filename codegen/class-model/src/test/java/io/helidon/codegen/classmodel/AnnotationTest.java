/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
Test that annotations are correctly written.
 */
class AnnotationTest {
    private static final TypeName ANNOTATION_TYPE = TypeName.create(Test.class);

    @Test
    void testAnnotationWithConstant() {
        var annotation = Annotation.builder()
                .typeName(ANNOTATION_TYPE)
                .putProperty("value", AnnotationProperty.create("someName",
                                                                TypeName.create(AnnotationTest.class),
                                                                "VALUE"))
                .build();

        Field field = Field.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .type(String.class)
                .name("name")
                .addAnnotation(annotation)
                .build();
        String text = write(field);

        assertThat(text, is("""
                                    @Test(io.helidon.codegen.classmodel.AnnotationTest.VALUE)
                                    private String name;"""));
    }

    @Test
    void testPrintEnumValue() {
        TypeName enumType = TypeName.create(TestEnum.class);

        Field field = Field.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .type(String.class)
                .name("name")
                .addAnnotation(Annotation.builder()
                                       .typeName(ANNOTATION_TYPE)
                                       .putValue("enumValue", EnumValue.create(enumType,
                                                                               "ONE"))
                                       .build())
                .build();
        String text = write(field);

        assertThat(text, is("""
                                    @Test(enumValue = AnnotationTest.TestEnum.ONE)
                                    private String name;"""));
    }

    @Test
    void testMetaAnnotation() {
        Field field = Field.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .type(Annotation.class)
                .name("annotation")
                .addContentCreate(Annotation.builder()
                                          .typeName(ANNOTATION_TYPE)
                                          .putValue("value", "someValue")
                                          .addMetaAnnotation(Annotation.builder()
                                                                     .typeName(ANNOTATION_TYPE)
                                                                     .putValue("value", "string")
                                                                     .build())
                                          .build())
                .build();
        String text = write(field);

        String expected = """
                private Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("org.junit.jupiter.api.Test"))
                .putValue("value", "someValue")
                .addMetaAnnotation(Annotation.builder()
                .typeName(TypeName.create("org.junit.jupiter.api.Test"))
                .putValue("value", "string")
                .build()
                )
                .build();""";

        assertThat(text, is(expected));
    }

    @Test
    void testContentCreateEnumValue() {
        TypeName enumType = TypeName.create(TestEnum.class);

        Field field = Field.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .type(Annotation.class)
                .name("annotation")
                .addContentCreate(Annotation.builder()
                                          .typeName(ANNOTATION_TYPE)
                                          .putValue("enumValue", EnumValue.create(enumType,
                                                                                  "ONE"))
                                          .build())
                .build();
        String text = write(field);

        String expected = """
                private Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("org.junit.jupiter.api.Test"))
                .putValue("enumValue", EnumValue.create(TypeName.create("io.helidon.codegen.classmodel.AnnotationTest.TestEnum"),"ONE"))
                .build();""";

        assertThat(text, is(expected));
    }

    @Test
    void testClassValue() {
        TypeName enumType = TypeName.create(TestEnum.class);

        Field field = Field.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .type(String.class)
                .name("name")
                .addAnnotation(Annotation.builder()
                                       .typeName(ANNOTATION_TYPE)
                                       .putValue("classValue", enumType)
                                       .build())
                .build();
        String text = write(field);

        assertThat(text, is("""
                                    @Test(classValue = AnnotationTest.TestEnum.class)
                                    private String name;"""));
    }

    @Test
    void testContentCreateClassValue() {
        TypeName enumType = TypeName.create(TestEnum.class);

        Field field = Field.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .type(Annotation.class)
                .name("annotation")
                .addContentCreate(Annotation.builder()
                                          .typeName(ANNOTATION_TYPE)
                                          .putValue("classValue", enumType)
                                          .build())
                .build();
        String text = write(field);

        String expected = """
                private Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("org.junit.jupiter.api.Test"))
                .putValue("classValue", TypeName.create("io.helidon.codegen.classmodel.AnnotationTest.TestEnum"))
                .build();""";

        assertThat(text, is(expected));
    }

    String write(ModelComponent component) {
        ImportOrganizer io = ImportOrganizer.builder()
                .typeName(AnnotationTest.class.getName())
                .addImport(Test.class)
                .addImport(String.class)
                .addImport(Annotation.class)
                .addImport(TypeName.class)
                .addImport(EnumValue.class)
                .build();
        StringWriter writer = new StringWriter();
        ModelWriter modelWriter = new ModelWriter(writer, "");
        try {
            component.writeComponent(modelWriter,
                                     Set.of(),
                                     io,
                                     ClassType.CLASS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    private enum TestEnum {
        ONE,
        TWO
    }
}
