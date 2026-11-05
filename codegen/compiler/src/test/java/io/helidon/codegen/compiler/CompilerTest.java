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

package io.helidon.codegen.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.helidon.codegen.CodegenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompilerTest {
    @TempDir
    Path directory;

    @Test
    void closesDependencyJarAfterSuccessfulCompilation() throws IOException {
        Path dependency = dependencyJar();
        Path source = directory.resolve("Consumer.java");
        Files.writeString(source, """
                import fixture.Dependency;
                public class Consumer {
                    public String value() {
                        return Dependency.value();
                    }
                }
                """);
        Path output = Files.createDirectory(directory.resolve("compiled"));
        var options = CompilerOptions.builder()
                .classpath(List.of(dependency))
                .commandLineArguments(List.of("-proc:none"))
                .outputDirectory(output)
                .build();

        Compiler.compile(options, source);

        assertThat("Compilation must resolve the dependency JAR and produce the consumer class",
                   Files.exists(output.resolve("Consumer.class")), is(true));
        replaceAndDelete(dependency);
    }

    @Test
    void closesDependencyJarAfterCompilationFailure() throws IOException {
        Path dependency = dependencyJar();
        Path source = directory.resolve("Consumer.java");
        Files.writeString(source, """
                import fixture.Dependency;
                public class Consumer {
                    public String value() {
                        Dependency dependency = new Dependency();
                        return dependency.missingMember();
                    }
                }
                """);
        Path output = Files.createDirectory(directory.resolve("compiled"));
        var options = CompilerOptions.builder()
                .classpath(List.of(dependency))
                .commandLineArguments(List.of("-proc:none"))
                .outputDirectory(output)
                .build();

        var failure = assertThrows(CodegenException.class, () -> Compiler.compile(options, source));

        assertThat(failure.getMessage(), containsString("Consumer.java"));
        assertThat(failure.getMessage(), containsString("missingMember()"));
        assertThat(failure.getMessage(), containsString("fixture.Dependency"));
        assertThat("A failed compilation must not produce the consumer class",
                   Files.exists(output.resolve("Consumer.class")), is(false));
        replaceAndDelete(dependency);
    }

    private Path dependencyJar() throws IOException {
        Path source = directory.resolve("Dependency.java");
        Files.writeString(source, """
                package fixture;
                public class Dependency {
                    public static String value() { return "dependency"; }
                }
                """);
        Path classes = Files.createDirectory(directory.resolve("dependency-classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(List.of(source));
            var task = compiler.getTask(null, files, diagnostics,
                                       List.of("-proc:none", "--release", "27", "-d", classes.toString()), null, units);
            var successful = task.call();
            assertThat("Dependency fixture compilation: " + diagnostics.getDiagnostics(), successful, is(true));
        }
        Path jar = directory.resolve("dependency.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("fixture/Dependency.class"));
            Files.copy(classes.resolve("fixture/Dependency.class"), output);
            output.closeEntry();
        }
        return jar;
    }

    private void replaceAndDelete(Path dependency) throws IOException {
        Path replacement = directory.resolve("replacement.jar");
        Files.copy(dependency, replacement);
        Files.move(replacement, dependency, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(dependency);
        assertThat("The dependency JAR must be replaceable and deletable immediately after compilation",
                   Files.exists(dependency), is(false));
    }
}
