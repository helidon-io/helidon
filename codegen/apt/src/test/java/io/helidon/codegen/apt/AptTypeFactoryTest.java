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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

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
    void testEnclosingTypeAnnotationsAndArguments() {
        TypeName type = returnType(compile("@A Outer<@B String>.@C Inner<@A Integer> value();"), "value");
        TypeName owner = type.enclosingType().orElseThrow();

        assertAll(() -> assertAnnotations("inner", type, "C"),
                  () -> assertAnnotations("outer", owner, "A"),
                  () -> assertAnnotations("outer argument", owner.typeArguments().getFirst(), "B"),
                  () -> assertAnnotations("inner argument", type.typeArguments().getFirst(), "A"),
                  () -> assertThat("outer type", owner.fqName(), is("com.example.Outer")),
                  () -> assertThat("outer owner", owner.enclosingType().isEmpty(), is(true)),
                  () -> assertThat("inner type", type.fqName(), is("com.example.Outer.Inner")),
                  () -> assertThat("inner nesting", type.enclosingNames(), is(List.of("Outer"))),
                  () -> assertThat("inner resolved name", type.resolvedName(),
                                   is("com.example.Outer.Inner<java.lang.Integer>")),
                  () -> assertThat("type identity", type,
                                   is(TypeName.create("com.example.Outer.Inner<java.lang.Integer>"))));
    }

    @Test
    void testThreeEnclosingTypeAnnotationLevels() {
        TypeName type = returnType(compile("""
                @A Outer<@C String>.@B Inner<@A Integer>.@C Deep<@B Long> value();
                """), "value");
        TypeName middle = type.enclosingType().orElseThrow();
        TypeName outer = middle.enclosingType().orElseThrow();

        assertAll(() -> assertAnnotations("deep", type, "C"),
                  () -> assertAnnotations("middle", middle, "B"),
                  () -> assertAnnotations("outer", outer, "A"),
                  () -> assertAnnotations("deep argument", type.typeArguments().getFirst(), "B"),
                  () -> assertAnnotations("middle argument", middle.typeArguments().getFirst(), "A"),
                  () -> assertAnnotations("outer argument", outer.typeArguments().getFirst(), "C"),
                  () -> assertThat("middle type", middle.fqName(), is("com.example.Outer.Inner")),
                  () -> assertThat("deep type", type.fqName(), is("com.example.Outer.Inner.Deep")),
                  () -> assertThat("deep nesting", type.enclosingNames(), is(List.of("Outer", "Inner"))),
                  () -> assertThat("outer owner", outer.enclosingType().isEmpty(), is(true)));
    }

    @Test
    void testOwnerOnlyAnnotationsInNestedArrayArgument() {
        TypeName list = returnType(compile("List<@A Outer<@B String>.Inner<Integer> @C []> value();"), "value");
        TypeName array = list.typeArguments().getFirst();
        TypeName inner = array.componentType().orElseThrow();
        TypeName outer = inner.enclosingType().orElseThrow();

        assertAll(() -> assertAnnotations("array", array, "C"),
                  () -> assertAnnotations("unannotated inner", inner),
                  () -> assertAnnotations("outer", outer, "A"),
                  () -> assertAnnotations("outer argument", outer.typeArguments().getFirst(), "B"),
                  () -> assertAnnotations("unannotated inner argument", inner.typeArguments().getFirst()),
                  () -> assertThat("array component", inner.fqName(), is("com.example.Outer.Inner")),
                  () -> assertThat("array dimension", array.array(), is(true)),
                  () -> assertThat("component dimension", inner.array(), is(false)));
    }

    @Test
    void testRawAndStaticNestedTypeAnnotations() {
        TypeInfo info = compile("""
                @A Outer.@B Inner raw();
                Outer.@C Static<@B String> staticNested();
                """);
        TypeName raw = returnType(info, "raw");
        TypeName rawOwner = raw.enclosingType().orElseThrow();
        TypeName staticNested = returnType(info, "staticNested");

        assertAll(() -> assertAnnotations("raw inner", raw, "B"),
                  () -> assertAnnotations("raw outer", rawOwner, "A"),
                  () -> assertThat("raw inner arguments", raw.typeArguments(), empty()),
                  () -> assertThat("raw outer arguments", rawOwner.typeArguments(), empty()),
                  () -> assertAnnotations("static nested", staticNested, "C"),
                  () -> assertAnnotations("static argument", staticNested.typeArguments().getFirst(), "B"),
                  () -> assertThat("static owner", staticNested.enclosingType().isEmpty(), is(true)),
                  () -> assertThat("static nesting", staticNested.enclosingNames(), is(List.of("Outer"))),
                  () -> assertThat("static type", staticNested.fqName(), is("com.example.Outer.Static")));
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

    @Test
    void testImportedGeneratedTypesInNestedSignatures() {
        AtomicReference<TypeInfo> firstRound = new AtomicReference<>();
        var result = TestCompiler.builder()
                .currentRelease()
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
                        if (roundEnv.processingOver() || firstRound.get() != null) {
                            return false;
                        }
                        AptContext ctx = AptContext.create(processingEnv, Set.of());
                        TypeElement source = processingEnv.getElementUtils().getTypeElement("com.acme.Caller");
                        firstRound.set(AptTypeInfoFactory.create(ctx, source, ElementInfoPredicates.ALL_PREDICATE)
                                               .orElseThrow());
                        try (var config = processingEnv.getFiler().createSourceFile("com.acme.spi.GeneratedConfig")
                                     .openWriter();
                                var container = processingEnv.getFiler().createSourceFile("com.acme.spi.Container")
                                        .openWriter()) {
                            config.write("package com.acme.spi; public class GeneratedConfig {}");
                            container.write("package com.acme.spi; "
                                                    + "public class Container { public interface Nested {} }");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        return false;
                    }
                })
                .addSource("com/acme/Caller.java", """
                        package com.acme;

                        import java.util.List;
                        import java.util.Map;
                        import com.acme.spi.Container;
                        import com.acme.spi.GeneratedConfig;
                        import com.acme.spi.T;

                        interface Caller<T extends Comparable<T>> {
                            Map<String, List<? extends GeneratedConfig[]>> incoming();
                            List<? super GeneratedConfig> outgoing();
                            List<? extends T[]> genericArrays();
                            GeneratedConfig direct();
                            Container.Nested nested();
                            String resolved();
                            T typeVariable();
                        }
                        """)
                .addSource("com/acme/spi/T.java", """
                        package com.acme.spi;

                        public class T {
                        }
                        """)
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
        TypeInfo observed = firstRound.get();
        assertThat(observed, notNullValue());
        TypedElementInfo incoming = method(observed, "incoming");
        assertThat(incoming.componentTypes(), is(incoming.typeName().typeArguments()));
        TypeName array = incoming.typeName().typeArguments().get(1).typeArguments().getFirst().upperBounds().getFirst();
        assertThat(array.packageName(), is("com.acme.spi"));
        assertThat(array.className(), is("GeneratedConfig"));
        assertThat(array.array(), is(true));
        assertThat(array.componentType().orElseThrow(), is(TypeName.create("com.acme.spi.GeneratedConfig")));
        TypeName lower = method(observed, "outgoing").typeName().typeArguments().getFirst().lowerBounds().getFirst();
        assertThat(lower, is(TypeName.create("com.acme.spi.GeneratedConfig")));
        assertThat(method(observed, "direct").typeName(), is(TypeName.create("com.acme.spi.GeneratedConfig")));
        assertThat(method(observed, "nested").typeName(), is(TypeName.create("com.acme.spi.Container.Nested")));
        assertThat(method(observed, "resolved").typeName(), is(TypeName.create(String.class)));
        TypeName typeVariable = method(observed, "typeVariable").typeName();
        assertThat(typeVariable.className(), is("T"));
        assertThat(typeVariable.packageName(), is(""));
        assertThat(typeVariable.generic(), is(true));
        TypeName genericArray = method(observed, "genericArrays").typeName()
                .typeArguments().getFirst().upperBounds().getFirst();
        assertThat(genericArray.packageName(), is(""));
        assertThat(genericArray.componentType().orElseThrow().generic(), is(true));
    }

    @Test
    void preservesSourceAndClassfileValueProvenance() {
        Map<String, Annotation> source = new LinkedHashMap<>();
        TestCompiler.Result parent = TestCompiler.builder().currentRelease()
                .addProcessor(new ModelProcessor(source))
                .addSource("example/Library.java", """
                        package example;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.CLASS)
                        @interface Choice {
                            Mode mode() default Mode.NONE;
                            int count() default 0;
                            Nested nested() default @Nested;
                        }
                        enum Mode { NONE }
                        @interface Nested { String value() default "default"; }
                        @Choice class Implicit { }
                        @Choice(mode = Mode.NONE, count = 0, nested = @Nested("default")) class Explicit { }
                        """)
                .build().compile();
        assertThat(parent.diagnostics().toString(), parent.success(), is(true));
        Map<String, Annotation> binary = new LinkedHashMap<>();
        TestCompiler.Result child = TestCompiler.builder().currentRelease()
                .addProcessor(new ModelProcessor(binary))
                .addClasspathEntry(parent.classOutput())
                .addSource("example/Child.java", "package example; class Child { }")
                .build().compile();
        assertThat(child.diagnostics().toString(), child.success(), is(true));
        for (Map<String, Annotation> model : List.of(source, binary)) {
            Annotation implicit = model.get("Implicit");
            Annotation explicit = model.get("Explicit");
            assertThat(implicit, is(explicit));
            assertThat(implicit.hashCode(), is(explicit.hashCode()));
            for (String property : Set.of("mode", "count", "nested")) {
                assertThat(property, implicit.property(property).orElseThrow().explicit(), is(false));
                assertThat(property, explicit.property(property).orElseThrow().explicit(), is(true));
            }
            assertThat(implicit.annotationValue("nested").orElseThrow().property("value").orElseThrow().explicit(), is(false));
            assertThat(explicit.annotationValue("nested").orElseThrow().property("value").orElseThrow().explicit(), is(true));
        }
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

                        class Outer<T> {
                            class Inner<U> {
                                class Deep<V> {
                                }
                            }

                            static class Static<V> {
                            }
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

    private static TypedElementInfo method(TypeInfo type, String name) {
        return type.elementInfo().stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> it.elementName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static final class ModelProcessor extends AbstractProcessor {
        private final Map<String, Annotation> values;

        private ModelProcessor(Map<String, Annotation> values) {
            this.values = values;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_27;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (!round.processingOver() && values.isEmpty()) {
                for (String name : Set.of("Implicit", "Explicit")) {
                    TypeElement type = processingEnv.getElementUtils().getTypeElement("example." + name);
                    values.put(name, AptAnnotationFactory.createAnnotation(type.getAnnotationMirrors().getFirst(),
                                                                           processingEnv.getElementUtils()));
                }
            }
            return false;
        }
    }
}
