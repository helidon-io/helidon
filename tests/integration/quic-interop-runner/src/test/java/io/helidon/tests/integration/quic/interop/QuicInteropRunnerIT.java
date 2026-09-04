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

package io.helidon.tests.integration.quic.interop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@Testcontainers(disabledWithoutDocker = true)
class QuicInteropRunnerIT {
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String SERVER_IMAGE = "helidon-http3:test";
    private static final String CLIENT_IMPLEMENTATION = "quic-go";
    private static final String CLIENT_IMAGE = "martenseemann/quic-go-interop"
            + "@sha256:93123a8a0315ede4e35feb85a296337e674846cb853a2f1b320d75b9ef617519";
    private static final String SIMULATOR_IMAGE = "martenseemann/quic-network-simulator"
            + "@sha256:c23d82a55caffe681b1bdae65d4d30d23e1283141a414a7f02ee56cf15f9c6b9";
    private static final String RESULTS_ROOT = "/tmp/helidon-quic-interop-runner";
    private static final String RESULTS_JSON = RESULTS_ROOT + "/results.json";
    private static final String LOG_DIR = RESULTS_ROOT + "/logs";
    private static final String LOG_SUBDIR = LOG_DIR + "/helidon-http3_" + CLIENT_IMPLEMENTATION + "/http3";
    private static final String IMPLEMENTATIONS_JSON = "/opt/quic-interop-runner/implementations_quic.json";
    private static final String DOCKER_COMPOSE_YML = "/opt/quic-interop-runner/docker-compose.yml";
    private static final String TESTCASE_PY = "/opt/quic-interop-runner/testcase.py";
    private static final String TOOL_IMAGE_SBOM = "/usr/share/helidon/sbom/quic-interop-runner-tool.spdx.json";
    private static final String TOOL_IMAGE_PACKAGES =
            "/usr/share/helidon/sbom/quic-interop-runner-tool.packages.json";

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void shouldPassHttp3SmokeTestThroughQuicInteropRunner() throws Exception {
        Path artifactsDir = Files.createDirectories(Path.of("target", "quic-interop-runner"));
        ImageFromDockerfile toolImage = new ImageFromDockerfile("helidon-quic-interop-tool-" + UUID.randomUUID(), false)
                .withDockerfile(Path.of("./Dockerfile.runner-tool"));
        Network network = Network.newNetwork();
        GenericContainer<?> tool = new GenericContainer<>(toolImage)
                .withPrivilegedMode(true)
                .withNetwork(network)
                .waitingFor(Wait.forLogMessage(".*API listen on /var/run/docker.sock.*", 1)
                                    .withStartupTimeout(STARTUP_TIMEOUT));
        String registryMirror = System.getenv("HELIDON_QUIC_INTEROP_REGISTRY_MIRROR");
        if (registryMirror != null && !registryMirror.isBlank()) {
            tool.withCommand("dockerd",
                             "--host=unix:///var/run/docker.sock",
                             "--registry-mirror=" + registryMirror);
        }

        try (network; tool) {
            tool.start();
            waitForDocker(tool);
            verifyNestedDockerEndpoint(tool);

            Container.ExecResult composeVersion = tool.execInContainer("sh", "-c", "docker compose version");
            String composeVersionOutput = execOutput(composeVersion);
            Files.writeString(artifactsDir.resolve("docker-compose-version.txt"),
                              composeVersionOutput,
                              StandardCharsets.UTF_8);
            assertThat("Runner tool image does not provide docker compose.\n\n" + composeVersionOutput,
                       composeVersion.getExitCode(),
                       is(0));

            String toolImageSbom = copyTextFileIfPresent(tool,
                                                         TOOL_IMAGE_SBOM,
                                                         artifactsDir.resolve("runner-tool.spdx.json"));
            assertThat("Runner tool image does not retain its SPDX SBOM.", toolImageSbom.isBlank(), is(false));
            assertThat(toolImageSbom, containsString("\"spdxVersion\": \"SPDX-2.3\""));
            assertThat(toolImageSbom, containsString("\"name\": \"pycryptodome\""));

            String toolImagePackages = copyTextFileIfPresent(tool,
                                                             TOOL_IMAGE_PACKAGES,
                                                             artifactsDir.resolve("runner-tool.packages.json"));
            String expectedPackages = Files.readString(Path.of("sbom.runner-tool.lock.json"), StandardCharsets.UTF_8);
            assertThat("Runner tool image package set differs from its checked-in semantic SBOM baseline.",
                       toolImagePackages.replace("\r\n", "\n"),
                       is(expectedPackages.replace("\r\n", "\n")));

            String implementationsJson = copyTextFileIfPresent(tool,
                                                               IMPLEMENTATIONS_JSON,
                                                               artifactsDir.resolve("implementations_quic.json"));
            assertThat("The upstream runner implementation registry does not contain the Helidon server entry.\n\n"
                               + implementationsJson,
                       implementationsJson,
                       containsString("\"helidon-http3\""));
            assertThat(implementationsJson, containsString("\"image\": \"" + CLIENT_IMAGE + "\""));

            String compose = copyTextFileIfPresent(tool,
                                                   DOCKER_COMPOSE_YML,
                                                   artifactsDir.resolve("docker-compose.yml"));
            assertThat(compose, containsString("image: " + SIMULATOR_IMAGE));

            String testcase = copyTextFileIfPresent(tool,
                                                    TESTCASE_PY,
                                                    artifactsDir.resolve("testcase.py"));
            assertThat(testcase, containsString("Remove root-owned files directly"));
            assertThat(testcase, not(containsString("alpine:3.18")));

            Container.ExecResult buildResult = tool.execInContainer("sh",
                                                                    "-c",
                                                                    "docker build -t " + SERVER_IMAGE + " /workspace");
            String buildOutput = execOutput(buildResult);
            Files.writeString(artifactsDir.resolve("docker-build.txt"), buildOutput, StandardCharsets.UTF_8);
            assertThat("Failed to build the Helidon QUIC interop server image inside the runner tool container.\n\n"
                               + buildOutput,
                       buildResult.getExitCode(),
                       is(0));

            Container.ExecResult runnerResult = tool.execInContainer("sh",
                                                                     "-c",
                                                                     "mkdir -p " + RESULTS_ROOT
                                                                             + " && python3 run.py"
                                                                             + " -d"
                                                                             + " -s helidon-http3"
                                                                             + " -c " + CLIENT_IMPLEMENTATION
                                                                             + " -t http3"
                                                                             + " -r helidon-http3=" + SERVER_IMAGE
                                                                             + " -l " + LOG_DIR
                                                                             + " -j " + RESULTS_JSON);
            String runnerOutput = execOutput(runnerResult);
            Files.writeString(artifactsDir.resolve("runner-output.txt"), runnerOutput, StandardCharsets.UTF_8);
            Container.ExecResult resultsListing = tool.execInContainer("sh",
                                                                       "-c",
                                                                       "if [ -d '" + RESULTS_ROOT + "' ]; then "
                                                                               + "find '"
                                                                               + RESULTS_ROOT
                                                                               + "' -maxdepth 5 -type f | sort; "
                                                                               + "fi");
            Files.writeString(artifactsDir.resolve("results-root.txt"),
                              execOutput(resultsListing),
                              StandardCharsets.UTF_8);

            String resultsJson = copyTextFileIfPresent(tool, RESULTS_JSON, artifactsDir.resolve("results.json"));
            String interopOutput = copyTextFileIfPresent(tool,
                                                         LOG_SUBDIR + "/output.txt",
                                                         artifactsDir.resolve("interop-output.txt"));
            String serverLog = copyTextFileIfPresent(tool,
                                                     LOG_SUBDIR + "/server/helidon-http3.log",
                                                     artifactsDir.resolve("server.log"));

            assertThat("quic-interop-runner reported a failure.\n\n"
                               + runnerOutput
                               + "\n\nBuild output:\n"
                               + buildOutput,
                       runnerResult.getExitCode(),
                       is(0));
            assertThat("quic-interop-runner did not execute the expected http3 smoke testcase.\n\n" + runnerOutput,
                       runnerOutput,
                       containsString("Server: helidon-http3. Client: " + CLIENT_IMPLEMENTATION + ". Running test case: http3"));
            assertThat("quic-interop-runner did not export a results matrix.\n\n" + runnerOutput,
                       resultsJson.isBlank(),
                       is(false));
            assertThat(resultsJson, containsString("\"name\": \"http3\""));
            assertThat(resultsJson, containsString("\"result\": \"succeeded\""));
            assertThat("The exported runner output log did not include the Helidon server startup trace.\n\n"
                               + interopOutput
                               + "\n\nRunner output:\n"
                               + runnerOutput,
                       interopOutput,
                       containsString("Starting Helidon HTTP/3 interop server."));
        }
    }

