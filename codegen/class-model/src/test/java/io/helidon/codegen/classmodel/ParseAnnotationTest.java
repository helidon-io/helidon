/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/*
Test that annotations are correctly parsed.
 */
class ParseAnnotationTest {
    /*
    we should test all possible options:
    String
    boolean
    integer
    double
    long
    float
    char
    byte
    short
    class
    enum
    annotation
    array of above
     */

    static Stream<SingleValueTestData> singeValueArraySource() {
        return Stream.of(
                new SingleValueTestData("java.lang.Override({\"some string \\\" \", \"other\"})",
                                        "Override({\"some string \\\" \", \"other\"})",
                                        List.of("some string \" ", "other")),
                new SingleValueTestData("java.lang.Override({true, false})",
                                        "Override({true, false})",
                                        List.of(true, false)),
                new SingleValueTestData("java.lang.Override({42, 43})",
                                        "Override({42, 43})",
                                        List.of(42, 43)),
                new SingleValueTestData("java.lang.Override({42.0D, 43.0D})",
                                        "Override({42.0D, 43.0D})",
                                        List.of(42.0D, 43.0D)),
                new SingleValueTestData("java.lang.Override({42L, 43L})",
                                        "Override({42L, 43L})",
                                        List.of(42L, 43L)),
                new SingleValueTestData("java.lang.Override({42.0F, 43.0F})",
                                        "Override({42.0F, 43.0F})",
                                        List.of(42.0F, 43.0F)),
                new SingleValueTestData("java.lang.Override({'\\'', '\\n'})",
                                        "Override({'\\'', '\\n'})",
                                        List.of('\'', '\n')),
                new SingleValueTestData("java.lang.Override({42B, 43B})",
                                        "Override({(byte) 42, (byte) 43})",
                                        List.of((byte) 42, (byte) 43)),
                new SingleValueTestData("java.lang.Override({42S, 43S})",
                                        "Override({(short) 42, (short) 43})",
                                        List.of((short) 42, (short) 43)),
                new SingleValueTestData("java.lang.Override({class::java.lang.String,"
                                                + " class::java.lang.annotation.ElementType})",
                                        "Override({String.class, ElementType.class})",
                                        List.of(TypeName.create(String.class), TypeName.create(ElementType.class))),
                new SingleValueTestData("java.lang.Override({enum::java.lang.annotation.ElementType.TYPE, "
                                                + "enum::java.lang.annotation.ElementType.FIELD})",
                                        "Override({ElementType.TYPE, ElementType.FIELD})",
                                        List.of(EnumValue.create(ElementType.class, ElementType.TYPE),
                                                EnumValue.create(ElementType.class, ElementType.FIELD))),
                new SingleValueTestData("java.lang.Override({@java.lang.Override, @org.junit.jupiter.api.Test})",
                                        "Override({@Override, @Test})",
                                        List.of(io.helidon.common.types.Annotation.create(Override.class),
                                                io.helidon.common.types.Annotation.create(Test.class)))
        );
    }

    static Stream<SingleValueTestData> singeValueSource() {
        return Stream.of(
                new SingleValueTestData("java.lang.Override(\"some string \\\" \")",
                                        "Override(\"some string \\\" \")",
                                        "some string \" "),
                new SingleValueTestData("java.lang.Override(true)",
                                        "Override(true)",
                                        true),
                new SingleValueTestData("java.lang.Override(42)",
                                        "Override(42)",
                                        42),
                new SingleValueTestData("java.lang.Override(42.0D)",
                                        "Override(42.0D)",
                                        42.0D),
                new SingleValueTestData("java.lang.Override(42L)",
                                        "Override(42L)",
                                        42L),
                new SingleValueTestData("java.lang.Override(42.0F)",
                                        "Override(42.0F)",
                                        42.0F),
                new SingleValueTestData("java.lang.Override('\\'')",
                                        "Override('\\'')",
                                        '\''),
                new SingleValueTestData("java.lang.Override(42B)",
                                        "Override((byte) 42)",
                                        (byte) 42),
                new SingleValueTestData("java.lang.Override(42S)",
                                        "Override((short) 42)",
                                        (short) 42),
                new SingleValueTestData("java.lang.Override(class::java.lang.String)",
                                        "Override(String.class)",
                                        TypeName.create(String.class)),
                new SingleValueTestData("java.lang.Override(enum::java.lang.annotation.ElementType.TYPE)",
                                        "Override(ElementType.TYPE)",
                                        EnumValue.create(ElementType.class, ElementType.TYPE)),
                new SingleValueTestData("java.lang.Override(@java.lang.Override)",
                                        "Override(@Override)",
                                        io.helidon.common.types.Annotation.create(Override.class)));
    }

