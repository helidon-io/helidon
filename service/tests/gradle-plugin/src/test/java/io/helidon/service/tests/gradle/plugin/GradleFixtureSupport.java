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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.gradle.testkit.runner.GradleRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

final class GradleFixtureSupport {
    private static final String GRADLE_VERSION = "9.8.1";

    private GradleFixtureSupport() {
    }

    static GradleRunner runner(Path directory, String... tasks) {
        var arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of("--stacktrace", "--offline", "--console=plain", "--parallel"));
        var runner = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withTestKitDir(directory.resolve("test-kit").toFile())
                .withArguments(arguments);
        String installation = System.getProperty("gradle.home");
        return installation == null ? runner.withGradleVersion(GRADLE_VERSION)
                : runner.withGradleInstallation(new File(installation));
    }

    static void stopDaemons(Path directory) throws Exception {
        Path registry = directory.resolve("test-kit/test-kit-daemon");
        if (!Files.isDirectory(registry)) {
            return;
        }
        Path installation = Path.of(Files.readString(directory.resolve("gradle-installation.txt")).trim());
        Path launcher;
        try (var libraries = Files.list(installation.resolve("lib"))) {
            launcher = libraries.filter(path -> path.getFileName().toString().startsWith("gradle-gradle-cli-main-")
                            && path.toString().endsWith(".jar"))
                    .findFirst().orElseThrow();
        }
        List<Long> daemonPids;
        try (var logs = Files.walk(registry)) {
            daemonPids = logs.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("daemon-[0-9]+\\.out\\.log"))
                    .map(name -> Long.parseLong(name.substring(7, name.length() - 8)))
                    .toList();
        }
        Path output = directory.resolve("daemon-stop.log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                                         "-Dorg.gradle.daemon.registry.base=" + registry,
                                         "-jar", launcher.toString(),
                                         "--stop", "--gradle-user-home", directory.resolve("test-kit").toString())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            throw new AssertionError("Timed out stopping fixture Gradle daemons: " + Files.readString(output));
        }
        assertThat(Files.readString(output), process.exitValue(), is(0));
        for (long pid : daemonPids) {
            var daemon = ProcessHandle.of(pid);
            if (daemon.isPresent()) {
                daemon.get().onExit().get(30, TimeUnit.SECONDS);
            }
        }
    }
}
