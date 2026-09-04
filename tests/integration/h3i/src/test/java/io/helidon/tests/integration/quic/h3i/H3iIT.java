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

package io.helidon.tests.integration.quic.h3i;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import org.testcontainers.utility.MountableFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testcontainers(disabledWithoutDocker = true)
class H3iIT {
    private static final String SERVER_ALIAS = "helidon-http3";
    private static final int SERVER_PORT = 443;
    private static final List<String> SCENARIOS = List.of("content-length-mismatch",
                                                          "reserved-http2-setting-on-control-stream",
                                                          "uppercase-header-name");

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void shouldPassH3iMalformedPeerSuiteAgainstInteropHarness() throws Exception {
        Path workDir = Files.createTempDirectory("helidon-h3i");
        Path certsDir = Files.createDirectory(workDir.resolve("certs"));
        Path webRoot = Files.createDirectory(workDir.resolve("www"));
        Path certChain = certsDir.resolve("cert.pem");
        Path privateKey = certsDir.resolve("priv.key");
        Path artifactsDir = Files.createDirectories(Path.of("target", "h3i"));
        Path serverOutput = artifactsDir.resolve("server.log");

        Files.writeString(webRoot.resolve("index.html"), "hello from h3i", StandardCharsets.UTF_8);
        TestTlsSupport.exportServerPem(certChain, privateKey);

        ImageFromDockerfile serverImage = new ImageFromDockerfile("helidon-h3i-server-" + UUID.randomUUID(), false)
                .withDockerfile(Path.of("./Dockerfile.server"));
        ImageFromDockerfile toolImage = new ImageFromDockerfile("helidon-h3i-tool-" + UUID.randomUUID(), false)
                .withDockerfile(Path.of("./Dockerfile.h3i"));

        try (Network network = Network.newNetwork();
                GenericContainer<?> server = new GenericContainer<>(serverImage)
                        .withNetwork(network)
                        .withNetworkAliases(SERVER_ALIAS)
                        .withCopyFileToContainer(MountableFile.forHostPath(certChain), "/certs/cert.pem")
                        .withCopyFileToContainer(MountableFile.forHostPath(privateKey), "/certs/priv.key")
                        .withCopyFileToContainer(MountableFile.forHostPath(webRoot.resolve("index.html")), "/www/index.html")
                        .waitingFor(Wait.forLogMessage(".*Helidon HTTP/3 interop server listening on.*", 1));
                GenericContainer<?> tool = new GenericContainer<>(toolImage)
                        .withNetwork(network)) {

            server.start();
            tool.start();

            for (String scenario : SCENARIOS) {
                Container.ExecResult result = tool.execInContainer("h3i-runner",
                                                                   scenario,
                                                                   SERVER_ALIAS,
                                                                   Integer.toString(SERVER_PORT));
                String output = result.getStdout() + result.getStderr();
                Files.writeString(artifactsDir.resolve(scenario + ".txt"), output, StandardCharsets.UTF_8);

                assertThat("h3i scenario '" + scenario + "' failed.\n\nServer logs:\n"
                                   + server.getLogs()
                                   + "\n\nh3i output:\n"
                                   + output,
                           result.getExitCode(),
                           is(0));
            }

            Files.writeString(serverOutput, server.getLogs(), StandardCharsets.UTF_8);
        }
    }
}
