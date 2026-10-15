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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link io.helidon.config.FileSystemWatcher}.
 */
public class FileSystemWatcherTest {
    private static final String WATCHED_FILE = "watched-file.yaml";

    @TempDir
    Path tempDir;

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
}
