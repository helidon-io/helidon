/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.FileSystemWatcher}.
 */
public class FileSystemWatcherTest {
    private static final String WATCHED_FILE = "watched-file.yaml";

    @TempDir
    Path tempDir;

    @Test
    void testNullExecutorPreservesCustomSelection() {
        assertThrows(NullPointerException.class, () -> FileSystemWatcher.builder().executor(null));
        try (var executor = new TrackingExecutor()) {
            var builder = FileSystemWatcher.builder().executor(executor);
            assertThrows(NullPointerException.class, () -> builder.executor(null));
            var watcher = builder.build();
            try {
                watcher.start(tempDir.resolve(WATCHED_FILE), _ -> { });
                assertThat("An invalid setter must preserve the selected executor", executor.schedulingCount.get(), is(1));
            } finally {
                watcher.stop();
            }
            assertThat("A supplied executor remains caller-owned", executor.isShutdown(), is(false));
        }
    }

    @Test
    void testClearExecutorAffectsFutureBuilds() throws Exception {
        try (var executor = new TrackingExecutor()) {
            var builder = FileSystemWatcher.builder()
                    .initialDelay(Duration.ZERO)
                    .delay(Duration.ofMillis(10))
                    .executor(executor);
            var custom = builder.build();
            var defaults = builder.clearExecutor().clearExecutor().build();
            var restored = builder.executor(executor).build();
            var changed = new CountDownLatch(1);
            Path defaultTarget = tempDir.resolve("default.yaml");
            try {
                assertThat("Clearing must leave the supplied pool usable",
                           executor.submit(() -> "running").get(10, TimeUnit.SECONDS), is("running"));
                custom.start(tempDir.resolve("custom.yaml"), _ -> { });
                restored.start(tempDir.resolve("restored.yaml"), _ -> { });
                defaults.start(defaultTarget, _ -> changed.countDown());
                Files.createFile(defaultTarget);
                assertThat("The cleared default should watch for changes", changed.await(40, TimeUnit.SECONDS), is(true));
                assertThat("Only the custom watchers should use the supplied pool", executor.schedulingCount.get(), is(2));
            } finally {
                custom.stop();
                defaults.stop();
                restored.stop();
            }
            assertThat("Stopping watchers must leave the supplied pool usable",
                       executor.submit(() -> "running").get(10, TimeUnit.SECONDS), is("running"));
        }
    }

    @Test
    public void testWatchedDirectoryDeleted() throws IOException, InterruptedException {
        CountDownLatch watchedDirLatch = new CountDownLatch(1);

        Path watchedDir = Files.createDirectory(tempDir.resolve("watched-dir"));
        Files.write(Files.createFile(watchedDir.resolve("username")), "libor".getBytes());

        FileSystemWatcher watcher = FileSystemWatcher.create();

        watcher.start(watchedDir, changeEvent -> watchedDirLatch.countDown());

        deleteDir(watchedDir.toFile());

        assertThat(watchedDirLatch.await(60, TimeUnit.SECONDS), is(true));
    }

    void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    @Test
    public void testFileWatching() throws InterruptedException, IOException {
        CountDownLatch watchedFileLatch = new CountDownLatch(1);

        Path watchedPath = tempDir.resolve(WATCHED_FILE);

        FileSystemWatcher watcher = FileSystemWatcher.create();
        watcher.start(watchedPath, changeEvent -> watchedFileLatch.countDown());

        Files.createFile(watchedPath);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testPollingNotYetExisting() throws InterruptedException, IOException {
        CountDownLatch watchedFileLatch = new CountDownLatch(1);

        Path subdir = tempDir.resolve("subdir");
        Path watchedPath = subdir.resolve(WATCHED_FILE);

        FileSystemWatcher watcher = FileSystemWatcher.create();
        watcher.start(watchedPath, changeEvent -> watchedFileLatch.countDown());

        Files.createDirectory(subdir);
        Files.createFile(watchedPath);

        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testPollingAfterRestartWatchService() throws InterruptedException, IOException {
        CountDownLatch watchedFileLatch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();

        Path watchedPath = tempDir.resolve(WATCHED_FILE);

        FileSystemWatcher watcher = FileSystemWatcher.create();
        watcher.start(watchedPath, changeEvent -> {
            watchedFileLatch.countDown();
            count.incrementAndGet();
        });

        watcher.stop();
        watcher.start(watchedPath, changeEvent -> {
            watchedFileLatch.countDown();
            count.incrementAndGet();
        });

        Files.createFile(watchedPath);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
        assertThat("This should only be called once", count.get(), is(1));
    }

    private static final class TrackingExecutor extends ScheduledThreadPoolExecutor {
        private final AtomicInteger schedulingCount = new AtomicInteger();

        private TrackingExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            schedulingCount.incrementAndGet();
            return super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
    }
}
