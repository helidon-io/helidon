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

package io.helidon.metadata.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AnnotationTest {
    private static Annotation helidonAnnotation;
    private static MyAnnotation syntheticAnnotation;

    @BeforeAll
    public static void setup() {
        helidonAnnotation = null;
    }

    @Order(0)
    @Test
    public void testCreateAnnotation() {
        List<Annotation> annotations = AnnotationFactory.create(AnnotatedType.class);
        assertThat(annotations, hasSize(1));
        Annotation annotation = annotations.getFirst();
        helidonAnnotation = annotation;

        assertThat(annotation.typeName(), is(TypeName.create(MyAnnotation.class)));
        assertThat(annotation.stringValue("stringValue"), optionalValue(is("value1")));
        assertThat(annotation.booleanValue("booleanValue"), optionalValue(is(true)));
        assertThat(annotation.longValue("longValue"), optionalValue(is(49L)));
        assertThat(annotation.doubleValue("doubleValue"), optionalValue(is(49D)));
        assertThat(annotation.intValue("intValue"), optionalValue(is(49)));
        assertThat(annotation.byteValue("byteValue"), optionalValue(is((byte) 49)));
        assertThat(annotation.charValue("charValue"), optionalValue(is('x')));
        assertThat(annotation.shortValue("shortValue"), optionalValue(is((short) 49)));
        assertThat(annotation.floatValue("floatValue"), optionalValue(is(49F)));
        assertThat(annotation.typeValue("classValue"), optionalValue(is(TypeNames.STRING)));
        assertThat(annotation.typeValue("typeValue"), optionalValue(is(TypeNames.STRING)));
        assertThat(annotation.enumValue("enumValue", ElementType.class),
                   optionalValue(is(ElementType.FIELD)));
        assertThat(annotation.annotationValue("annotationValue"), optionalPresent());

        // lists
        assertThat(annotation.stringValues("lstring"),
                   optionalValue(is(List.of("value1", "value2"))));
        assertThat(annotation.booleanValues("lboolean")
                , optionalValue(is(List.of(true, false))));
        assertThat(annotation.longValues("llong"),
                   optionalValue(is(List.of(49L, 50L))));
        assertThat(annotation.doubleValues("ldouble"),
                   optionalValue(is(List.of(49D, 50D))));
        assertThat(annotation.intValues("lint"),
                   optionalValue(is(List.of(49, 50))));
        assertThat(annotation.byteValues("lbyte"),
                   optionalValue(is(List.of((byte) 49, (byte) 50))));
        assertThat(annotation.charValues("lchar"),
                   optionalValue(is(List.of('x', 'y'))));
        assertThat(annotation.shortValues("lshort"),
                   optionalValue(is(List.of((short) 49, (short) 50))));
        assertThat(annotation.floatValues("lfloat"),
                   optionalValue(is(List.of(49F, 50F))));
        assertThat(annotation.typeValues("lclass"),
                   optionalValue(is(List.of(TypeNames.STRING, TypeNames.BOXED_INT))));
        assertThat(annotation.typeValues("ltype"),
                   optionalValue(is(List.of(TypeNames.STRING, TypeNames.BOXED_INT))));
        assertThat(annotation.enumValues("lenum", ElementType.class),
                   optionalValue(is(List.of(ElementType.FIELD, ElementType.MODULE))));
        assertThat(annotation.annotationValues("lannotation"), optionalPresent());
        assertThat(annotation.stringValues("emptyList"), optionalValue(is(List.of())));
        assertThat(annotation.stringValues("singletonList"), optionalValue(is(List.of("value"))));
    }

    @Order(2)
    @Test
    public void testSyntheticAnnotation() {
        Optional<MyAnnotation> synthesizedAnnotation = AnnotationFactory.synthesize(helidonAnnotation);

        assertThat(synthesizedAnnotation, optionalPresent());
        MyAnnotation annotation = synthesizedAnnotation.get();
        AnnotationTest.syntheticAnnotation = annotation;

        assertThat(annotation.annotationType(), sameInstance(MyAnnotation.class));
        assertThat(annotation.stringValue(), is("value1"));
        assertThat(annotation.booleanValue(), is(true));
        assertThat(annotation.longValue(), is(49L));
        assertThat(annotation.doubleValue(), is(49D));
        assertThat(annotation.intValue(), is(49));
        assertThat(annotation.byteValue(), is((byte) 49));
        assertThat(annotation.charValue(), is('x'));
        assertThat(annotation.shortValue(), is((short) 49));
        assertThat(annotation.floatValue(), is(49F));
        assertThat(annotation.typeValue(), sameInstance(String.class));
        assertThat(annotation.typeValue(), sameInstance(String.class));
        assertThat(annotation.enumValue(), is(ElementType.FIELD));

        // lists
        assertThat(annotation.lstring(), arrayContaining("value1", "value2"));
        assertThat("Expected: [true, false], actual: " + Arrays.toString(annotation.lboolean()),
                   Arrays.equals(annotation.lboolean(),
                                 new boolean[] {true, false}));
        assertThat("Expected: [49L, 50L], actual: " + Arrays.toString(annotation.llong()),
                   Arrays.equals(annotation.llong(),
                                 new long[] {49L, 50L}));
        assertThat("Expected: [49D, 50D], actual: " + Arrays.toString(annotation.ldouble()),
                   Arrays.equals(annotation.ldouble(),
                                 new double[] {49L, 50L}));
        assertThat("Expected: [49, 50], actual: " + Arrays.toString(annotation.lint()),
                   Arrays.equals(annotation.lint(),
                                 new int[] {49, 50}));
        assertThat("Expected: [(byte) 49, (byte) 50], actual: " + Arrays.toString(annotation.lbyte()),
                   Arrays.equals(annotation.lbyte(),
                                 new byte[] {(byte) 49, (byte) 50}));
        assertThat("Expected: ['x', 'y'], actual: " + Arrays.toString(annotation.lchar()),
                   Arrays.equals(annotation.lchar(),
                                 new char[] {'x', 'y'}));
        assertThat("Expected: [49, 50] (shorts), actual: " + Arrays.toString(annotation.lshort()),
                   Arrays.equals(annotation.lshort(),
                                 new short[] {49, 50}));
        assertThat("Expected: [49F, 50F], actual: " + Arrays.toString(annotation.lfloat()),
                   Arrays.equals(annotation.lfloat(),
                                 new float[] {49F, 50F}));
        assertThat(annotation.lclass(), arrayContaining(String.class, Integer.class));
        assertThat(annotation.ltype(), arrayContaining(String.class, Integer.class));
        assertThat(annotation.lenum(), arrayContaining(ElementType.FIELD, ElementType.MODULE));
        assertThat(annotation.lannotation(), is(arrayWithSize(2)));
        assertThat(annotation.emptyList(), arrayWithSize(0));
        assertThat(annotation.singletonList(), arrayContaining("value"));
    }

    @Order(2)
    @Test
    public void testEqualsAndHashCode() {
        MyAnnotation synthesizedAnnotation2 = AnnotationFactory.<MyAnnotation>synthesize(helidonAnnotation).get();

        assertThat("Synthesized from same annotation should be equal", syntheticAnnotation, is(synthesizedAnnotation2));
        assertThat("Synthesized from same annotation should have same hash",
                   syntheticAnnotation.hashCode(),
                   is(synthesizedAnnotation2.hashCode()));

        MyAnnotation onType = AnnotatedType.class.getAnnotation(MyAnnotation.class);
        assertThat("Synthesized and real annotation should be equal", syntheticAnnotation, is(onType));
        assertThat("Synthesized and real annotation should have same hash",
                   syntheticAnnotation.hashCode(),
                   is(onType.hashCode()));
    }

    @Order(3)
    @Test
    public void testOtherObjectMethods() {
        String string = syntheticAnnotation.toString();
        assertThat(string, is(notNullValue()));
        Class<? extends MyAnnotation> aClass = syntheticAnnotation.getClass();
        assertThat(aClass, is(notNullValue()));
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

    @MyAnnotation(stringValue = "value1",
                  booleanValue = true,
                  longValue = 49L,
                  doubleValue = 49.0D,
                  intValue = 49,
                  byteValue = (byte) 49,
                  charValue = 'x',
                  shortValue = (short) 49,
                  floatValue = 49.0F,
                  classValue = String.class,
                  typeValue = String.class,
                  enumValue = ElementType.FIELD,
                  annotationValue = @Target(ElementType.CONSTRUCTOR),
                  lstring = {"value1", "value2"},
                  lboolean = {true, false},
                  llong = {49L, 50L},
                  ldouble = {49.0D, 50.0D},
                  lint = {49, 50},
                  lbyte = {(byte) 49, (byte) 50},
                  lchar = {'x', 'y'},
                  lshort = {(short) 49, (short) 50},
                  lfloat = {49.0F, 50.0F},
                  lclass = {String.class, java.lang.Integer.class},
                  ltype = {String.class, java.lang.Integer.class},
                  lenum = {ElementType.FIELD, ElementType.MODULE},
                  lannotation = {@Target(ElementType.CONSTRUCTOR), @Target(ElementType.FIELD)},
                  emptyList = {},
                  singletonList = "value")
    private static class AnnotatedType {
    }
}
