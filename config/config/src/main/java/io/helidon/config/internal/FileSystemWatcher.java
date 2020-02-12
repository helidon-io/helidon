/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.ConfigException;
import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.PollingStrategy;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * This polling strategy is backed by {@link WatchService} to fire a polling event with every change on monitored {@link Path}.
 * <p>
 * When a parent directory of the {@code path} is not available, or becomes unavailable later, a new attempt to register {@code
 * WatchService} is scheduled again and again until the directory finally exists and the registration is successful.
 * <p>
 * This {@link PollingStrategy} might be initialized with a custom {@link ScheduledExecutorService executor} or the {@link
 * Executors#newSingleThreadScheduledExecutor()} is assigned when parameter is {@code null}.
 *
 * @see WatchService
 */
public class FileSystemWatcher implements ChangeWatcher<Path> {

    private static final Logger LOGGER = Logger.getLogger(FileSystemWatcher.class.getName());

    private ScheduledExecutorService executor;
    private final List<WatchEvent.Modifier> watchServiceModifiers;

    // TODO: add support for closing this
    private ScheduledFuture<?> scheduledFuture;

    /**
     * Creates a strategy with watched {@code path} as a parameters.
     *
     * @param executor a custom executor or the {@link io.helidon.config.internal.ConfigThreadFactory} is assigned when
     *                 parameter is {@code null}
     */
    private FileSystemWatcher(ScheduledExecutorService executor) {
        if (executor == null) {
            this.executor = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("file-watch-polling"));
        } else {
            this.executor = executor;
        }

        this.watchServiceModifiers = new LinkedList<>();
    }

    @Override
    public void start(Path target, Consumer<ChangeEvent<Path>> listener) {
        WatchService watchService;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new ConfigException("Cannot obtain WatchService.", e);
        }


        Path watchedDirectory;
        if (Files.isDirectory(target)) {
            watchedDirectory = target;
        } else {
            watchedDirectory = target.getParent();
        }

        Monitor monitor = new Monitor(executor,
                                      watchService,
                                      listener,
                                      target,
                                      watchedDirectory,
                                      watchServiceModifiers);

        this.scheduledFuture = executor.scheduleWithFixedDelay(monitor, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public Class<Path> type() {
        return Path.class;
    }

    /**
     * Add modifiers to be used when registering the {@link WatchService}.
     * See {@link Path#register(WatchService, WatchEvent.Kind[],
     * WatchEvent.Modifier...) Path.register}.
     *
     * @param modifiers the modifiers to add
     */
    public void initWatchServiceModifiers(WatchEvent.Modifier... modifiers) {
        watchServiceModifiers.addAll(Arrays.asList(modifiers));
    }

    private static class Monitor implements Runnable {
        private final ScheduledExecutorService executor;
        private WatchService watchService;
        private final Consumer<ChangeEvent<Path>> listener;
        private final Path path;
        private final Path target;
        private final Path watchedDirectory;
        private final boolean watchDir;
        private final List<WatchEvent.Modifier> watchServiceModifiers;
        private final AtomicBoolean failed = new AtomicBoolean(false);

        private WatchKey watchKey;

        private Monitor(ScheduledExecutorService executor,
                        WatchService watchService,
                        Consumer<ChangeEvent<Path>> listener,
                        Path target,
                        Path watchedDirectory,
                        List<WatchEvent.Modifier> watchServiceModifiers) {
            this.executor = executor;
            this.watchService = watchService;
            this.listener = listener;
            this.target = target;
            this.watchedDirectory = watchedDirectory;
            this.watchServiceModifiers = watchServiceModifiers;
            this.path = target.equals(watchedDirectory) ? target : watchedDirectory.relativize(target);
            this.watchDir = target.equals(watchedDirectory);
        }

        private void register() {
            try {
                WatchKey oldWatchKey = watchKey;
                watchKey = watchedDirectory.register(watchService,
                                          new WatchEvent.Kind[] {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE},
                                          watchServiceModifiers.toArray(new WatchEvent.Modifier[0]));

                failed.set(false);
                if (null != oldWatchKey) {
                    oldWatchKey.cancel();
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINEST, "Failed to register watch service", e);
                failed.set(true);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            i
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
                    listener.accept(ChangeEvent.create(target, ChangeEventType.CREATED));
                } else if (kind == ENTRY_DELETE) {
                    listener.accept(ChangeEvent.create(target, ChangeEventType.DELETED));
                } else if (kind == ENTRY_MODIFY) {
                    listener.accept(ChangeEvent.create(target, ChangeEventType.CHANGED));
                }
            }

            boolean reset = key.reset();
            if (reset) {
                schedule();
            } else {
                LOGGER.info("Directory " + watchedDirectory + " is no longer valid to be watched. Watcher terminated.");
            }
        }

        private void schedule() {
            executor.schedule(this, 100, TimeUnit.MILLISECONDS);
        }
    }
}
