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
package io.helidon.data.jdbc.health;

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

class JdbcHealthLinkageTest {
    @TempDir
    Path temporary;

    @Test
    void coreLinksWithoutHealthOnClasspathAndModulePath() throws Exception {
        link(false, false);
        link(true, false);
    }

    @Test
    void adapterLinksOnClasspathAndModulePath() throws Exception {
        link(false, true);
        link(true, true);
    }

    /**
     * Keeps dependency selection and health exclusions independent of path separators and checkout names.
     */
    @Test
    void librariesUsePathComponentsForUnixAndWindows() {
        String root = "C:/work/helidon-health-check/helidon";
        String core = root + "/data/jdbc/jdbc/target/classes";
        String dependency = root + "/.m2/repository/io/helidon/common/helidon-common/28/helidon-common-28.jar";
        String adapterClasses = root + "/data/jdbc/health/target/classes";
        String healthClasses = root + "/health/target/classes";
        String adapterJar = root + "/.m2/repository/io/helidon/data/jdbc/helidon-data-jdbc-health/28/"
                + "helidon-data-jdbc-health-28.jar";
        String healthJar = root + "/.m2/repository/io/helidon/health/helidon-health/28/helidon-health-28.jar";
        List<String> entries = List.of(core, dependency, adapterClasses, healthClasses, adapterJar, healthJar,
                                      root + "/data/jdbc/health/target/test-classes",
                                      root + "/.m2/repository/org/example/example/28/example-28.jar");
        for (List<String> paths : List.of(entries, entries.stream().map(value -> value.replace('/', '\\')).toList())) {
            assertThat(libraries(paths, false), is(List.of(Path.of(core), Path.of(dependency))));
            assertThat(libraries(paths, true), is(List.of(Path.of(core), Path.of(dependency), Path.of(adapterClasses),
                                                        Path.of(healthClasses), Path.of(adapterJar), Path.of(healthJar))));
        }
    }

    private static List<Path> libraries(List<String> entries, boolean adapter) {
        return entries.stream()
                .map(value -> Path.of(value.replace('\\', '/')).normalize())
                .filter(path -> !path.endsWith(Path.of("target", "test-classes")))
                .filter(path -> path.getFileName().toString().startsWith("helidon-")
                        || path.endsWith(Path.of("target", "classes")))
                .filter(path -> adapter || (!path.endsWith(Path.of("health", "target", "classes"))
                        && !path.getFileName().toString().startsWith("helidon-health-")
                        && !path.getFileName().toString().startsWith("helidon-data-jdbc-health-")))
                .toList();
    }

    private void link(boolean modular, boolean adapter) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path");
        String libraries = libraries(Arrays.asList(classPath.split(File.pathSeparator)), adapter).stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        Path directory = Files.createDirectory(temporary.resolve((modular ? "module" : "class") + adapter));
        Path source = directory.resolve("sample/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package sample;
                import java.time.Duration;
                import io.helidon.data.jdbc.JdbcHealthSupport;
                """ + (adapter ? "import io.helidon.data.jdbc.health.JdbcClientHealthCheck;\n" : "") + """
                public class Main {
                    public static void main(String[] arguments) {
                        try {
                            JdbcHealthSupport.createProbe(null, "SELECT 1", Duration.ofSeconds(2));
                            throw new AssertionError();
                        } catch (NullPointerException _) {
                        }
                """ + (adapter ? "JdbcClientHealthCheck.builder();\n" : "") + "} }\n");
        List<String> options = new ArrayList<>(List.of("--release", "27", "-proc:none", "-d", directory.toString(),
                modular ? "--module-path" : "--class-path", libraries, source.toString()));
        if (modular) {
            Path descriptor = directory.resolve("module-info.java");
            Files.writeString(descriptor, "module sample { requires io.helidon.data.jdbc"
                    + (adapter ? ".health" : "") + "; }");
            options.add(descriptor.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new)), is(0));
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
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
        try {
            assertThat("The linkage process must finish within its bound.", process.waitFor(20, TimeUnit.SECONDS), is(true));
            assertThat(Files.readString(output), process.exitValue(), is(0));
        } finally {
            process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS), is(true));
        }
    }
}
