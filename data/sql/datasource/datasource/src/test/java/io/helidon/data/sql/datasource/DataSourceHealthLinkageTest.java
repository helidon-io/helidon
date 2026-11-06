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
package io.helidon.data.sql.datasource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DataSourceHealthLinkageTest {
    @TempDir
    Path temporary;

    /**
     * Compiles and executes the check and generated configuration on the classpath and module path.
     */
    @Test
    void linksOnClasspathAndModulePath() throws Exception {
        link(false);
        link(true);
    }

    /**
     * Selects libraries by path components on Unix and Windows, excluding the obsolete health adapter.
     */
    @Test
    void librariesUsePathComponentsForUnixAndWindows() {
        String root = "C:/work/helidon-health-check/helidon";
        String source = root + "/data/sql/datasource/datasource/target/classes";
        String lexical = root + "/.m2/repository/io/helidon/data/jdbc/helidon-data-jdbc-lexical/28/"
                + "helidon-data-jdbc-lexical-28.jar";
        String health = root + "/health/health/target/classes";
        String runtime = root + "/data/jdbc/jdbc/target/classes";
        String runtimeJar = root + "/.m2/repository/io/helidon/data/jdbc/helidon-data-jdbc/28/helidon-data-jdbc-28.jar";
        List<String> entries = List.of(source, lexical, health, runtime, runtimeJar,
                                      root + "/data/jdbc/health/target/classes",
                                      root + "/.m2/repository/helidon-data-jdbc-health-28.jar",
                                      root + "/data/jdbc/jdbc/target/test-classes",
                                      root + "/.m2/repository/unrelated-28.jar");
        List<Path> expected = List.of(Path.of(source), Path.of(lexical), Path.of(health));
        for (List<String> paths : List.of(entries, entries.stream().map(value -> value.replace('/', '\\')).toList())) {
            assertThat(libraries(paths), is(expected));
        }
    }

    private static List<Path> libraries(List<String> entries) {
        return entries.stream()
                .map(value -> Path.of(value.replace('\\', '/')).normalize())
                .filter(path -> !path.endsWith(Path.of("target", "test-classes")))
                .filter(path -> path.getFileName().toString().startsWith("helidon-")
                        || path.endsWith(Path.of("target", "classes")))
                .filter(path -> !path.endsWith(Path.of("data", "jdbc", "health", "target", "classes"))
                        && !path.getFileName().toString().startsWith("helidon-data-jdbc-health-"))
                .filter(path -> !path.endsWith(Path.of("data", "jdbc", "jdbc", "target", "classes"))
                        && !path.getFileName().toString().matches("helidon-data-jdbc-[0-9].*\\.jar"))
                .toList();
    }

    private void link(boolean modular) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path");
        String libraries = libraries(Arrays.asList(classPath.split(File.pathSeparator))).stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        Path directory = Files.createDirectory(temporary.resolve(modular ? "module" : "class"));
        Path source = directory.resolve("sample/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package sample;
                import io.helidon.data.sql.datasource.DataSourceHealthCheck;
                import io.helidon.data.sql.datasource.DataSourceHealthCheckConfig;
                import javax.sql.DataSource;
                import io.helidon.health.HealthCheckType;
                public class Main {
                    public static void main(String[] arguments) {
                        DataSourceHealthCheck.builder().query("SELECT 1").type(HealthCheckType.READINESS);
                        try {
                            DataSourceHealthCheck.create((DataSourceHealthCheckConfig) null);
                            throw new AssertionError("Null configuration must be rejected.");
                        } catch (NullPointerException _) {
                        }
                        try {
                            DataSourceHealthCheck.builder().dataSource((DataSource) null);
                            throw new AssertionError("Null datasource must be rejected.");
                        } catch (NullPointerException _) {
                        }
                    }
                }
                """);
        List<String> options = new ArrayList<>(List.of("--release", "27", "-proc:none", "-d", directory.toString(),
                modular ? "--module-path" : "--class-path", libraries, source.toString()));
        if (modular) {
            Path descriptor = directory.resolve("module-info.java");
            Files.writeString(descriptor, "module sample { requires io.helidon.data.sql.datasource; }");
            options.add(descriptor.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new)), is(0));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString()));
        command.add(modular ? "--module-path" : "--class-path");
        command.add(directory + File.pathSeparator + libraries);
        if (modular) {
            command.add("--module");
            command.add("sample/sample.Main");
        } else {
            command.add("sample.Main");
        }
        Path output = directory.resolve("process.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        Throwable primary = null;
        try {
            assertThat("The linkage process must finish within its bound.", process.waitFor(20, TimeUnit.SECONDS), is(true));
            assertThat(Files.readString(output), process.exitValue(), is(0));
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS), is(true));
            } catch (Exception | Error failure) {
                if (primary == null) {
                    throw failure;
                }
                if (primary != failure) {
                    primary.addSuppressed(failure);
                }
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