    private static void verifyNestedDockerEndpoint(GenericContainer<?> tool) throws Exception {
        Container.ExecResult socket = tool.execInContainer("test", "-S", "/var/run/docker.sock");
        assertThat("Nested Docker must listen on the expected Unix socket.\n\n" + execOutput(socket),
                   socket.getExitCode(),
                   is(0));

        Container.ExecResult tcpApi = tool.execInContainer(
                "python3",
                "-c",
                "import socket, sys\n"
                        + "for port in (2375, 2376):\n"
                        + "    probe = socket.socket()\n"
                        + "    probe.settimeout(1)\n"
                        + "    if probe.connect_ex(('127.0.0.1', port)) == 0:\n"
                        + "        sys.exit(port)\n"
                        + "sys.exit(0)\n");
        assertThat("Nested Docker must not expose a TCP API.\n\n" + execOutput(tcpApi),
                   tcpApi.getExitCode(),
                   is(0));

        Container.ExecResult uid = tool.execInContainer("id", "-u");
        assertThat("Direct cleanup of root-owned runner artifacts requires the dedicated tool to run as root.\n\n"
                           + execOutput(uid),
                   uid.getExitCode(),
                   is(0));
        assertThat(execOutput(uid).trim(), is("0"));
    }

    private static String execOutput(Container.ExecResult result) {
        return result.getStdout() + result.getStderr();
    }

