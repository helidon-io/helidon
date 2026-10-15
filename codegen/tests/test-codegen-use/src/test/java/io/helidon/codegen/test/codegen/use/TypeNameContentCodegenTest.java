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

package io.helidon.codegen.test.codegen.use;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class TypeNameContentCodegenTest {
    private static final String PACKAGE_NAME = "io.helidon.codegen.test.codegen.use.generated";
    private static final String PACKAGE_PATH = PACKAGE_NAME.replace('.', '/');

    @Test
    void testAnnotatedNestedTypeNameCompiles() throws IOException, ReflectiveOperationException {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .property("value", "quoted \" backslash \\ tab \t\nblock \"\"\" end")
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

        GeneratedModel model = typeNameModel("NestedTypeName", typeName);
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(model))
                .addClasspath(List.of(Builder.class,
                                      Prototype.class,
                                      Annotation.class,
                                      ElementKind.class,
                                      TypeName.class,
                                      TypedElementInfo.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = generatedSource(result, "NestedTypeName");

        assertThat(source, not(containsString("TypeName.create(\"java.util.Map<java.lang.String,")));
        assertContainsInOrder(source,
                              "return TypeName.builder()",
                              ".packageName(\"java.util\")",
                              ".className(\"Map\")",
                              ".addTypeArgument(TypeName.create(\"java.lang.String\"))",
                              ".addTypeArgument(TypeName.builder()",
                              ".packageName(\"java.util\")",
                              ".className(\"Map\")",
                              ".addTypeArgument(TypeName.create(\"java.lang.String\"))",
                              ".addTypeArgument(TypeName.builder()",
                              ".packageName(\"java.lang\")",
                              ".className(\"String\")",
                              ".addAnnotation(Annotation.builder()",
                              ".typeName(TypeName.create(\"io.helidon.RandomAnnotation\"))",
                              ".property(\"value\", \"quoted \\\" backslash \\\\ tab \\t\\nblock \\\"\\\"\\\" end\")",
                              ".build()");
        assertClassGenerated(result, "NestedTypeName");

        TypeName generatedTypeName = generatedTypeName(result, "NestedTypeName");
        assertThat(generatedTypeName.typeArguments()
                           .get(1)
                           .typeArguments()
                           .get(1)
                           .annotations(),
                   hasItem(annotation));
    }

    @Test
    void testAnnotatedBoundTypeNameCompiles() throws IOException, ReflectiveOperationException {
        Annotation annotation = randomAnnotation();
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(annotation)
                                                        .build())
                                         .build())
                .build();

        GeneratedModel model = typeNameModel("BoundTypeName", typeName);
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(model))
                .addClasspath(List.of(Builder.class,
                                      Prototype.class,
                                      Annotation.class,
                                      ElementKind.class,
                                      TypeName.class,
                                      TypedElementInfo.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = generatedSource(result, "BoundTypeName");

        assertThat(source, not(containsString("TypeName.create(\"java.util.List<? extends")));
        assertContainsInOrder(source,
                              "return TypeName.builder()",
                              ".packageName(\"java.util\")",
                              ".className(\"List\")",
                              ".addTypeArgument(TypeName.builder()",
                              ".wildcard(true)",
                              ".addUpperBound(TypeName.builder()",
                              ".packageName(\"java.lang\")",
                              ".className(\"String\")",
                              ".addAnnotation(Annotation.create(TypeName.create(\"io.helidon.RandomAnnotation\")))",
                              ".build()");
        assertClassGenerated(result, "BoundTypeName");

        TypeName generatedTypeName = generatedTypeName(result, "BoundTypeName");
        assertThat(generatedTypeName.typeArguments()
                           .getFirst()
                           .upperBounds()
                           .getFirst()
                           .annotations(),
                   hasItem(annotation));
    }

    @Test
    void testAnnotatedLowerBoundTypeNameCompiles() throws IOException, ReflectiveOperationException {
        Annotation annotation = randomAnnotation();
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addLowerBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(annotation)
                                                        .build())
                                         .build())
                .build();

        GeneratedModel model = typeNameModel("LowerBoundTypeName", typeName);
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(model))
                .addClasspath(List.of(Builder.class,
                                      Prototype.class,
                                      Annotation.class,
                                      ElementKind.class,
                                      TypeName.class,
                                      TypedElementInfo.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = generatedSource(result, "LowerBoundTypeName");

        assertThat(source, not(containsString("TypeName.create(\"java.util.List<? super")));
        assertContainsInOrder(source,
                              "return TypeName.builder()",
                              ".packageName(\"java.util\")",
                              ".className(\"List\")",
                              ".addTypeArgument(TypeName.builder()",
                              ".wildcard(true)",
                              ".addLowerBound(TypeName.builder()",
                              ".packageName(\"java.lang\")",
                              ".className(\"String\")",
                              ".addAnnotation(Annotation.create(TypeName.create(\"io.helidon.RandomAnnotation\")))",
                              ".build()");
        assertClassGenerated(result, "LowerBoundTypeName");

        TypeName generatedTypeName = generatedTypeName(result, "LowerBoundTypeName");
        assertThat(generatedTypeName.typeArguments()
                           .getFirst()
                           .lowerBounds()
                           .getFirst()
                           .annotations(),
                   hasItem(annotation));
    }

    @Test
    void testAnnotatedComponentTypeNameCompiles() throws IOException, ReflectiveOperationException {
        Annotation annotation = randomAnnotation();
        TypeName componentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(annotation)
                .build();
        TypeName typeName = TypeName.builder(TypeNames.STRING)
                .array(true)
                .componentType(componentType)
                .build();

        GeneratedModel model = typeNameModel("ComponentTypeName", typeName);
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(model))
                .addClasspath(List.of(Builder.class,
                                      Prototype.class,
                                      Annotation.class,
                                      ElementKind.class,
                                      TypeName.class,
                                      TypedElementInfo.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = generatedSource(result, "ComponentTypeName");

        assertThat(source, not(containsString("TypeName.create(\"java.lang.String[]")));
        assertContainsInOrder(source,
                              "return TypeName.builder()",
                              ".packageName(\"java.lang\")",
                              ".className(\"String\")",
                              ".array(true)",
                              ".componentType(TypeName.builder()",
                              ".packageName(\"java.lang\")",
                              ".className(\"String\")",
                              ".addAnnotation(Annotation.create(TypeName.create(\"io.helidon.RandomAnnotation\")))",
                              ".build()");
        assertClassGenerated(result, "ComponentTypeName");

        TypeName generatedTypeName = generatedTypeName(result, "ComponentTypeName");
        assertThat(generatedTypeName.componentType().orElseThrow().annotations(), hasItem(annotation));
    }

    @Test
    void testInheritedAnnotatedTypeNameCompiles() throws IOException, ReflectiveOperationException {
        Annotation annotation = randomAnnotation();
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder(TypeNames.STRING)
                                         .addInheritedAnnotation(annotation)
                                         .build())
                .build();

        GeneratedModel model = typeNameModel("InheritedTypeName", typeName);
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(model))
                .addClasspath(List.of(Builder.class,
                                      Prototype.class,
                                      Annotation.class,
                                      ElementKind.class,
                                      TypeName.class,
                                      TypedElementInfo.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = generatedSource(result, "InheritedTypeName");

        assertContainsInOrder(source,
                              "return TypeName.builder()",
                              ".packageName(\"java.util\")",
                              ".className(\"List\")",
                              ".addTypeArgument(TypeName.builder()",
                              ".packageName(\"java.lang\")",
                              ".className(\"String\")",
                              ".addInheritedAnnotation(Annotation.create(TypeName.create(\"io.helidon.RandomAnnotation\")))",
                              ".build()");
        assertClassGenerated(result, "InheritedTypeName");

        TypeName generatedTypeName = generatedTypeName(result, "InheritedTypeName");
        TypeName generatedTypeArgument = generatedTypeName.typeArguments().getFirst();
        assertThat(generatedTypeArgument.annotations(), not(hasItem(annotation)));
        assertThat(generatedTypeArgument.inheritedAnnotations(), hasItem(annotation));
    }

    @Test
    void testTypedElementEnclosingTypeCompiles() throws IOException {
        TypeName enclosingType = TypeName.create("io.helidon.codegen.classmodel.EnclosingType");
        TypedElementInfo elementInfo = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("method")
                .typeName(TypeNames.STRING)
                .enclosingType(enclosingType)
                .build();

        GeneratedModel model = typedElementModel("ElementInfo", elementInfo);
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(model))
                .addClasspath(List.of(Builder.class,
                                      Prototype.class,
                                      Annotation.class,
                                      ElementKind.class,
                                      TypeName.class,
                                      TypedElementInfo.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = generatedSource(result, "ElementInfo");

        assertThat(source, containsString(".enclosingType(TypeName.create(\"" + enclosingType.fqName() + "\"))"));
        assertClassGenerated(result, "ElementInfo");
    }

    @ParameterizedTest
    @MethodSource("annotationStrings")
    void testAnnotationStringGeneratesCompilableSource(String value, String expectedLiteral, String expectedAnnotationLiteral)
            throws IOException {
        ClassModel classModel = ClassModel.builder()
                .packageName(PACKAGE_NAME)
                .name("AnnotatedString")
                .addMethod(method -> method
                        .isStatic(true)
                        .name("content")
                        .returnType(TypeNames.STRING)
                        .addContent("return ")
                        .addContentLiteral(value)
                        .addContentLine(";"))
                .addMethod(method -> method
                        .isStatic(true)
                        .name("metadata")
                        .returnType(TypeName.create(Annotation.class))
                        .addContent("return ")
                        .addContentCreate(Annotation.builder()
                                                  .typeName(TypeName.create(Deprecated.class))
                                                  .property("since", value)
                                                  .build())
                        .addContentLine(";"))
                .addAnnotation(Annotation.builder()
                                       .typeName(TypeName.create(Deprecated.class))
                                       .property("since", value)
                                       .build())
                .build();
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ContentProcessor(new GeneratedModel("AnnotatedString", classModel)))
                .addClasspath(List.of(Builder.class, Prototype.class, Annotation.class, TypeName.class))
                .printDiagnostics(false)
                .addSource("io/helidon/codegen/test/codegen/use/generated/Trigger.java", """
                        package io.helidon.codegen.test.codegen.use.generated;

                        final class Trigger {
                        }
                        """)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(generatedSource(result, "AnnotatedString"), matches("""
                //...
                @Deprecated(since = "%s")
                public class AnnotatedString {
                //...
                        return "%s";
                //...
                                .property("since", "%s")
                //...
                """.formatted(expectedAnnotationLiteral, expectedLiteral, expectedLiteral)));
        assertClassGenerated(result, "AnnotatedString");
    }

    private static Stream<Arguments> annotationStrings() {
        return Stream.of(literalCase("", ""),
                         literalCase("plain", "plain"),
                         literalCase("\"", "\\\""),
                         literalCase("a\"b", "a\\\"b"),
                         literalCase("a\"\"\"b", "a\\\"\\\"\\\"b"),
                         literalCase("\\u000d", "\\\\u000d"),
                         literalCase("^a\rb$", "^a\\rb$"),
                         literalCase("^a\r\nb$", "^a\\r\\nb$"),
                         literalCase("^a\nb$", "^a\\nb$"),
                         literalCase("^\\Qa\rb\\E$", "^\\\\Qa\\rb\\\\E$"),
                         literalCase("(?x)^a\rb$", "(?x)^a\\rb$"),
                         literalCase("(?x)^a#comment\rb$", "(?x)^a#comment\\rb$"),
                         literalCase("^a\\\rb$", "^a\\\\\\rb$"),
                         literalCase("^a\tb\bc\fd\u0001$", "^a\\tb\\bc\\fd\\u0001$"),
                         literalCase(new String(new char[] {0, 1, 7, 8, 9, 10, 11, 12, 13, 26, 31, 127, 133, 159}),
                                     "\\u0000\\u0001\\u0007\\b\\t\\n\\u000b\\f\\r\\u001a\\u001f\u007f\u0085\u009f"),
                         literalCase("\ud83d\ude00", "\\ud83d\\ude00"),
                         literalCase("x\ud800y", "x\\ud800y"),
                         literalCase("x\udc00y", "x\\udc00y"),
                         literalCase("x\u2028y\u2029z", "x\u2028y\u2029z"),
                         Arguments.of("\"source\\nquoted\"", "\\\"source\\\\nquoted\\\"", "source\\nquoted"),
                         Arguments.of("\"\"", "\\\"\\\"", ""));
    }

    private static Arguments literalCase(String value, String expectedLiteral) {
        return Arguments.of(value, expectedLiteral, expectedLiteral);
    }

    private static Annotation randomAnnotation() {
        return Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .build();
    }

    private static GeneratedModel typeNameModel(String className, TypeName typeName) {
        ClassModel classModel = ClassModel.builder()
                .packageName(PACKAGE_NAME)
                .name(className)
                .addMethod(method -> method
                        .isStatic(true)
                        .name("create")
                        .returnType(TypeName.create(TypeName.class))
                        .addContent("return ")
                        .addContentCreate(typeName)
                        .addContentLine(";"))
                .build();
        return new GeneratedModel(className, classModel);
    }

    private static GeneratedModel typedElementModel(String className, TypedElementInfo elementInfo) {
        ClassModel classModel = ClassModel.builder()
                .packageName(PACKAGE_NAME)
                .name(className)
                .addMethod(method -> method
                        .isStatic(true)
                        .name("create")
                        .returnType(TypeName.create(TypedElementInfo.class))
                        .addContent("return ")
                        .addContentCreate(elementInfo)
                        .addContentLine(";"))
                .build();
        return new GeneratedModel(className, classModel);
    }

    private static String generatedSource(TestCompiler.Result result, String className) throws IOException {
        Path generatedSource = result.sourceOutput().resolve(PACKAGE_PATH + "/" + className + ".java");
        assertThat("Generated source should exist: " + generatedSource, Files.exists(generatedSource), is(true));
        return Files.readString(generatedSource);
    }

    private static void assertClassGenerated(TestCompiler.Result result, String className) {
        assertThat(Files.exists(result.classOutput().resolve(PACKAGE_PATH + "/" + className + ".class")), is(true));
    }

    private static TypeName generatedTypeName(TestCompiler.Result result, String className)
            throws IOException, ReflectiveOperationException {
        URL[] urls = {result.classOutput().toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(urls, TypeNameContentCodegenTest.class.getClassLoader())) {
            Class<?> generatedClass = Class.forName(PACKAGE_NAME + "." + className, true, classLoader);
            return (TypeName) generatedClass.getMethod("create").invoke(null);
        }
    }

    private static void assertContainsInOrder(String source, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int current = source.indexOf(fragment, previous + 1);
            assertThat("Missing fragment after index " + previous + ": " + fragment, current > previous, is(true));
            previous = current;
        }
    }

    private record GeneratedModel(String className, ClassModel classModel) {
    }

    private static final class ContentProcessor extends AbstractProcessor {
        private final GeneratedModel model;
        private boolean processed;

        private ContentProcessor(GeneratedModel model) {
            this.model = model;
        }

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
            if (processed || roundEnv.processingOver()) {
                return false;
            }
            processed = true;
            try {
                var sourceFile = processingEnv.getFiler()
                        .createSourceFile(PACKAGE_NAME + "." + model.className());
                try (Writer writer = sourceFile.openWriter()) {
                    model.classModel().write(writer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return false;
        }
    }
}
