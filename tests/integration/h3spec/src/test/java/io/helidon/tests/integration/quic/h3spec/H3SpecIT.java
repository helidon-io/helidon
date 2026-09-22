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

package io.helidon.tests.integration.quic.h3spec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@Testcontainers(disabledWithoutDocker = true)
class H3SpecIT {
    private static final Pattern FAILURE_PATTERN =
            Pattern.compile(" {2}(MUST.+) (\\[[^]]+]) (?:\\[\\u2718\\]|FAILED.*)");
    private static final List<String> EXCLUDED_SPECS = List.of();
    private static final String SERVER_ALIAS = "helidon-http3";
    private static final int SERVER_PORT = 443;

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void shouldPassH3SpecAgainstInteropHarness(@TempDir(cleanup = CleanupMode.ALWAYS) Path workDir) throws Exception {
        Path certsDir = Files.createDirectory(workDir.resolve("certs"));
        Path webRoot = Files.createDirectory(workDir.resolve("www"));
        Path certChain = certsDir.resolve("cert.pem");
        Path privateKey = certsDir.resolve("priv.key");
        Path h3specOutput = Path.of("target", "h3spec-output.txt");
        Path serverOutput = Path.of("target", "h3spec-server.log");

        Files.writeString(webRoot.resolve("index.html"), "hello from h3spec", StandardCharsets.UTF_8);
        TestTlsSupport.exportServerPem(certChain, privateKey);

        ImageFromDockerfile serverImage = new ImageFromDockerfile("helidon-h3spec-server-" + UUID.randomUUID(), true)
                .withDockerfile(Path.of("./Dockerfile.server"));
        ImageFromDockerfile toolImage = new ImageFromDockerfile("helidon-h3spec-tool-" + UUID.randomUUID(), true)
                .withDockerfile(Path.of("./Dockerfile.h3spec"));

        try (AutoCloseable _ = () -> DockerClientFactory.instance().client()
                .removeImageCmd(serverImage.getDockerImageName()).exec();
                AutoCloseable _ = () -> DockerClientFactory.instance().client()
                        .removeImageCmd(toolImage.getDockerImageName()).exec();
                Network network = Network.newNetwork();
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

            Container.ExecResult result = tool.execInContainer(h3specCommand());
            String output = result.getStdout() + result.getStderr();
            String serverLogs = server.getLogs();
            List<Failure> failures = parseFailures(output);

            Files.writeString(h3specOutput, output, StandardCharsets.UTF_8);
            Files.writeString(serverOutput, serverLogs, StandardCharsets.UTF_8);

            assertThat("Unexpected h3spec exit code.\n\nh3spec output:\n" + output,
                       result.getExitCode(),
                       anyOf(is(0), is(1)));
            assertThat("h3spec did not print its completion summary.\n\nh3spec output:\n" + output,
                       output.contains("Finished in"),
                       is(true));
            assertThat("h3spec exited with failures, but no failure lines matched the parser.\n\nh3spec output:\n" + output,
                       result.getExitCode() == 0 || !failures.isEmpty(),
                       is(true));
            assertThat("h3spec reported RFC failures.\n\nFailures:\n"
                               + formatFailures(failures)
                               + "\nServer logs:\n"
                               + serverLogs
                               + "\n\nh3spec output:\n"
                               + output,
                       failures,
                       empty());
        }
    }

    private static String[] h3specCommand() {
        List<String> command = new ArrayList<>();
        command.add("h3spec");
        command.add(SERVER_ALIAS);
        command.add(Integer.toString(SERVER_PORT));
        command.add("-n");
        command.add("--timeout");
        command.add("5000");
        command.add("--debug");
        for (String excludedSpec : EXCLUDED_SPECS) {
            command.add("--skip");
            command.add(excludedSpec);
        }
        return command.toArray(String[]::new);
    }

    private static List<Failure> parseFailures(String output) {
        List<Failure> failures = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Matcher matcher = FAILURE_PATTERN.matcher(line);
            if (matcher.matches()) {
                failures.add(new Failure(matcher.group(1), matcher.group(2)));
            }
        }
        return failures;
    }

    private static String formatFailures(List<Failure> failures) {
        if (failures.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (Failure failure : failures) {
            builder.append("- ")
                    .append(failure.name())
                    .append(' ')
                    .append(failure.rfcSection())
                    .append('\n');
        }
        return builder.toString();
    }

    private record Failure(String name, String rfcSection) {
    }
}
