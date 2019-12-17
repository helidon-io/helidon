/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.spi.PollingStrategy;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

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
public class FilesystemWatchPollingStrategy implements PollingStrategy {

    private static final Logger LOGGER = Logger.getLogger(FilesystemWatchPollingStrategy.class.getName());

    private static final long DEFAULT_RECURRING_INTERVAL = 5;

    private final Path path;
    private final SubmissionPublisher<PollingEvent> ticksSubmitter;
    private final Flow.Publisher<PollingEvent> ticksPublisher;
    private final boolean customExecutor;
    private ScheduledExecutorService executor;
    private WatchService watchService;
    private final List<WatchEvent.Modifier> watchServiceModifiers;

    private WatchKey watchKey;
    private Future<?> watchThreadFuture;

    /**
     * Creates a strategy with watched {@code path} as a parameters.
     *
     * @param path     a watched file
     * @param executor a custom executor or the {@link io.helidon.config.internal.ConfigThreadFactory} is assigned when
     *                 parameter is {@code null}
     */
    public FilesystemWatchPollingStrategy(Path path, ScheduledExecutorService executor) {
        if (executor == null) {
            this.customExecutor = false;
        } else {
            this.customExecutor = true;
            this.executor = executor;
        }

        this.path = path;

        ticksSubmitter = new SubmissionPublisher<>(Runnable::run, //deliver events on current thread
                                                   1); //(almost) do not buffer events
        ticksPublisher = ConfigHelper.suspendablePublisher(ticksSubmitter,
                                                           this::startWatchService,
                                                           this::stopWatchService);

        watchServiceModifiers = new LinkedList<>();
    }

    /**
     * Configured path.
     *
     * @return configured path
     */
    public Path path() {
        return path;
    }

    @Override
    public Flow.Publisher<PollingEvent> ticks() {
        return ticksPublisher;
    }

    private void fireEvent(WatchEvent<?> watchEvent) {
        ticksSubmitter().offer(
                PollingEvent.now(),
                (subscriber, pollingEvent) -> {
                    LOGGER.log(Level.FINER, String.format("Event %s has not been delivered to %s.", pollingEvent, subscriber));
                    return false;
                });
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

    void startWatchService() {
        if (!customExecutor) {
            executor = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("file-watch-polling"));
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new ConfigException("Cannot obtain WatchService.", e);
        }
        CountDownLatch latch = new CountDownLatch(1);
        watchThreadFuture = executor.scheduleWithFixedDelay(new Monitor(path, latch, watchServiceModifiers),
                                                            0,
                                                            DEFAULT_RECURRING_INTERVAL,
                                                            TimeUnit.SECONDS);
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new ConfigException("Thread which is supposed to register to watch service exceeded the limit 1s.", e);
        }
    }

    void stopWatchService() {
        if (watchKey != null) {
            watchKey.cancel();
        }
        if (watchThreadFuture != null) {
            watchThreadFuture.cancel(true);
        }
        if (!customExecutor) {
            ConfigUtils.shutdownExecutor(executor);
            executor = null;
        }
    }

    private class Monitor implements Runnable {

        private final Path path;
        private final CountDownLatch latch;
        private final List<WatchEvent.Modifier> watchServiceModifiers;
        private boolean fail;

        private Monitor(Path path, CountDownLatch latch, List<WatchEvent.Modifier> watchServiceModifiers) {
            this.path = path;
            this.latch = latch;
            this.watchServiceModifiers = watchServiceModifiers;
        }

        @Override
        public void run() {
            Path dir = path.getParent();
            try {
                register();
                if (fail) {
                    FilesystemWatchPollingStrategy.this.fireEvent(null);
                    fail = false;
                }
            } catch (Exception e) {
                fail = true;
                LOGGER.log(Level.FINE, "Cannot register to watch service.", e);
                return;
            } finally {
                latch.countDown();
            }

            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (Exception ie) {
                    fail = true;
                    LOGGER.log(Level.FINE, ie, () -> "Watch service on '" + dir + "' directory interrupted.");
                    break;
                }
                List<WatchEvent<?>> events = key.pollEvents();
                events.stream()
                        .filter(e -> FilesystemWatchPollingStrategy.this.path.endsWith((Path) e.context()))
                        .forEach(FilesystemWatchPollingStrategy.this::fireEvent);

                if (!key.reset()) {
                    fail = true;
                    LOGGER.log(Level.FINE, () -> "Directory '" + dir + "' is no more valid to be watched.");
                    FilesystemWatchPollingStrategy.this.fireEvent(null);
                    break;
                }
            }
        }

        private void register() throws IOException {
            Path target = target(path);
            Path dir = parentDir(target);
            WatchKey oldWatchKey = watchKey;
            watchKey = dir.register(watchService,
                                    List.of(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
                                            .toArray(new WatchEvent.Kind[0]),
                                    watchServiceModifiers.toArray(new WatchEvent.Modifier[0]));
            if (oldWatchKey != null) {
                oldWatchKey.cancel();
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
                        String.format("Cannot find parent directory for '%s' to register watch service.", path.toString()));
            }
            return parent;
        }

    }

    SubmissionPublisher<PollingEvent> ticksSubmitter() {
        return ticksSubmitter;
    }

    Future<?> watchThreadFuture() {
        return watchThreadFuture;
    }

    @Override
    public String toString() {
        return "FilesystemWatchPollingStrategy{"
                + "path=" + path
                + '}';
    }
}