    static Stream<SingleValueTestData> singeNamedValueSource() {
        return Stream.of(
                new SingleValueTestData("java.lang.Override(key = \"some string \\\" \")",
                                        "Override(key = \"some string \\\" \")",
                                        "some string \" "),
                new SingleValueTestData("java.lang.Override(key = true)",
                                        "Override(key = true)",
                                        true),
                new SingleValueTestData("java.lang.Override(key = 42)",
                                        "Override(key = 42)",
                                        42),
                new SingleValueTestData("java.lang.Override(key = 42.0D)",
                                        "Override(key = 42.0D)",
                                        42.0D),
                new SingleValueTestData("java.lang.Override(key = 42L)",
                                        "Override(key = 42L)",
                                        42L),
                new SingleValueTestData("java.lang.Override(key = 42.0F)",
                                        "Override(key = 42.0F)",
                                        42.0F),
                new SingleValueTestData("java.lang.Override(key = '\\'')",
                                        "Override(key = '\\'')",
                                        '\''),
                new SingleValueTestData("java.lang.Override(key = 42B)",
                                        "Override(key = (byte) 42)",
                                        (byte) 42),
                new SingleValueTestData("java.lang.Override(key = 42S)",
                                        "Override(key = (short) 42)",
                                        (short) 42),
                new SingleValueTestData("java.lang.Override(key = class::java.lang.String)",
                                        "Override(key = String.class)",
                                        TypeName.create(String.class)),
                new SingleValueTestData("java.lang.Override(key = enum::java.lang.annotation.ElementType.TYPE)",
                                        "Override(key = ElementType.TYPE)",
                                        EnumValue.create(ElementType.class, ElementType.TYPE)),
                new SingleValueTestData("java.lang.Override(key = @java.lang.Override)",
                                        "Override(key = @Override)",
                                        io.helidon.common.types.Annotation.create(Override.class)));
    }

    @ParameterizedTest
    @MethodSource("singeNamedValueSource")
    void testSingleNamedValueAnnotation(SingleValueTestData testData) {
        var annotation = Annotation.parse(testData.annotationString())
                .toTypesAnnotation();

        assertThat(annotation.typeName(), is(TypeName.create(Override.class)));
        assertThat(annotation.objectValue("key"), not(Optional.empty()));
        var actualValue = annotation.objectValue("key").get();
        var expectedValue = testData.expectedValue();
        assertThat(actualValue, instanceOf(expectedValue.getClass()));
        assertThat(actualValue, is(expectedValue));

        Method method = Method.builder()
                .addAnnotation(annotation)
                .name("name")
                .build();

        String text = write(method);
        String expected = "@" + testData.expectedAnnotation()
                + "\npublic void name() {\n}";

        assertThat(testData.toString(),
                   text,
                   is(expected));
    }

    @ParameterizedTest
    @MethodSource("singeValueArraySource")
    void testSingleArrayValueAnnotation(SingleValueTestData singleValueTestData) {
        var annotation = Annotation.parse(singleValueTestData.annotationString())
                .toTypesAnnotation();

        assertThat(annotation.typeName(), is(TypeName.create(Override.class)));
        assertThat(annotation.objectValue(), is(Optional.of(singleValueTestData.expectedValue())));

        Method method = Method.builder()
                .addAnnotation(annotation)
                .name("name")
                .build();

        String text = write(method);
        String expected = "@" + singleValueTestData.expectedAnnotation()
                + "\npublic void name() {\n}";

        assertThat(singleValueTestData.toString(),
                   text,
                   is(expected));
    }

    @ParameterizedTest
    @MethodSource("singeValueSource")
    void testSingleValueAnnotation(SingleValueTestData singleValueTestData) {
        var annotation = Annotation.parse(singleValueTestData.annotationString())
                .toTypesAnnotation();

        assertThat(annotation.typeName(), is(TypeName.create(Override.class)));
        assertThat(annotation.objectValue(), is(Optional.of(singleValueTestData.expectedValue())));

        Method method = Method.builder()
                .addAnnotation(annotation)
                .name("name")
                .build();

        String text = write(method);
        String expected = "@" + singleValueTestData.expectedAnnotation()
                + "\npublic void name() {\n}";

        assertThat(singleValueTestData.toString(),
                   text,
                   is(expected));
    }

    @Test
    void testSimpleAnnotation() {
        var annotation = Annotation.parse(Override.class.getName())
                .toTypesAnnotation();

        assertThat(annotation.typeName(), is(TypeName.create(Override.class)));
        assertThat(annotation.value(), is(Optional.empty()));

        Method method = Method.builder()
                .addAnnotation(annotation)
                .name("name")
                .build();

        String text = write(method);

        assertThat(text,
                   is("""
                              @Override
                              public void name() {
                              }"""));
    }

