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

package io.helidon.codegen.apt;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;

class AptTypeFactoryTest {
    @Test
    void testPrimitiveReturnTypeAnnotations() {
        TypeInfo type = compile("""
                @A boolean booleanValue();
                @A byte byteValue();
                @A short shortValue();
                @A int intValue();
                @A long longValue();
                @A char charValue();
                @A float floatValue();
                @A double doubleValue();
                """);

        assertAll(List.of("boolean", "byte", "short", "int", "long", "char", "float", "double")
                          .stream()
                          .map(primitive -> () -> {
                              TypeName returnType = returnType(type, primitive + "Value");
                              assertThat(primitive + " type", returnType.className(), is(primitive));
                              assertThat(primitive + " primitive", returnType.primitive(), is(true));
                              assertAnnotations(primitive + " return", returnType, "A");
                          }));
    }

    @Test
    void testDeclaredReturnTypeAnnotations() {
        TypeName type = returnType(compile("@A String value();"), "value");

        assertThat(type.fqName(), is("java.lang.String"));
        assertAnnotations("declared return", type, "A");
    }

    @Test
    void testPrimitiveArrayComponentAnnotation() {
        TypeName type = returnType(compile("@A int[] value();"), "value");

        assertArray(type, "int", List.of(), List.of("A"));
    }

    @Test
    void testPrimitiveArrayAnnotation() {
        TypeName type = returnType(compile("int @B [] value();"), "value");

        assertArray(type, "int", List.of("B"), List.of());
    }

    @Test
    void testPrimitiveArrayDimensionAnnotations() {
        TypeName type = returnType(compile("@A int @B [] @C [] value();"), "value");

        assertArray(type, "int", List.of("B"), List.of("C"), List.of("A"));
    }

    @Test
    void testDeclaredArrayComponentAnnotation() {
        TypeName type = returnType(compile("@A String[] value();"), "value");

        assertArray(type, "java.lang.String", List.of(), List.of("A"));
    }

    @Test
    void testDeclaredArrayAnnotation() {
        TypeName type = returnType(compile("String @B [] value();"), "value");

        assertArray(type, "java.lang.String", List.of("B"), List.of());
    }

    @Test
    void testDeclaredArrayDimensionAnnotations() {
        TypeName type = returnType(compile("@A String @B [] @C [] value();"), "value");

        assertArray(type, "java.lang.String", List.of("B"), List.of("C"), List.of("A"));
    }

    @Test
    void testParameterizedArrayPreservesTypeArgumentAnnotations() {
        TypeName type = returnType(compile("@A List<@C String> @B [] value();"), "value");
        TypeName component = type.componentType().orElseThrow();

        assertAll(() -> assertArray(type, "java.util.List", List.of("B"), List.of("A")),
                  () -> {
                      assertThat("array type arguments", type.typeArguments(), hasSize(1));
                      assertThat("component type arguments", component.typeArguments(), hasSize(1));
                      TypeName argument = component.typeArguments().getFirst();
                      assertThat(argument.fqName(), is("java.lang.String"));
                      assertAnnotations("component type argument", argument, "C");
                      assertAnnotations("array type argument", type.typeArguments().getFirst(), "C");
                  });
    }

    @Test
    void testTypeVariableAnnotations() {
        TypeName type = returnType(compile("@A T value();"), "value");

        assertAll(() -> assertTypeVariable(type, "T", "A"),
                  () -> assertThat("type variable upper bound",
                                   type.upperBounds().stream().map(TypeName::fqName).toList(),
                                   is(List.of("java.lang.Number"))));
    }

    @Test
    void testTypeVariableArrayAnnotations() {
        TypeName type = returnType(compile("@A T @B [] value();"), "value");

        assertAll(() -> assertArray(type, "T", List.of("B"), List.of("A")),
                  () -> assertThat("array type variable name", type.className(), is("T")),
                  () -> assertTypeVariable(type.componentType().orElseThrow(), "T", "A"));
    }

    @Test
    void testRecursiveTypeVariableAnnotations() {
        TypeName type = returnType(compile("<R extends Comparable<@A R>> @B R recursive();"), "recursive");

        assertTypeVariable(type, "R", "B");
        assertThat("recursive upper bounds", type.upperBounds(), hasSize(1));
        TypeName bound = type.upperBounds().getFirst();
        assertThat("recursive upper bound", bound.fqName(), is("java.lang.Comparable"));
        assertThat("recursive bound arguments", bound.typeArguments(), hasSize(1));
        TypeName reference = bound.typeArguments().getFirst();
        assertTypeVariable(reference, "R", "A");
    }

