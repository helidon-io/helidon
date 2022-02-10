/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.NativeImageHelper;
import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.PollingStrategy;

/**
 * Mutability support for file based sources.
 * <p>
 * Provides support for polling based strategy
 * ({@link #poll(java.nio.file.Path, java.time.Duration, java.util.function.Consumer, java.util.function.Consumer)}) and
 * for file watching ({@link #watch(java.nio.file.Path, java.util.function.Consumer, java.util.function.Consumer)}).
 */
public final class MutabilitySupport {
    private static final Logger LOGGER = Logger.getLogger(MutabilitySupport.class.getName());
    private static final LazyValue<ScheduledExecutorService> EXECUTOR
            = LazyValue.create(Executors::newSingleThreadScheduledExecutor);

    private MutabilitySupport() {
    }

    /**
     * Start polling for changes.
     *
     * @param path path to watch
     * @param duration duration of polling
     * @param updater consumer that reads the file content and updates properties (in case file is changed)
     * @param cleaner runnable to clean the properties (in case file is deleted)
     * @return runnable to stop the file watcher
     */
    public static Runnable poll(Path path, Duration duration, Consumer<Path> updater, Consumer<Path> cleaner) {
        if (NativeImageHelper.isBuildTime()) {
            LOGGER.info("File polling is not enabled in native image build time. Path: " + path);
        }

        PollingStrategy strategy = PollingStrategies.regular(duration)
                .executor(EXECUTOR.get())
                .build();

        strategy.start(new PathPolled(path, updater, cleaner));
        return strategy::stop;
    }

    /**
     * Start watching a file for changes.
     *
     * @param path path to watch
     * @param updater consumer that reads the file content and updates properties
     * @param cleaner runnable to clean the properties (in case file is deleted)
     * @return runnable to stop the file watcher
     */
    public static Runnable watch(Path path, Consumer<Path> updater, Consumer<Path> cleaner) {
        if (NativeImageHelper.isBuildTime()) {
            LOGGER.info("File watching is not enabled in native image build time. Path: " + path);
        }
        FileSystemWatcher watcher = FileSystemWatcher.builder()
                .executor(EXECUTOR.get())
                .build();

        watcher.start(path, event -> {
            try {
                if (event.type() == ChangeEventType.DELETED) {
                    cleaner.accept(event.target());
                } else {
                    updater.accept(event.target());
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to process change watcher event " + event
                        + " for file " + path.toAbsolutePath(), e);
            }
        });
        return watcher::stop;
    }

    private static class PathPolled implements PollingStrategy.Polled {
        private final Path path;
        private final Consumer<Path> updater;
        private final Consumer<Path> cleaner;

        private boolean exists;
        private Instant lastChange;

        private PathPolled(Path path,
                           Consumer<Path> updater,
                           Consumer<Path> cleaner) {

            this.path = path;
            this.updater = updater;
            this.cleaner = cleaner;
            this.exists = Files.exists(path);
            if (exists) {
                try {
                    this.lastChange = Files.getLastModifiedTime(path).toInstant();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public ChangeEventType poll(Instant when) {
            try {
                return doPoll();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to poll for changes at " + when, e);
                return ChangeEventType.CHANGED;
            }
        }

        private ChangeEventType doPoll() {
            if (Files.exists(path)) {
                ChangeEventType response;
                if (exists) {
                    // existed and exists now, let's see if modified
                    Instant instant = Instant.now();
                    try {
                        instant = Files.getLastModifiedTime(path).toInstant();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to get last modified for " + path.toAbsolutePath(), e);
                    }
                    if (instant.isAfter(this.lastChange)) {
                        this.lastChange = instant;
                        response = ChangeEventType.CHANGED;
                        updater.accept(path);
                    } else {
                        response = ChangeEventType.UNCHANGED;
                    }
                } else {
                    response = ChangeEventType.CREATED;
                    updater.accept(path);
                }
                exists = true;
                return response;
            } else {
                ChangeEventType response;
                if (exists) {
                    response = ChangeEventType.DELETED;
                    cleaner.accept(path);
                } else {
                    response = ChangeEventType.UNCHANGED;
                }
                exists = false;
                return response;
            }
        }
    }
}
