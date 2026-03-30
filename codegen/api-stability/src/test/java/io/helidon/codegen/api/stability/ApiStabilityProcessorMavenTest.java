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

package io.helidon.codegen.api.stability;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class ApiStabilityProcessorMavenTest {
    @Test
    void testConsumerBuildFailsByDefault() throws IOException, InterruptedException {
        var result = runConsumerBuild("default", "**/UsesApis.java");

        assertThat(result.exitCode(), not(is(0)));
        assertThat(result.output(), hasItem(containsString("BUILD FAILURE")));
        assertThat(result.output(), hasItem(containsString("Usage of Helidon APIs annotated with @Api.Internal")));
        assertThat(result.output(), hasItem(containsString("Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(result.output(), not(hasItem(containsString("no more tokens - could not parse error message"))));
    }

    @Test
    void testConsumerBuildWarnsWhenConfigured() throws IOException, InterruptedException {
        var result = runConsumerBuild("warn", "**/UsesApis.java");

        assertThat(result.exitCode(), is(0));
        assertThat(result.output(), hasItem(containsString("BUILD SUCCESS")));
        assertThat(result.output(), hasItem(containsString("Usage of Helidon APIs annotated with @Api.Internal")));
        assertThat(result.output(), hasItem(containsString("Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(result.output(), hasItem(containsString("Usage of Helidon APIs annotated with @Api.Preview")));
        assertThat(result.output(), hasItem(containsString("Usage of Helidon APIs annotated with @Api.Deprecated")));
        assertThat(result.output(), not(hasItem(containsString("no more tokens - could not parse error message"))));
    }

    @Test
    void testConsumerBuildSupportsSuppressWarnings() throws IOException, InterruptedException {
        var result = runConsumerBuild("default", "**/SuppressedUsesApis.java");

        assertThat(result.exitCode(), is(0));
        assertThat(result.output(), hasItem(containsString("BUILD SUCCESS")));
        assertThat(result.output(), not(hasItem(containsString("Usage of Helidon APIs annotated with @Api."))));
    }

    private static BuildResult runConsumerBuild(String action,
                                                String sourceIncludes) throws IOException, InterruptedException {
        Path root = repositoryRoot();
        deleteDirectory(root.resolve("codegen/tests/test-api-stability-consumer/target"));
        Path logFile = Files.createTempFile("api-stability-consumer-build", ".log");

        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("-B");
        command.add("-ntp");
        command.add("-Dstyle.color=never");
        command.add("-Ptests");
        command.add("-pl");
        command.add("codegen/tests/test-api-stability-consumer");
        command.add("-am");
        command.add("-DskipTests");
        command.add("-Dapi.stability.action=" + action);
        command.add("-Dapi.stability.source.includes=" + sourceIncludes);
        command.add("compile");

        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out waiting for Maven build. Log file: " + logFile);
        }

        var output = Files.readString(logFile, StandardCharsets.UTF_8)
                .lines()
                .toList();
        return new BuildResult(process.exitValue(), output);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath().normalize();
        while (path != null) {
            if (Files.exists(path.resolve("pom.xml")) && Files.exists(path.resolve("codegen/tests/pom.xml"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot determine repository root from " + Path.of("").toAbsolutePath());
    }

    private record BuildResult(int exitCode,
                               List<String> output) {
    }
}