    @Test
    void testWildcardAnnotations() {
        TypeInfo type = compile("""
                List<@A ? extends @B Number> upper();
                List<@B ? super @C String> lower();
                List<@C ?> unbounded();
                """);
        TypeName upper = returnType(type, "upper").typeArguments().getFirst();
        TypeName lower = returnType(type, "lower").typeArguments().getFirst();
        TypeName unbounded = returnType(type, "unbounded").typeArguments().getFirst();

        assertAll(() -> {
            assertThat("upper wildcard", upper.wildcard(), is(true));
            assertAnnotations("upper wildcard", upper, "A");
            assertThat("upper bounds", upper.upperBounds(), hasSize(1));
            assertThat("upper bound type", upper.upperBounds().getFirst().fqName(), is("java.lang.Number"));
            assertAnnotations("upper bound", upper.upperBounds().getFirst(), "B");
            assertThat("upper wildcard lower bounds", upper.lowerBounds(), empty());
        }, () -> {
            assertThat("lower wildcard", lower.wildcard(), is(true));
            assertAnnotations("lower wildcard", lower, "B");
            assertThat("lower bounds", lower.lowerBounds(), hasSize(1));
            assertThat("lower bound type", lower.lowerBounds().getFirst().fqName(), is("java.lang.String"));
            assertAnnotations("lower bound", lower.lowerBounds().getFirst(), "C");
            assertThat("lower wildcard upper bounds", lower.upperBounds(), empty());
        }, () -> {
            assertThat("unbounded wildcard", unbounded.wildcard(), is(true));
            assertAnnotations("unbounded wildcard", unbounded, "C");
            assertThat("unbounded wildcard upper bounds", unbounded.upperBounds(), empty());
            assertThat("unbounded wildcard lower bounds", unbounded.lowerBounds(), empty());
        });
    }

    private static TypeInfo compile(String methods) {
        var typeInfo = new AtomicReference<TypeInfo>();
        var result = TestCompiler.builder()
                .addProcessor(new AbstractProcessor() {
                    @Override
                    public Set<String> getSupportedAnnotationTypes() {
                        return Set.of("*");
                    }

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (!roundEnv.processingOver()) {
                            AptContext context = AptContext.create(processingEnv, Set.of());
                            TypeElement type = processingEnv.getElementUtils().getTypeElement("com.example.Example");
                            typeInfo.set(AptTypeInfoFactory.create(context, type, ElementInfoPredicates.ALL_PREDICATE)
                                                 .orElseThrow());
                        }
                        return false;
                    }
                })
                .currentRelease()
                .addSource("com/example/Example.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;
                        import java.util.List;

                        @Target(ElementType.TYPE_USE)
                        @Retention(RetentionPolicy.CLASS)
                        @interface A {
                        }

                        @Target(ElementType.TYPE_USE)
                        @Retention(RetentionPolicy.CLASS)
                        @interface B {
                        }

                        @Target(ElementType.TYPE_USE)
                        @Retention(RetentionPolicy.CLASS)
                        @interface C {
                        }

                        interface Example<T extends Number> {
                            %s
                        }
                        """.formatted(methods))
                .build()
                .compile();

        assertThat("Compilation diagnostics: " + result.diagnostics(), result.success(), is(true));
        assertThat("compiled type metadata", typeInfo.get(), notNullValue());
        return typeInfo.get();
    }

    private static TypeName returnType(TypeInfo type, String methodName) {
        return type.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.METHOD)
                .filter(it -> it.elementName().equals(methodName))
                .findFirst()
                .orElseThrow()
                .typeName();
    }

    @SafeVarargs
    private static void assertArray(TypeName type, String componentName, List<String>... annotationsByDepth) {
        TypeName current = type;
        for (int depth = 0; depth < annotationsByDepth.length; depth++) {
            String position = "array depth " + depth;
            assertAnnotations(position, current, annotationsByDepth[depth].toArray(String[]::new));
            boolean array = depth < annotationsByDepth.length - 1;
            assertThat(position + " array", current.array(), is(array));
            if (array) {
                current = current.componentType().orElseThrow();
            } else {
                assertThat(position + " component type", current.fqName(), is(componentName));
                assertThat(position + " component", current.componentType().isEmpty(), is(true));
            }
        }
    }

    private static void assertAnnotations(String position, TypeName type, String... annotations) {
        assertThat(position + " annotations",
                   type.annotations().stream().map(annotation -> annotation.typeName().fqName()).toList(),
                   is(List.of(annotations).stream().map(name -> "com.example." + name).toList()));
    }

    private static void assertTypeVariable(TypeName type, String name, String... annotations) {
        assertAll(() -> assertThat("type variable name", type.className(), is(name)),
                  () -> assertThat("type variable generic", type.generic(), is(true)),
                  () -> assertThat("type variable array", type.array(), is(false)),
                  () -> assertThat("type variable component", type.componentType().isEmpty(), is(true)),
                  () -> assertAnnotations("type variable " + name, type, annotations));
    }
}
