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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void testAnnotatedNestedTypeName(@TempDir Path tempDir) throws Exception {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
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
        assertThat(createString, containsString(".addAnnotation(@io.helidon.common.types.Annotation@.create("));
        assertTypeName("NestedTypeName", typeName, compileAndCreateTypeName(tempDir, "NestedTypeName", createString));
    }

    @Test
    void testAnnotatedBoundTypeName(@TempDir Path tempDir) throws Exception {
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
        assertTypeName("BoundTypeName", typeName, compileAndCreateTypeName(tempDir, "BoundTypeName", createString));
    }

    @Test
    void testAnnotatedLowerBoundTypeName(@TempDir Path tempDir) throws Exception {
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
        assertTypeName("LowerBoundTypeName", typeName, compileAndCreateTypeName(tempDir, "LowerBoundTypeName", createString));
    }

    @Test
    void testAnnotatedComponentTypeName(@TempDir Path tempDir) throws Exception {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create("io.helidon.RandomAnnotation"))
                .build();
        TypeName componentType = TypeName.builder(TypeNames.STRING)
                .addAnnotation(annotation)
                .build();
        TypeName typeName = TypeName.builder(componentType)
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
        assertTypeName("ComponentTypeName", typeName, compileAndCreateTypeName(tempDir, "ComponentTypeName", createString));
    }

    private static TypeName compileAndCreateTypeName(Path tempDir, String className, String createString) throws Exception {
        Path source = tempDir.resolve(className + ".java");
        Path output = Files.createDirectory(tempDir.resolve("classes-" + className));
        String sourceContent = """
                import io.helidon.common.types.TypeName;

                public final class %s {
                    public static TypeName create() {
                        return %s;
                    }
                }
                """.formatted(className, javaSource(createString));
        Files.writeString(source, sourceContent, StandardCharsets.UTF_8);

        List<String> command = javacCommand(output, source);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String compilerOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int result = process.waitFor();
        assertThat(compilerOutput, result, is(0));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {output.toUri().toURL()},
                                                             TypesCodegenTest.class.getClassLoader())) {
            Class<?> generatedType = classLoader.loadClass(className);
            Method create = generatedType.getMethod("create");
            return (TypeName) create.invoke(null);
        }
    }

    private static String javaSource(String createString) {
        return createString.replaceAll("@([^@]+)@", "$1");
    }

    private static List<String> javacCommand(Path output, Path source) {
        List<String> command = new ArrayList<>();
        command.add(javac().toString());
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null && !modulePath.isBlank()) {
            command.add("--module-path");
            command.add(modulePath);
            command.add("--add-modules");
            command.add("io.helidon.common.types");
        }
        command.add("-classpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-d");
        command.add(output.toString());
        command.add(source.toString());
        return command;
    }

    private static Path javac() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "javac.exe" : "javac";
        Path javac = Path.of(System.getProperty("java.home"), "bin", executable);
        assertThat("JDK javac is required", Files.isRegularFile(javac), is(true));
        return javac;
    }

    private static void assertTypeName(String description, TypeName expected, TypeName actual) {
        assertThat(description + " package name", actual.packageName(), is(expected.packageName()));
        assertThat(description + " class name", actual.className(), is(expected.className()));
        assertThat(description + " enclosing names", actual.enclosingNames(), is(expected.enclosingNames()));
        assertThat(description + " primitive", actual.primitive(), is(expected.primitive()));
        assertThat(description + " array", actual.array(), is(expected.array()));
        assertThat(description + " vararg", actual.vararg(), is(expected.vararg()));
        assertThat(description + " generic", actual.generic(), is(expected.generic()));
        assertThat(description + " wildcard", actual.wildcard(), is(expected.wildcard()));
        assertThat(description + " annotations", actual.annotations(), is(expected.annotations()));
        assertThat(description + " inherited annotations", actual.inheritedAnnotations(), is(expected.inheritedAnnotations()));
        assertThat(description + " type parameters", actual.typeParameters(), is(expected.typeParameters()));
        assertTypeNames(description + " type argument", expected.typeArguments(), actual.typeArguments());
        assertTypeNames(description + " lower bound", expected.lowerBounds(), actual.lowerBounds());
        assertTypeNames(description + " upper bound", expected.upperBounds(), actual.upperBounds());
        assertThat(description + " component type present",
                   actual.componentType().isPresent(),
                   is(expected.componentType().isPresent()));
        if (expected.componentType().isPresent()) {
            assertTypeName(description + " component type", expected.componentType().orElseThrow(), actual.componentType().orElseThrow());
        }
    }

    private static void assertTypeNames(String description, List<TypeName> expected, List<TypeName> actual) {
        assertThat(description + " size", actual.size(), is(expected.size()));
        for (int i = 0; i < expected.size(); i++) {
            assertTypeName(description + " " + i, expected.get(i), actual.get(i));
        }
    }
}
