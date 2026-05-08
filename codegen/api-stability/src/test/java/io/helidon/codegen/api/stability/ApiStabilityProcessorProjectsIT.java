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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests that verify the projects under {@code src/it/projects}.
 */
public class ApiStabilityProcessorProjectsIT {
    @Test
    @DisplayName("Default action fails for internal and incubating API usage")
    void defaultBuildFails() throws IOException {
        String buildLog = buildLog("default");

        assertThat(buildLog, containsString("BUILD FAILURE"));
        assertThat(buildLog, containsString("Usage of Helidon APIs annotated with @Api.Internal"));
        assertThat(buildLog, containsString("Usage of Helidon APIs annotated with @Api.Incubating"));
        assertThat(buildLog, not(containsString("no more tokens - could not parse error message")));
    }

    @Test
    @DisplayName("Warn action reports all API stability diagnostics as warnings")
    void warnBuildSucceeds() throws IOException {
        String buildLog = buildLog("warn");

        assertThat(buildLog, containsString("BUILD SUCCESS"));
        assertThat(buildLog, containsString("Usage of Helidon APIs annotated with @Api.Internal"));
        assertThat(buildLog, containsString("Usage of Helidon APIs annotated with @Api.Incubating"));
        assertThat(buildLog, containsString("Usage of Helidon APIs annotated with @Api.Preview"));
        assertThat(buildLog, containsString("Usage of Helidon APIs annotated with @Deprecated"));
        assertThat(buildLog, not(containsString("no more tokens - could not parse error message")));
    }

    @Test
    @DisplayName("SuppressWarnings support suppresses all API stability diagnostics")
    void suppressedBuildSucceeds() throws IOException {
        String buildLog = buildLog("suppressed");

        assertThat(buildLog, containsString("BUILD SUCCESS"));
        assertThat(buildLog, not(containsString("Usage of Helidon APIs annotated with @Api.")));
    }

    private String buildLog(String projectName) throws IOException {
        return Files.readString(projectPath(projectName).resolve("build.log"), StandardCharsets.UTF_8);
    }

    private Path projectPath(String projectName) {
        try {
            var testClasses = Paths.get(getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return testClasses.resolve("../it/projects").resolve(projectName).normalize();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve invoker project path for " + projectName, e);
        }
    }
}
