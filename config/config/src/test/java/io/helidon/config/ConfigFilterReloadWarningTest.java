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

package io.helidon.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ConfigFilterReloadWarningTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"unused", "factories", "manual", "polling", "watching", "events", "overrides",
            "provider", "concurrent"})
    void warningAndLegacyBehavior(String scenario) throws Exception {
        Path output = directory.resolve(scenario + ".log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(java.toString(), "-cp", classpath,
                                             ConfigFilterReloadWarningScenario.class.getName(), scenario, directory.toString())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            assertThat("Scenario " + scenario + " timed out. Output:\n" + Files.readString(output), completed, is(true));
            assertThat("Scenario " + scenario + " failed. Output:\n" + Files.readString(output), process.exitValue(), is(0));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat("Child JVM must terminate", process.waitFor(10, TimeUnit.SECONDS), is(true));
            }
        }
    }
}
