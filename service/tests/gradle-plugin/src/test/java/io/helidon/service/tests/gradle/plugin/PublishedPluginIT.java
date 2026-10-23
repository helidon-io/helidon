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

package io.helidon.service.tests.gradle.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class PublishedPluginIT {
    private Path directory;

    @BeforeEach
    void copyPublishedPluginFixture() throws Exception {
        Path fixtures = Files.createDirectories(Path.of(System.getProperty("fixture.directory")).toAbsolutePath());
        directory = Files.createTempDirectory(fixtures, "published-plugin-");
        Path source = Path.of(PublishedPluginIT.class.getResource("/gradle/published-plugin").toURI());
        String version = System.getProperty("fixture.version");
        String repository = Path.of(System.getProperty("fixture.repository")).toAbsolutePath().toUri().toASCIIString()
                .replace("'", "%27");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = directory.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    String contents = Files.readString(path)
                            .replace("@PROJECT_VERSION@", version)
                            .replace("@REPOSITORY_URI@", repository);
                    Files.writeString(destination, contents);
                }
            }
        }
    }

    @AfterEach
    void stopFixtureDaemons() throws Exception {
        if (directory != null) {
            GradleFixtureSupport.stopDaemons(directory);
        }
    }

    @Test
    void resolvesPublishedPluginAndDefaultGenerator() throws IOException {
        var result = GradleFixtureSupport.runner(directory, "publishedRun").build();
        assertThat(result.getOutput(), result.task(":generateApplicationBindings"), notNullValue());
        assertThat(result.getOutput(), result.task(":generateApplicationBindings").getOutcome(), is(TaskOutcome.SUCCESS));
        assertThat(result.getOutput(), containsString("PUBLISHED:singleton"));
        String binding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString(".bind("));
        assertThat(binding, containsString("Application__ServiceDescriptor"));
        assertThat("Generated binding must be compiled into the application output",
                   Files.exists(directory.resolve("build/classes/helidon/main/example/ApplicationBinding.class")), is(true));
    }
}
