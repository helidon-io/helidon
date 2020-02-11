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

package io.helidon.config.changes;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.config.ChangeEventType;
import io.helidon.config.spi.ChangeWatcher.Change;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class FileChangeWatcherTest {
    private Path directory;

    @BeforeEach
    void beforeEach() throws IOException {
        directory = Files.createTempDirectory("helidon-unit-");
    }

    @AfterEach
    void afterEach() throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    @Disabled("This test requires around 30 seconds to run...")
    void testWatching() throws IOException, InterruptedException {
        BlockingQueue<Change<Path>> events = new LinkedBlockingQueue<>();

        Consumer<Change<Path>> listener = events::add;

        Path target = directory.resolve("test.properties");

        FileChangeWatcher watcher = FileChangeWatcher.create();
        watcher.start(target, listener);

        Files.write(target, List.of("line=value"));
        Change<Path> poll = events.poll(15, TimeUnit.SECONDS);
        assertThat(poll, notNullValue());
        assertThat(poll.type(), is(ChangeEventType.CREATED));
        assertThat(poll.target(), is(target));
        assertThat(poll.changeInstant(), notNullValue());
        assertThat("Queue should be empty", events.size(), is(0));

        Files.write(target, List.of("line=newValue"));
        poll = events.poll(15, TimeUnit.SECONDS);
        assertThat(poll, notNullValue());
        assertThat(poll.type(), is(ChangeEventType.CHANGED));
        assertThat(poll.target(), is(target));
        assertThat(poll.changeInstant(), notNullValue());
        assertThat("Queue should be empty", events.size(), is(0));

        Files.delete(target);
        poll = events.poll(15, TimeUnit.SECONDS);
        assertThat(poll, notNullValue());
        assertThat(poll.type(), is(ChangeEventType.DELETED));
        assertThat(poll.target(), is(target));
        assertThat(poll.changeInstant(), notNullValue());
        assertThat("Queue should be empty", events.size(), is(0));
    }
}