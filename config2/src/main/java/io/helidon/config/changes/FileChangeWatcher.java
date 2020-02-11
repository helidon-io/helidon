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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.ChangeEventType;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ChangeWatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileChangeWatcher implements ChangeWatcher<Path> {
    private static final Logger LOGGER = Logger.getLogger(FileChangeWatcher.class.getName());
    private static final ScheduledExecutorService WATCH_EXECUTOR = Executors
            .newSingleThreadScheduledExecutor(r -> new Thread(r, "FileChangeWatcher Thread"));
    private AtomicBoolean run = new AtomicBoolean(true);

    private FileChangeWatcher() {
    }

    public static FileChangeWatcher create() {
        return new FileChangeWatcher();
    }

    @Override
    public Class<Path> type() {
        return Path.class;
    }

    @Override
    public void start(Path target, Consumer<Change<Path>> listener) {
        Path watchedDirectory;
        if (Files.isDirectory(target)) {
            watchedDirectory = target;
        } else {
            watchedDirectory = target.getParent();
        }

        WatchService watcher;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            watchedDirectory.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            throw new ConfigException("Failed to create a file watch service on " + watchedDirectory.toAbsolutePath(), e);
        }

        Monitor monitor = new Monitor(listener, target, watchedDirectory, watcher, run);
        WATCH_EXECUTOR.schedule(monitor, 1, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        run.set(false);
    }

    private static class Monitor implements Runnable {
        private final Consumer<Change<Path>> listener;
        private final Path path;
        private final Path target;
        private final boolean watchDir;
        private final WatchService watcher;
        private final AtomicBoolean run;

        private Monitor(Consumer<Change<Path>> listener,
                        Path target,
                        Path watchedDirectory,
                        WatchService watcher,
                        AtomicBoolean run) {
            this.listener = listener;
            this.target = target;
            this.path = target.equals(watchedDirectory) ? target : watchedDirectory.relativize(target);
            this.watchDir = target.equals(watchedDirectory);

            this.watcher = watcher;
            this.run = run;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            if (!run.get()) {
                // do not check, do not reschedule, cancel
                try {
                    watcher.close();
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Failed to close watcher service on directory of: " + target.toAbsolutePath(), e);
                }
                return;
            }

            WatchKey key = watcher.poll();
            if (null == key) {
                schedule();
                return;
            }

            for (WatchEvent<?> watchEvent : key.pollEvents()) {
                WatchEvent<Path> event = (WatchEvent<Path>) watchEvent;
                Path context = event.context();

                // if we watch on whole directory
                // make sure this is the watched file
                if (!watchDir && !context.equals(path)) {
                    continue;
                }

                WatchEvent.Kind<Path> kind = event.kind();
                if (kind.equals(OVERFLOW)) {
                    continue;
                }

                if (kind == ENTRY_CREATE) {
                    listener.accept(Change.create(target, ChangeEventType.CREATED));
                } else if (kind == ENTRY_DELETE) {
                    listener.accept(Change.create(target, ChangeEventType.DELETED));
                } else if (kind == ENTRY_MODIFY) {
                    listener.accept(Change.create(target, ChangeEventType.CHANGED));
                }
            }

            // todo handle result
            boolean reset = key.reset();
            schedule();
        }

        private void schedule() {
            WATCH_EXECUTOR.schedule(this, 100, TimeUnit.MILLISECONDS);
        }
    }
}
