/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.spi.PollingStrategy;

import com.sun.nio.file.SensitivityWatchEventModifier;
import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.test.infra.TemporaryFolderExt;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests {@link FilesystemWatchPollingStrategy}.
 */
@Disabled //TODO tests are still TOO slow to be unit tests -> refactor it to run much quicker
public class FilesystemWatchPollingStrategyTest {
    private static final String WATCHED_FILE = "watched-file.yaml";

    @RegisterExtension
    static TemporaryFolderExt dir = TemporaryFolderExt.build();
    
    @Test
    public void testPollingDirectoryDeleted() throws IOException, InterruptedException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch watchedDirLatch = new CountDownLatch(1);

        File watchedDir = dir.newFolder();
        Files.write(Files.createFile(new File(watchedDir, "username").toPath()), "libor".getBytes());

        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedDir.toPath(), null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);

        SubmissionPublisher<PollingStrategy.PollingEvent> publisher = new SubmissionPublisher<>();
        when(mockPollingStrategy.getTicksSubmitter()).thenReturn(publisher);

        publisher.subscribe(new Flow.Subscriber<PollingStrategy.PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(1);
            }

            @Override
            public void onNext(PollingStrategy.PollingEvent item) {
                watchedDirLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                Assertions.fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        mockPollingStrategy.startWatchService();

        assertThat(subscribeLatch.await(10, TimeUnit.MILLISECONDS), is(true));

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
    public void testPolling() throws InterruptedException, IOException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch watchedFileLatch = new CountDownLatch(1);

        SubmissionPublisher<PollingStrategy.PollingEvent> publisher = new SubmissionPublisher<>();
        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);
        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedPath, null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);
        when(mockPollingStrategy.getTicksSubmitter()).thenReturn(publisher);

        publisher.subscribe(new Flow.Subscriber<PollingStrategy.PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(1);
            }

            @Override
            public void onNext(PollingStrategy.PollingEvent item) {
                watchedFileLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                Assertions.fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        mockPollingStrategy.startWatchService();

        assertThat(subscribeLatch.await(10, TimeUnit.MILLISECONDS), is(true));

        dir.newFile(WATCHED_FILE);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testPollingNotYetExisting() throws InterruptedException, IOException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch watchedFileLatch = new CountDownLatch(1);

        SubmissionPublisher<PollingStrategy.PollingEvent> publisher = new SubmissionPublisher<>();
        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path subdir = dirPath.resolve("subdir");
        Path watchedPath = subdir.resolve(WATCHED_FILE);
        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedPath, null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);
        when(mockPollingStrategy.getTicksSubmitter()).thenReturn(publisher);

        publisher.subscribe(new Flow.Subscriber<PollingStrategy.PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(1);
            }

            @Override
            public void onNext(PollingStrategy.PollingEvent item) {
                watchedFileLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                Assertions.fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        mockPollingStrategy.startWatchService();

        assertThat(subscribeLatch.await(10, TimeUnit.MILLISECONDS), is(true));

        dir.newFolder("subdir");
        dir.newFile("subdir/" + WATCHED_FILE);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));

    }

    @Test
    public void testPollingSymLink() throws InterruptedException, IOException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch firstEventLatch = new CountDownLatch(1);
        CountDownLatch secondEventLatch = new CountDownLatch(1);
        CountDownLatch thirdEventLatch = new CountDownLatch(1);

        SubmissionPublisher<PollingStrategy.PollingEvent> publisher = new SubmissionPublisher<>();
        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);
        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedPath, null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);
        when(mockPollingStrategy.getTicksSubmitter()).thenReturn(publisher);

        publisher.subscribe(new Flow.Subscriber<PollingStrategy.PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(PollingStrategy.PollingEvent item) {
                System.out.println("on next");
                if (firstEventLatch.getCount() > 0) {
                    firstEventLatch.countDown();
                    System.out.println("first event received");
                } else if (secondEventLatch.getCount() > 0) {
                    secondEventLatch.countDown();
                    System.out.println("second event received");
                } else {
                    thirdEventLatch.countDown();
                    System.out.println("third event received");
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Assertions.fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(10, TimeUnit.MILLISECONDS), is(true));

        Path targetDir = createFile(WATCHED_FILE, CollectionsHelper.listOf("a: a"));
        Path symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), targetDir);
        Path symlink = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), WATCHED_FILE),
                                                Paths.get(symlinkToDir.toString(), WATCHED_FILE));
        mockPollingStrategy.startWatchService();

        Path newTarget = createFile(WATCHED_FILE, CollectionsHelper.listOf("a: b"));
        Files.walk(targetDir).map(Path::toFile).forEach(File::delete);
        Files.delete(targetDir);
        Files.delete(symlinkToDir);
        symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), newTarget);

        printDir();

        assertThat(firstEventLatch.await(30, TimeUnit.SECONDS), is(true));

        targetDir = newTarget;
        newTarget = createFile(WATCHED_FILE, CollectionsHelper.listOf("a: c"));
        Files.walk(targetDir).map(Path::toFile).forEach(File::delete);
        Files.delete(targetDir);
        Files.delete(symlinkToDir);
        symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), newTarget);

        printDir();

        assertThat(secondEventLatch.await(40, TimeUnit.SECONDS), is(true));

        targetDir = newTarget;
        newTarget = createFile(WATCHED_FILE, CollectionsHelper.listOf("a: d"));
        Files.walk(targetDir).map(Path::toFile).forEach(File::delete);
        Files.delete(targetDir);
        Files.delete(symlinkToDir);
        symlinkToDir = Files.createSymbolicLink(Paths.get(dir.getRoot().toString(), "symlink-to-target-dir"), newTarget);

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

    private Path createFile(String s, Iterable<String> content) throws IOException {
        File folder = dir.newFolder("symlink-folder-" + UUID.randomUUID());
        Path target = Files.createFile(Paths.get(folder.toString(), WATCHED_FILE));
        Files.write(target, content);
        return folder.toPath();
    }

    @Test
    public void testPollingAfterRestartWatchService() throws InterruptedException, IOException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch watchedFileLatch = new CountDownLatch(1);

        SubmissionPublisher<PollingStrategy.PollingEvent> publisher = new SubmissionPublisher<>();
        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);
        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedPath, null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);
        when(mockPollingStrategy.getTicksSubmitter()).thenReturn(publisher);

        publisher.subscribe(new Flow.Subscriber<PollingStrategy.PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(1);
            }

            @Override
            public void onNext(PollingStrategy.PollingEvent item) {
                watchedFileLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                Assertions.fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        mockPollingStrategy.startWatchService();
        mockPollingStrategy.stopWatchService();
        mockPollingStrategy.startWatchService();

        assertThat(subscribeLatch.await(10, TimeUnit.MILLISECONDS), is(true));

        dir.newFile(WATCHED_FILE);
        assertThat(watchedFileLatch.await(40, TimeUnit.SECONDS), is(true));

    }

    @Test
    public void testWatchThreadFuture() throws InterruptedException {
        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);
        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedPath, null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);
        mockPollingStrategy.startWatchService();

        Assertions.assertNotNull(mockPollingStrategy.getWatchThreadFuture());
        assertThat(mockPollingStrategy.getWatchThreadFuture().isCancelled(), is(false));

        mockPollingStrategy.startWatchService();
    }

    @Test
    public void testWatchThreadFutureCanceled() throws InterruptedException {
        Path dirPath = FileSystems.getDefault().getPath(dir.getRoot().getAbsolutePath());
        Path watchedPath = dirPath.resolve(WATCHED_FILE);
        FilesystemWatchPollingStrategy mockPollingStrategy = spy(new FilesystemWatchPollingStrategy(watchedPath, null));
        mockPollingStrategy.initWatchServiceModifiers(SensitivityWatchEventModifier.HIGH);
        mockPollingStrategy.startWatchService();
        mockPollingStrategy.stopWatchService();

        Assertions.assertNotNull(mockPollingStrategy.getWatchThreadFuture());
        assertThat(mockPollingStrategy.getWatchThreadFuture().isCancelled(), is(true));
    }

}