    @Test
    void testComplexAnnotation() {
        String annotationString = """
                      io.helidon.codegen.classmodel.ParseAnnotationTest.MyAnnotation(stringValue = "value1",
                      booleanValue = true,
                      longValue = 49L,
                      doubleValue = 49.0D,
                      intValue = 49,
                      byteValue = 49B,
                      charValue = 'x',
                      shortValue = 49S,
                      floatValue = 49.0F,
                      classValue = class::java.lang.String,
                      typeValue = class::java.lang.String,
                      enumValue = enum::java.lang.annotation.ElementType.FIELD,
                      annotationValue = @java.lang.annotation.Target(enum::java.lang.annotation.ElementType.CONSTRUCTOR),
                      lstring = {"value\\"1", "value2"},
                      lboolean = {true, false},
                      llong = {49L, 50L},
                      ldouble = {49.0D, 50.0D},
                      lint = {49, 50},
                      lbyte = {49b, 50b},
                      lchar = {'x', '\\''},
                      lshort = {49S, 50S},
                      lfloat = {49.0F, 50.0F},
                      lclass = {class::java.lang.String, class::java.lang.Integer},
                      ltype = {class::java.lang.String, class::java.lang.Integer},
                      lenum = {enum::java.lang.annotation.ElementType.FIELD, enum::java.lang.annotation.ElementType.MODULE},
                      lannotation = {@java.lang.annotation.Target(enum::java.lang.annotation.ElementType.CONSTRUCTOR),
                                        @java.lang.annotation.Target(enum::java.lang.annotation.ElementType.FIELD)},
                      emptyList = {},
                      singletonList = "value")
                """;
        var annotation = Annotation.parse(annotationString)
                .toTypesAnnotation();

        assertThat(annotation.typeName(), is(TypeName.create(MyAnnotation.class)));

        Method method = Method.builder()
                .addAnnotation(annotation)
                .name("name")
                .build();

        String text = write(method);
        String expected = "@ParseAnnotationTest.MyAnnotation(stringValue = \"value1\", "
                + "booleanValue = true, "
                + "longValue = 49L, "
                + "doubleValue = 49.0D, "
                + "intValue = 49, "
                + "byteValue = (byte) 49, "
                + "charValue = 'x', "
                + "shortValue = (short) 49, "
                + "floatValue = 49.0F, "
                + "classValue = String.class, "
                + "typeValue = String.class, "
                + "enumValue = ElementType.FIELD, "
                + "annotationValue = @Target(ElementType.CONSTRUCTOR), "
                + "lstring = {\"value\\\"1\", \"value2\"}, "
                + "lboolean = {true, false}, "
                + "llong = {49L, 50L}, "
                + "ldouble = {49.0D, 50.0D}, "
                + "lint = {49, 50}, "
                + "lbyte = {(byte) 49, (byte) 50}, "
                + "lchar = {'x', '\\''}, "
                + "lshort = {(short) 49, (short) 50}, "
                + "lfloat = {49.0F, 50.0F}, "
                + "lclass = {String.class, Integer.class}, "
                + "ltype = {String.class, Integer.class}, "
                + "lenum = {ElementType.FIELD, ElementType.MODULE}, "
                + "lannotation = {@Target(ElementType.CONSTRUCTOR), @Target(ElementType.FIELD)}, "
                + "emptyList = {}, "
                + "singletonList = \"value\""
                + ")\npublic void name() {\n"
                + "}";

        assertThat(text, is(expected));
    }

    String write(ModelComponent component) {
        ImportOrganizer io = ImportOrganizer.builder()
                .typeName(ParseAnnotationTest.class.getName())
                .addImport(Override.class)
                .addImport(Test.class)
                .addImport(MyAnnotation.class)
                .addImport(DisplayName.class)
                .addImport(Target.class)
                .addImport(Integer.class)
                .addImport(ElementType.class)
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

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation {
        String stringValue();

        boolean booleanValue();

        long longValue();

        double doubleValue();

        int intValue();

        byte byteValue();

        char charValue();

        short shortValue();

        float floatValue();

        Class<?> classValue();

        Class<?> typeValue();

        ElementType enumValue();

        Target annotationValue();

        String[] lstring();

        boolean[] lboolean();

        long[] llong();

        double[] ldouble();

        int[] lint();

        byte[] lbyte();

        char[] lchar();

        short[] lshort();

        float[] lfloat();

        Class<?>[] lclass();

        Class<?>[] ltype();

        ElementType[] lenum();

        Target[] lannotation();

        String[] emptyList();

        String[] singletonList();
    }

    record SingleValueTestData(String annotationString, String expectedAnnotation, Object expectedValue) {
    }
}
