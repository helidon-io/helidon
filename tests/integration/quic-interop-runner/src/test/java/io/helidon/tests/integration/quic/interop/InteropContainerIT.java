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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testcontainers(disabledWithoutDocker = true)
class InteropContainerIT {
    private static final int CONTAINER_PORT = 443;

    @Test
    void shouldServeStaticContentFromInteropImage(@TempDir(cleanup = CleanupMode.ALWAYS) Path workDir) throws Exception {
        Path certsDir = Files.createDirectory(workDir.resolve("certs"));
        Path webRoot = Files.createDirectory(workDir.resolve("www"));
        Path certChain = certsDir.resolve("cert.pem");
        Path privateKey = certsDir.resolve("priv.key");
        Files.writeString(webRoot.resolve("index.html"), "hello from interop image", StandardCharsets.UTF_8);
        TestTlsSupport.exportServerPem(certChain, privateKey);

        ImageFromDockerfile image = new ImageFromDockerfile("helidon-qir-it-" + UUID.randomUUID(), true)
                .withDockerfile(Path.of("./Dockerfile"));
        GenericContainer<?> container = new GenericContainer<>(image)
                .withCopyFileToContainer(MountableFile.forHostPath(certChain), "/certs/cert.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(privateKey), "/certs/priv.key")
                .withCopyFileToContainer(MountableFile.forHostPath(webRoot.resolve("index.html")), "/www/index.html")
                .withEnv("ROLE", "server")
                .withEnv("TESTCASE", "http3")
                .withEnv("HELIDON_QUIC_INTEROP_SKIP_SETUP", "1")
                .waitingFor(Wait.forLogMessage(".*Helidon HTTP/3 interop server listening on.*", 1)
                                    .withStartupTimeout(Duration.ofSeconds(60)));

        try (AutoCloseable _ = () -> DockerClientFactory.instance().client()
                .removeImageCmd(image.getDockerImageName()).exec();
                container) {
            container.start();
            Container.ExecResult probe = container.execInContainer(
                    "java",
                    "-cp",
                    "/opt/helidon/app.jar:/opt/helidon/libs/*",
                    ContainerProbe.class.getName(),
                    "https://localhost:" + CONTAINER_PORT + "/",
                    "/certs/cert.pem");

            assertThat(probe.getStderr(), probe.getExitCode(), is(0));
            assertThat(probe.getStdout(), is("hello from interop image"));
        }
    }
}
