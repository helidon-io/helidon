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

package io.helidon.tests.integration.h2spec.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.images.builder.Transferable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class H2SpecSectionContainerTest {
    @TempDir
    Path directory;

    @Test
    void timeoutDuringStartupCopyCapturesDiagnosticsBeforeRemovingCreatedContainer() throws Exception {
        DockerClient docker = mock(DockerClient.class);
        CopyArchiveToContainerCmd copy = mock(CopyArchiveToContainerCmd.class);
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        InspectContainerCmd inspect = mock(InspectContainerCmd.class);
        InspectContainerResponse state = new InspectContainerResponse();
        CountDownLatch copying = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        CountDownLatch copyExited = new CountDownLatch(1);
        AtomicBoolean containerRemoved = new AtomicBoolean();
        AtomicBoolean diagnosedBeforeRemoval = new AtomicBoolean();
        AtomicBoolean copyHeldUntilRemoval = new AtomicBoolean();
        AtomicReference<InputStream> archive = new AtomicReference<>();

        when(docker.copyArchiveToContainerCmd("created-container")).thenReturn(copy);
        when(copy.withTarInputStream(any())).thenAnswer(invocation -> {
            archive.set(invocation.getArgument(0));
            return copy;
        });
        when(copy.withRemotePath("/")).thenReturn(copy);
        when(copy.exec()).thenAnswer(_ -> {
            archive.get().transferTo(OutputStream.nullOutputStream());
            copying.countDown();
            boolean interrupted = false;
            try {
                // A synchronous Docker socket operation may not respond to worker interruption.
                while (removed.getCount() != 0) {
                    try {
                        removed.await();
                    } catch (InterruptedException _) {
                        interrupted = true;
                    }
                }
                return null;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                copyExited.countDown();
            }
        });
        when(docker.removeContainerCmd("created-container")).thenReturn(remove);
        when(remove.withForce(true)).thenReturn(remove);
        when(remove.withRemoveVolumes(true)).thenReturn(remove);
        when(remove.exec()).thenAnswer(_ -> {
            copyHeldUntilRemoval.set(copyExited.getCount() == 1);
            containerRemoved.set(true);
            removed.countDown();
            return null;
        });
        when(docker.inspectContainerCmd("created-container")).thenReturn(inspect);
        when(inspect.exec()).thenAnswer(_ -> {
            diagnosedBeforeRemoval.set(!containerRemoved.get());
            return state;
        });

        Path diagnostics = directory.resolve("diagnostics");
        try (var execution = new H2SpecExecution(Duration.ofSeconds(2), diagnostics)) {
            var section = new H2SpecClientIT.H2SpecSection("client/1", 34_000);
            var container = mock(H2SpecClientIT.SectionContainer.class,
                                 withSettings().useConstructor("unused-image", execution, section)
                                         .defaultAnswer(CALLS_REAL_METHODS));
            // Simulate successful creation, followed immediately by GenericContainer's startup archive copy.
            doReturn("created-container").when(container).getContainerId();
            doReturn(docker).when(container).getDockerClient();
            doReturn("startup container logs").when(container).getLogs();
            var future = execution.submit(() -> {
                container.copyFileToContainer(Transferable.of("launcher"), "/application.jar");
                return null;
            });
            assertThat("startup copy must enter the held Docker request", copying.await(2, TimeUnit.SECONDS), is(true));
            var failure = assertThrows(IllegalStateException.class,
                                       () -> execution.await(future, System.nanoTime(), "startup archive copy"));
            assertThat(failure.getMessage(), containsString("startup archive copy"));
            assertThat("state must be inspected before container removal", diagnosedBeforeRemoval.get(), is(true));
            assertThat(Files.readString(diagnostics.resolve("client-1.txt-state.txt")), is(state.toString()));
            assertThat(Files.readString(diagnostics.resolve("client-1.txt-logs.txt")), is("startup container logs"));
        } finally {
            removed.countDown();
            assertThat("held archive request must terminate during teardown", copyExited.await(2, TimeUnit.SECONDS), is(true));
        }
        assertThat("created container must be removed despite the held archive copy", containerRemoved.get(), is(true));
        assertThat("container removal must release the held copy independently of interruption",
                   copyHeldUntilRemoval.get(), is(true));
        verify(remove).withForce(true);
        verify(remove).withRemoveVolumes(true);
        verify(remove).exec();
    }

    @Test
    void creationCompletingAfterShutdownRemovesContainerBeforeCopying() throws Exception {
        DockerClient docker = mock(DockerClient.class);
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        when(docker.removeContainerCmd("late-container")).thenReturn(remove);
        when(remove.withForce(true)).thenReturn(remove);
        when(remove.withRemoveVolumes(true)).thenReturn(remove);
        try (var execution = new H2SpecExecution(Duration.ofSeconds(2), directory.resolve("diagnostics"))) {
            var section = new H2SpecClientIT.H2SpecSection("client/1", 34_000);
            var container = mock(H2SpecClientIT.SectionContainer.class,
                                 withSettings().useConstructor("unused-image", execution, section)
                                         .defaultAnswer(CALLS_REAL_METHODS));
            doReturn("late-container").when(container).getContainerId();
            doReturn(docker).when(container).getDockerClient();
            execution.close();

            assertThrows(IllegalStateException.class,
                         () -> container.copyFileToContainer(Transferable.of("launcher"), "/application.jar"));
            verify(remove).exec();
            verify(docker, never()).copyArchiveToContainerCmd(any());
        }
    }
}
