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
package io.helidon.logging.jul;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LogConfigInitializationTest {
    @TempDir
    Path workingDirectory;

    @Test
    void configureRuntimeInitializesLoggingOnce() throws Exception {
        verifyInitialization("configureRuntime");
    }

    @Test
    void initClassInitializesLoggingOnce() throws Exception {
        verifyInitialization("initClass");
    }

    @Test
    void configureRuntimePreservesLoggingAfterClassInitialization() throws Exception {
        verifyInitialization("runtimePhase");
    }

    @Test
    void configureRuntimePreservesPreviousLogFile() throws Exception {
        verifyLogRotation("configureRuntime");
    }

    @Test
    void initClassThenConfigureRuntimePreservesPreviousLogFile() throws Exception {
        verifyLogRotation("initClass");
    }

    private void verifyLogRotation(String entrypoint) throws Exception {
        Files.writeString(workingDirectory.resolve("logging.properties"), """
                handlers=java.util.logging.FileHandler
                .level=INFO
                java.util.logging.FileHandler.pattern=application-%g.log
                java.util.logging.FileHandler.count=2
                java.util.logging.FileHandler.append=false
                """);
        String previousLog = "Previous application run\n";
        Files.writeString(workingDirectory.resolve("application-0.log"), previousLog);

        verifyInitialization(entrypoint);

        assertThat("Startup preserves the previous application log",
                   Files.readString(workingDirectory.resolve("application-1.log")), is(previousLog));
        assertThat("Application logging remains active",
                   Files.readString(workingDirectory.resolve("application-0.log")), containsString("Application log record"));
    }

    private void verifyInitialization(String entrypoint) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = workingDirectory.resolve("process.log");
        Process process = new ProcessBuilder(java, "-cp", classpath, InitializationMain.class.getName(), entrypoint)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        try {
            assertThat("Logging initialization process completed", process.waitFor(30, TimeUnit.SECONDS), is(true));
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            assertThat(output, process.exitValue(), is(0));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    public static class InitializationMain {
        public static void main(String[] args) {
            if (args[0].equals("runtimePhase")) {
                verifyRuntimePhase();
                return;
            }
            Logger root = Logger.getLogger("");
            root.setLevel(Level.OFF);
            if (args[0].equals("configureRuntime")) {
                LogConfig.configureRuntime();
            } else {
                LogConfig.initClass();
            }
            assertThat("Classpath logging.properties applied", root.getLevel(), is(Level.INFO));

            root.setLevel(Level.FINEST);
            LogConfig.configureRuntime();
            LogConfig.initClass();
            assertThat("Repeated initialization preserves application logging changes", root.getLevel(), is(Level.FINEST));
            root.info("Application log record");
        }

        private static void verifyRuntimePhase() {
            Logger root = Logger.getLogger("");
            root.setLevel(Level.OFF);
            LogConfig.initClass();
            assertThat("Class initialization applies logging.properties", root.getLevel(), is(Level.INFO));

            root.setLevel(Level.FINEST);
            LogConfig.initClass();
            assertThat("Repeated class initialization preserves application logging changes", root.getLevel(), is(Level.FINEST));

            LogConfig.configureRuntime();
            assertThat("Runtime configuration preserves application logging changes", root.getLevel(), is(Level.FINEST));

            root.setLevel(Level.FINEST);
            LogConfig.configureRuntime();
            LogConfig.initClass();
            assertThat("Repeated runtime configuration preserves application logging changes", root.getLevel(), is(Level.FINEST));
        }
    }
}