    private static void waitForDocker(GenericContainer<?> tool) throws Exception {
        Exception failure = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                Container.ExecResult result = tool.execInContainer("docker",
                                                                   "--host=unix:///var/run/docker.sock",
                                                                   "info");
                if (result.getExitCode() == 0) {
                    return;
                }
            } catch (Exception e) {
                failure = e;
            }
            Thread.sleep(1000);
        }

        if (failure != null) {
            throw failure;
        }

        throw new IllegalStateException("Nested Docker daemon in the interop runner tool container did not become ready.");
    }

    private static String copyTextFileIfPresent(GenericContainer<?> container,
                                                String sourcePath,
                                                Path targetPath) throws Exception {
        Files.createDirectories(targetPath.getParent());
        try {
            return container.copyFileFromContainer(sourcePath, input -> {
                byte[] bytes = input.readAllBytes();
                Files.write(targetPath, bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            });
        } catch (Exception ignored) {
            Container.ExecResult fallback = container.execInContainer("sh",
                                                                      "-c",
                                                                      "if [ -f '" + sourcePath + "' ]; then "
                                                                              + "cat '" + sourcePath + "'; "
                                                                              + "fi");
            if (fallback.getExitCode() != 0) {
                return "";
            }

            String output = execOutput(fallback);
            if (!output.isEmpty()) {
                Files.writeString(targetPath, output, StandardCharsets.UTF_8);
            }
            return output;
        }
    }
}
