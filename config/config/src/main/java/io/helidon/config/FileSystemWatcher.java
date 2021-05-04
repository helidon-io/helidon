/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.ChangeWatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * This change watcher is backed by {@link WatchService} to fire a polling event with every change on monitored {@link Path}.
 * <p>
 * When a parent directory of the {@code path} is not available, or becomes unavailable later, a new attempt to register {@code
 * WatchService} is scheduled again and again until the directory finally exists and the registration is successful.
 * <p>
 * This {@link io.helidon.config.spi.ChangeWatcher} might be initialized with a custom {@link ScheduledExecutorService executor}
 * or the {@link Executors#newSingleThreadScheduledExecutor()} is used if none is explicitly configured.
 * <p>
 * This watcher notifies with appropriate change event in the following cases:
 * <ul>
 *     <li>The watched directory is gone</li>
 *     <li>The watched directory appears</li>
 *     <li>A file in the watched directory is deleted, created or modified</li>
 * </ul>
 * <p>
 * A single file system watcher may be used to watch multiple targets. In such a case, if {@link #stop()} is invoked, it stops
 * watching all of these targets.
 *
 * @see WatchService
 */
public final class FileSystemWatcher implements ChangeWatcher<Path> {

    private static final Logger LOGGER = Logger.getLogger(FileSystemWatcher.class.getName());

    /*
     * Configurable options through builder.
     */
    private final List<WatchEvent.Modifier> watchServiceModifiers = new LinkedList<>();
    private ScheduledExecutorService executor;
    private final boolean defaultExecutor;
    private final long initialDelay;
    private final long delay;
    private final TimeUnit timeUnit;

    /*
     * Runtime options.
     */
    private final List<TargetRuntime> runtimes = Collections.synchronizedList(new LinkedList<>());

    private FileSystemWatcher(Builder builder) {
        ScheduledExecutorService executor = builder.executor;
        if (executor == null) {
            this.executor = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("file-watch-polling"));
            this.defaultExecutor = true;
        } else {
            this.executor = executor;
            this.defaultExecutor = false;
        }

        this.watchServiceModifiers.addAll(builder.watchServiceModifiers);

        this.initialDelay = builder.initialDelay;
        this.delay = builder.delay;
        this.timeUnit = builder.timeUnit;
    }

    /**
     * Fluent API builder for {@link io.helidon.config.FileSystemWatcher}.
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new file watcher with default configuration.
     *
     * @return a new file watcher
     */
    public static FileSystemWatcher create() {
        return builder().build();
    }

    @Override
    public synchronized void start(Path target, Consumer<ChangeEvent<Path>> listener) {
        if (defaultExecutor && executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("file-watch-polling"));
        }
        if (executor.isShutdown()) {
            throw new ConfigException("Cannot start a watcher for path " + target + ", as the executor service is shutdown");
        }

        Monitor monitor = new Monitor(
                listener,
                target,
                watchServiceModifiers);

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(monitor, initialDelay, delay, timeUnit);

        this.runtimes.add(new TargetRuntime(monitor, future));
    }

    @Override
    public synchronized void stop() {
        runtimes.forEach(TargetRuntime::stop);

        if (defaultExecutor) {
            ConfigUtils.shutdownExecutor(executor);
        }
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

    private static final class TargetRuntime {
        private final Monitor monitor;
        private final ScheduledFuture<?> future;

        private TargetRuntime(Monitor monitor, ScheduledFuture<?> future) {
            this.monitor = monitor;
            this.future = future;
        }

        public void stop() {
            monitor.stop();
            future.cancel(true);
        }
    }

    private static final class Monitor implements Runnable {
        private final WatchService watchService;
        private final Consumer<ChangeEvent<Path>> listener;
        private final Path target;
        private final List<WatchEvent.Modifier> watchServiceModifiers;
        private final boolean watchingFile;
        private final Path watchedDir;

        /*
         * Runtime handling
         */
        // we have failed - retry registration on next trigger
        private volatile boolean failed = true;
        // maybe we were stopped, do not do anything (the scheduled future will be cancelled shortly)
        private volatile boolean shouldStop = false;
        // last file information
        private volatile boolean fileExists;

        private WatchKey watchKey;

        private Monitor(Consumer<ChangeEvent<Path>> listener,
                        Path target,
                        List<WatchEvent.Modifier> watchServiceModifiers) {
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                throw new ConfigException("Cannot obtain WatchService.", e);
            }
            this.listener = listener;
            this.target = target;
            this.watchServiceModifiers = watchServiceModifiers;
            this.fileExists = Files.exists(target);
            this.watchingFile = !Files.isDirectory(target);
            this.watchedDir = watchingFile ? target.getParent() : target;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            if (shouldStop) {
                return;
            }

            if (failed) {
                register();
            }

            if (failed) {
                return;
            }

            // if we used `take`, we would block the thread forever. This way we can use the same thread to handle
            // multiple targets
            WatchKey key = watchService.poll();
            if (null == key) {
                return;
            }

            List<WatchEvent<?>> watchEvents = key.pollEvents();
            if (watchEvents.isEmpty()) {
                // something happened, cannot get details
                key.cancel();
                listener.accept(ChangeEvent.create(target, ChangeEventType.CHANGED));
                failed = true;
                return;
            }

            // we actually have some changes
            for (WatchEvent<?> watchEvent : watchEvents) {
                WatchEvent<Path> event = (WatchEvent<Path>) watchEvent;
                Path eventPath = event.context();

                // as we watch on whole directory
                // make sure this is the watched file (if only interested in a single file)
                if (watchingFile && !target.endsWith(eventPath)) {
                    continue;
                }

                eventPath = watchedDir.resolve(eventPath);
                WatchEvent.Kind<Path> kind = event.kind();
                if (kind.equals(OVERFLOW)) {
                    LOGGER.finest("Overflow event on path: " + eventPath);
                    continue;
                }

                if (kind.equals(ENTRY_CREATE)) {
                    LOGGER.finest("Entry created. Path: " + eventPath);
                    listener.accept(ChangeEvent.create(eventPath, ChangeEventType.CREATED));
                } else if (kind == ENTRY_DELETE) {
                    LOGGER.finest("Entry deleted. Path: " + eventPath);
                    listener.accept(ChangeEvent.create(eventPath, ChangeEventType.DELETED));
                } else if (kind == ENTRY_MODIFY) {
                    LOGGER.finest("Entry changed. Path: " + eventPath);
                    listener.accept(ChangeEvent.create(eventPath, ChangeEventType.CHANGED));
                }
            }

            if (!key.reset()) {
                LOGGER.log(Level.FINE, () -> "Directory of '" + target + "' is no more valid to be watched.");
                failed = true;
            }
        }

        private void fire(Path target, ChangeEventType eventType) {
            listener.accept(ChangeEvent.create(target, eventType));
        }

        private synchronized void register() {
            if (shouldStop) {
                failed = true;
                return;
            }

            boolean oldFileExists = fileExists;

            try {
                Path cleanTarget = target(this.target);
                Path watchedDirectory = Files.isDirectory(cleanTarget) ? cleanTarget : parentDir(cleanTarget);

                WatchKey oldWatchKey = watchKey;
                watchKey = watchedDirectory.register(watchService,
                                                     new WatchEvent.Kind[] {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE},
                                                     watchServiceModifiers.toArray(new WatchEvent.Modifier[0]));

                failed = false;
                if (null != oldWatchKey) {
                    oldWatchKey.cancel();
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINEST, "Failed to register watch service", e);
                this.failed = true;
            }

            // in either case, let's see if our target has changed
            this.fileExists = Files.exists(target);

            if (fileExists != oldFileExists) {
                if (fileExists) {
                    fire(this.target, ChangeEventType.CREATED);
                } else {
                    fire(this.target, ChangeEventType.DELETED);
                }
            }
        }

        private synchronized void stop() {
            this.shouldStop = true;
            if (null != watchKey) {
                watchKey.cancel();
            }
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to close watch service", e);
            }
        }

        private Path target(Path path) throws IOException {
            Path target = path;
            while (Files.isSymbolicLink(target)) {
                target = target.toRealPath();
            }
            return target;
        }

        private Path parentDir(Path path) {
            Path parent = path.getParent();
            if (parent == null) {
                throw new ConfigException(
                        String.format("Cannot find parent directory for '%s' to register watch service.", path));
            }
            return parent;
        }
    }

    /**
     * Fluent API builder for {@link FileSystemWatcher}.
     */
    public static final class Builder implements io.helidon.common.Builder<FileSystemWatcher> {
        private final List<WatchEvent.Modifier> watchServiceModifiers = new LinkedList<>();
        private ScheduledExecutorService executor;
        private long initialDelay = 1000;
        private long delay = 100;
        private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        private Builder() {
        }

        @Override
        public FileSystemWatcher build() {
            return new FileSystemWatcher(this);
        }

        /**
         * Update this builder from meta configuration.
         * <p>
         * Currently these options are supported:
         * <ul>
         *     <li>{@code initial-delay-millis} - delay between the time this watcher is started
         *      and the time the first check is triggered</li>
         *     <li>{@code delay-millis} - how often do we check the watcher service for changes</li>
         * </ul>
         * As the watcher is implemented as non-blocking, a single watcher can be used to watch multiple
         * directories using the same thread.
         *
         * @param metaConfig configuration of file system watcher
         * @return updated builder instance
         */
        public Builder config(Config metaConfig) {
            metaConfig.get("initial-delay-millis")
                    .asLong()
                    .ifPresent(initDelay -> initialDelay = timeUnit.convert(initDelay, TimeUnit.MILLISECONDS));
            metaConfig.get("delay-millis")
                    .asLong()
                    .ifPresent(delayMillis -> delay = timeUnit.convert(delayMillis, TimeUnit.MILLISECONDS));

            return this;
        }

        /**
         * Executor to use for this watcher.
         * The task is scheduled for regular execution and is only blocking a thread for the time needed
         * to process changed files.
         *
         * @param executor executor service to use
         * @return updated builder instance
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Configure schedule of the file watcher.
         *
         * @param initialDelay initial delay before regular scheduling starts
         * @param delay delay between schedules
         * @param timeUnit time unit of the delays
         * @return updated builer instance
         */
        public Builder schedule(long initialDelay, long delay, TimeUnit timeUnit) {
            this.initialDelay = initialDelay;
            this.delay = delay;
            this.timeUnit = timeUnit;
            return this;
        }

        /**
         * Add a modifier of the watch service.
         * Currently only implementation specific modifier are available, such as
         * {@code com.sun.nio.file.SensitivityWatchEventModifier}.
         *
         * @param modifier modifier to use
         * @return updated builder instance
         */
        public Builder addWatchServiceModifier(WatchEvent.Modifier modifier) {
            this.watchServiceModifiers.add(modifier);
            return this;
        }

        /**
         * Set modifiers to use for the watch service.
         * Currently only implementation specific modifier are available, such as
         * {@code com.sun.nio.file.SensitivityWatchEventModifier}.
         *
         * @param modifiers modifiers to use (replacing current configuration)
         * @return updated builder instance
         */
        public Builder watchServiceModifiers(List<WatchEvent.Modifier> modifiers) {
            this.watchServiceModifiers.clear();
            this.watchServiceModifiers.addAll(modifiers);

            return this;
        }
    }
}
