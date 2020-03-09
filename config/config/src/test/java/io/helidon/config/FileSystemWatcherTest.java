/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.test.infra.TemporaryFolderExt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link io.helidon.config.FileSystemWatcher}.
 */
@Disabled //TODO tests are still TOO slow to be unit tests -> refactor it to run much quicker
public class FileSystemWatcherTest {
    private static final String WATCHED_FILE = "watched-file.yaml";

    @RegisterExtension
    static TemporaryFolderExt dir = TemporaryFolderExt.build();

    @Test
    public void testWatchedDirectoryDeleted() throws IOException, InterruptedException {
        CountDownLatch watchedDirLatch = new CountDownLatch(1);

        File watchedDir = dir.newFolder();
        Files.write(Files.createFile(new File(watchedDir, "username").toPath()), "libor".getBytes());

        FileSystemWatcher watcher = FileSystemWatcher.create();

        watcher.start(watchedDir.toPath(), changeEvent -> watchedDirLatch.countDown());

        deleteDir(watchedDir);

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

        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);

        FileSystemWatcher watcher = FileSystemWatcher.create();
        watcher.start(watchedPath, changeEvent -> watchedFileLatch.countDown());

        dir.newFile(WATCHED_FILE);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testPollingNotYetExisting() throws InterruptedException, IOException {
        CountDownLatch watchedFileLatch = new CountDownLatch(1);

        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path subdir = dirPath.resolve("subdir");
        Path watchedPath = subdir.resolve(WATCHED_FILE);

        FileSystemWatcher watcher = FileSystemWatcher.create();
        watcher.start(watchedPath, changeEvent -> watchedFileLatch.countDown());

        dir.newFolder("subdir");
        dir.newFile("subdir/" + WATCHED_FILE);

        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testPollingSymLink() throws InterruptedException, IOException {
        CountDownLatch firstEventLatch = new CountDownLatch(1);
        CountDownLatch secondEventLatch = new CountDownLatch(1);
        CountDownLatch thirdEventLatch = new CountDownLatch(1);

        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);

        FileSystemWatcher watcher = FileSystemWatcher.create();
        watcher.start(watchedPath, changeEvent -> {
            if (firstEventLatch.getCount() > 0) {
                firstEventLatch.countDown();
            } else if (secondEventLatch.getCount() > 0) {
                secondEventLatch.countDown();
            } else {
                thirdEventLatch.countDown();
            }
        });

        Path targetDir = createFile(List.of("a: a"));
        Path symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), targetDir);
        Path symlink = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), WATCHED_FILE),
                                                Paths.get(symlinkToDir.toString(), WATCHED_FILE));

        Path newTarget = createFile(List.of("a: b"));
        Files.walk(targetDir).map(Path::toFile).forEach(File::delete);
        Files.delete(targetDir);
        Files.delete(symlinkToDir);
        symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), newTarget);

        printDir();

        assertThat(firstEventLatch.await(30, TimeUnit.SECONDS), is(true));

        targetDir = newTarget;
        newTarget = createFile(List.of("a: c"));
        Files.walk(targetDir).map(Path::toFile).forEach(File::delete);
        Files.delete(targetDir);
        Files.delete(symlinkToDir);
        symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), newTarget);

        printDir();

        assertThat(secondEventLatch.await(40, TimeUnit.SECONDS), is(true));

        targetDir = newTarget;
        newTarget = createFile(List.of("a: d"));
        Files.walk(targetDir).map(Path::toFile).forEach(File::delete);
        Files.delete(targetDir);
        Files.delete(symlinkToDir);
        Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), newTarget);

        printDir();

        assertThat(thirdEventLatch.await(40, TimeUnit.SECONDS), is(true));

    }

    private void printDir() throws IOException {
        Files.walk(dir.getRoot().toPath()).forEach(f -> {
            System.out.print(f.toFile().isFile() ? "f" : "d");
            System.out.print(" ");
            System.out.print(f.toFile().lastModified());
            System.out.print(" ");
            System.out.println(f);
        });
    }

    private Path createFile(Iterable<String> content) throws IOException {
        File folder = dir.newFolder("symlink-folder-" + UUID.randomUUID());
        Path target = Files.createFile(Paths.get(folder.toString(), FileSystemWatcherTest.WATCHED_FILE));
        Files.write(target, content);
        return folder.toPath();
    }

    @Test
    public void testPollingAfterRestartWatchService() throws InterruptedException, IOException {
        CountDownLatch watchedFileLatch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();

        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);

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

        dir.newFile(WATCHED_FILE);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
        assertThat("This should only be called once", count.get(), is(1));
    }
}
