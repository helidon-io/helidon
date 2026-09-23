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

package io.helidon.tests.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Portable parent/child/grandchild process used to validate controlled-build cleanup.
 */
public final class Http3QuicEvidenceProcessFixture {
    private Http3QuicEvidenceProcessFixture() {
    }

    /**
     * Runs one process-tree fixture role.
     *
     * @param arguments PID directory followed by fixture role
     * @throws Exception if process setup fails
     */
    public static void main(String[] arguments) throws Exception {
        Path pidDirectory = Path.of(arguments[0]);
        String role = arguments[1];
        Files.createDirectories(pidDirectory);
        Files.writeString(pidDirectory.resolve(role + ".pid"),
                          Long.toString(ProcessHandle.current().pid()));
        switch (role) {
        case "root" -> {
            new ProcessBuilder(command(pidDirectory, "child"))
                    .inheritIO()
                    .start();
            new CountDownLatch(1).await();
        }
        case "child" -> {
            new ProcessBuilder(command(pidDirectory, "grandchild"))
                    .inheritIO()
                    .start();
            new CountDownLatch(1).await();
        }
        case "grandchild" -> new CountDownLatch(1).await();
        case "orchestrator" -> {
            Thread.ofPlatform()
                    .daemon()
                    .name("http3-quic-evidence-fixture-shutdown")
                    .start(() -> {
                        Path exitRequest = pidDirectory.resolve("exit.request");
                        try {
                            while (!Files.exists(exitRequest)) {
                                Thread.sleep(10);
                            }
                            System.exit(130);
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                        }
                    });
            Http3QuicEvidenceManifestTest.runMaven(
                    pidDirectory,
                    command(pidDirectory, "root"),
                    "controlled-build shutdown fixture",
                    60,
                    1);
        }
        default -> throw new IllegalArgumentException("Unknown process-tree fixture role: " + role);
        }
    }

    static List<String> command(Path pidDirectory, String role) {
        String javaExecutable = System.getProperty("os.name").startsWith("Windows")
                ? "java.exe"
                : "java";
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Http3QuicEvidenceProcessFixture.class.getName());
        command.add(pidDirectory.toString());
        command.add(role);
        return command;
    }
}
