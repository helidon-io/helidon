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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class TypeNameContentCodegenTest {
    private static final String PACKAGE_NAME = "io.helidon.codegen.test.codegen.use.generated";
    private static final String PACKAGE_PATH = PACKAGE_NAME.replace('.', '/');

    @Test
    void testAnnotatedNestedTypeNameCompiles() throws IOException {
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
                              ".property(\"value\", \"quoted \\\" backslash \\\\ tab \\t\")",
                              ".build()");
        assertClassGenerated(result, "NestedTypeName");
    }

    @Test
    void testAnnotatedBoundTypeNameCompiles() throws IOException {
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addUpperBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(randomAnnotation())
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
    }

    @Test
    void testAnnotatedLowerBoundTypeNameCompiles() throws IOException {
        TypeName typeName = TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder()
                                         .className("?")
                                         .wildcard(true)
                                         .addLowerBound(TypeName.builder(TypeNames.STRING)
                                                        .addAnnotation(randomAnnotation())
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
    }

    @Test
    void testAnnotatedComponentTypeNameCompiles() throws IOException {
        TypeName componentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(randomAnnotation())
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
