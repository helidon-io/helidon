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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2SpecExecutionTest {
    @TempDir
    Path directory;

    @Test
    void removesDiagnosticsFromAnEarlierRun() throws Exception {
        Path diagnostics = Files.createDirectory(directory.resolve("diagnostics"));
        Path stale = Files.writeString(diagnostics.resolve("host-threads.txt"), "old timeout");
        Path unrelated = Files.writeString(directory.resolve("unrelated.txt"), "keep");
        try (var execution = new H2SpecExecution(Duration.ofSeconds(2), diagnostics)) {
            assertFalse(Files.exists(stale), "an earlier timeout must not appear in the current run's artifacts");
            assertEquals("keep", Files.readString(unrelated));
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
            assertTrue(started.await(2, TimeUnit.SECONDS), "worker did not start");
            assertEquals("partial output", Files.readString(output));
            assertThrows(IllegalStateException.class, () -> execution.await(future, System.nanoTime(), "test section"));
        }
        assertTrue(streamClosed.get(), "Docker stream must close on timeout");
        assertTrue(removed.get(), "owned container removal must run on timeout");
        assertTrue(interrupted.await(2, TimeUnit.SECONDS), "blocked worker must be interrupted");
        assertEquals("partial output", Files.readString(output));
        assertEquals("running", Files.readString(diagnostics.resolve("container-state.txt")));
        assertTrue(Files.readString(diagnostics.resolve("host-threads.txt")).contains("test section"),
                   "timeout phase must be recorded with the host stacks");
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
        assertTrue(closed.get(), "late Docker response must not escape cleanup");
    }

    @Test
    void resourceAcquiredAfterShutdownIsRemoved() throws Exception {
        AtomicBoolean removed = new AtomicBoolean();
        var execution = new H2SpecExecution(Duration.ofSeconds(2));
        execution.close();
        assertThrows(IOException.class, () -> execution.own(() -> removed.set(true)));
        assertTrue(removed.get(), "late-created container must be removed");
    }
}
