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

package io.helidon.archetypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TracingProviderIT {
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({"se,jaeger", "se,zipkin", "mp,jaeger", "mp,zipkin"})
    void rejectsDeprecatedProvider(String flavor, String provider) throws IOException, InterruptedException {
        GenerationResult result = generate(flavor, "-Dtracing.provider=" + provider);

        assertAll(() -> assertThat(result.output(), result.exitCode(), is(1)),
                  () -> assertThat(result.output(), containsString("Invalid input: tracing.provider='" + provider + "'")),
                  () -> assertThat("Rejected input must not generate a project", Files.exists(result.project()), is(false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"se", "mp"})
    void defaultsToOpenTelemetry(String flavor) throws IOException, InterruptedException {
        assertOpenTelemetryProject(flavor, generate(flavor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"se", "mp"})
    void acceptsOpenTelemetry(String flavor) throws IOException, InterruptedException {
        assertOpenTelemetryProject(flavor, generate(flavor, "-Dtracing.provider=opentelemetry"));
    }

    private void assertOpenTelemetryProject(String flavor, GenerationResult result) throws IOException {
        assertThat(result.output(), result.exitCode(), is(0));
        String pom = Files.readString(result.project().resolve("pom.xml"));
        String dependency = flavor.equals("se") ? "helidon-tracing-providers-opentelemetry" : "opentelemetry-exporter-otlp";
        assertAll(() -> assertThat(pom, containsString(dependency)),
                  () -> assertThat(pom, not(containsString("helidon-tracing-providers-jaeger"))),
                  () -> assertThat(pom, not(containsString("zipkin"))));
    }

    private GenerationResult generate(String flavor, String... options) throws IOException, InterruptedException {
        Path project = directory.resolve("project");
        Path home = Files.createDirectory(directory.resolve("home"));
        Path log = directory.resolve("cli.log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path cliData = Path.of(System.getProperty("helidon.archetype.cli-data"));
        List<String> command = new ArrayList<>(List.of(java.toString(),
                                                      "-Duser.home=" + home,
                                                      "-jar", System.getProperty("helidon.cli.jar"),
                                                      "init", "--batch", "--error", "--reset",
                                                      "--url", cliData.toUri().toString(),
                                                      "--project", project.toString(),
                                                      "-Dflavor=" + flavor,
                                                      "-Dapp-type=custom",
                                                      "-Dtracing=true",
                                                      "-Dmedia=none",
                                                      "-Dmetrics=false",
                                                      "-Dhealth=false",
                                                      "-Dsecurity=false",
                                                      "-Ddb=false",
                                                      "-Dextra=none",
                                                      "-Ddocker=false",
                                                      "-Dk8s=false",
                                                      "-Djpms=false",
                                                      "-DgroupId=com.example",
                                                      "-DartifactId=tracing-test",
                                                      "-Dversion=1.0.0-SNAPSHOT",
                                                      "-Dpackage=com.example.tracing"));
        command.addAll(List.of(options));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            process.getOutputStream().close();
            boolean exited = process.waitFor(30, TimeUnit.SECONDS);
            assertThat("CLI did not exit within 30 seconds:\n" + Files.readString(log), exited, is(true));
            return new GenerationResult(project, process.exitValue(), Files.readString(log));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    private record GenerationResult(Path project, int exitCode, String output) {
    }
}
