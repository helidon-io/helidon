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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2SpecExecutionTest {
    @TempDir
    Path directory;

    @Test
    void removesDiagnosticsFromAnEarlierRun() throws Exception {
        Path diagnostics = Files.createDirectory(directory.resolve("diagnostics"));
        Path stale = Files.writeString(diagnostics.resolve("host-threads.txt"), "old timeout");
        Path unrelated = Files.writeString(directory.resolve("unrelated.txt"), "keep");
        try (var _ = new H2SpecExecution(Duration.ofSeconds(2), diagnostics)) {
            assertThat("an earlier timeout must not appear in the current run's artifacts", Files.exists(stale), is(false));
            assertThat(Files.readString(unrelated), is("keep"));
        }
    }

    @Test
    void timeoutClosesStreamRemovesResourceAndPreservesPartialOutput() throws Exception {
        Path output = directory.resolve("section.txt");
        AtomicBoolean streamClosed = new AtomicBoolean();
        AtomicBoolean removed = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Path diagnostics = directory.resolve("diagnostics");
        try (var execution = new H2SpecExecution(Duration.ofSeconds(2), diagnostics);
             var file = Files.newOutputStream(output)) {
            var callback = new H2SpecExecution.Callback<byte[]>(bytes -> {
                try {
                    file.write(bytes);
                    file.flush();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            callback.onStart(() -> streamClosed.set(true));
            execution.own(callback);
            execution.own(() -> removed.set(true));
            execution.diagnostic("container-state.txt", () -> removed.get() ? "removed" : "running");
            var future = execution.submit(() -> {
                callback.onNext("partial output".getBytes(StandardCharsets.UTF_8));
                started.countDown();
                try {
                    callback.await();
                    new CountDownLatch(1).await();
                } catch (InterruptedException _) {
                    interrupted.countDown();
                }
                return null;
            });
            assertThat("worker did not start", started.await(2, TimeUnit.SECONDS), is(true));
            assertThat(Files.readString(output), is("partial output"));
            assertThrows(IllegalStateException.class, () -> execution.await(future, System.nanoTime(), "test section"));
        }
        assertThat("Docker stream must close on timeout", streamClosed.get(), is(true));
        assertThat("owned container removal must run on timeout", removed.get(), is(true));
        assertThat("blocked worker must be interrupted", interrupted.await(2, TimeUnit.SECONDS), is(true));
        assertThat(Files.readString(output), is("partial output"));
        assertThat(Files.readString(diagnostics.resolve("container-state.txt")), is("running"));
        assertThat("timeout phase must be recorded with the host stacks",
                   Files.readString(diagnostics.resolve("host-threads.txt")), containsString("test section"));
    }

    @Test
    void streamArrivingAfterCancellationIsImmediatelyClosed() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        var callback = new H2SpecExecution.Callback<String>(_ -> {
            throw new AssertionError("Output must not be delivered after cancellation");
        });
        callback.close();
        callback.onStart(() -> closed.set(true));
        callback.onNext("late output");
        assertThat("late Docker response must not escape cleanup", closed.get(), is(true));
    }

    @Test
    void resourceAcquiredAfterShutdownIsRemoved() throws Exception {
        AtomicBoolean removed = new AtomicBoolean();
        var execution = new H2SpecExecution(Duration.ofSeconds(2));
        execution.close();
        assertThrows(IOException.class, () -> execution.own(() -> removed.set(true)));
        assertThat("late-created container must be removed", removed.get(), is(true));
    }
}
