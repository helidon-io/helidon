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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.OverrideSource.OverrideData;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

class OverrideSourceRuntime {
    private static final Logger LOGGER = Logger.getLogger(OverrideSourceRuntime.class.getName());

    private final OverrideReloader reloader;
    private final Runnable changesRunnable;
    private final OverrideSource source;
    // we only want to start change support if changes are supported by the source
    private final boolean changesSupported;
    // the data used by filter to retrieve override data
    private final AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData = new AtomicReference<>(List.of());
    // reference to change listener (the change listening is started after construction of this class)
    private final AtomicReference<Runnable> changeListener = new AtomicReference<>();

    // set to true if changes started
    private boolean changesStarted = false;
    // set to true when the content is loaded (to start changes whether the registration for change is before or after load)
    private boolean dataLoaded = false;

    @SuppressWarnings("unchecked")
    OverrideSourceRuntime(OverrideSource overrideSource) {
        this.source = overrideSource;

        // content source
        AtomicReference<Object> lastStamp = new AtomicReference<>();
        this.reloader = new OverrideReloader(lastStamp, overrideSource);

        // change support
        boolean changesSupported = false;
        Runnable changesRunnable = null;

        if (overrideSource instanceof WatchableSource) {
            WatchableSource<Object> watchable = (WatchableSource<Object>) source;
            Optional<ChangeWatcher<Object>> changeWatcher = watchable.changeWatcher();

            if (changeWatcher.isPresent()) {
                changesSupported = true;
                changesRunnable = new WatchableChangesStarter(
                        lastData,
                        reloader,
                        source,
                        watchable,
                        changeWatcher.get(),
                        changeListener);
            }
        }

        if (!changesSupported && (overrideSource instanceof PollableSource)) {
            PollableSource<Object> pollable = (PollableSource<Object>) source;
            Optional<PollingStrategy> pollingStrategy = pollable.pollingStrategy();

            if (pollingStrategy.isPresent()) {
                changesSupported = true;
                changesRunnable = new PollingStrategyStarter(
                        lastData,
                        reloader,
                        source,
                        pollable,
                        pollingStrategy.get(),
                        lastStamp,
                        changeListener);
            }
        }

        this.changesRunnable = changesRunnable;
        this.changesSupported = changesSupported;
    }

    // for testing purposes
    static OverrideSourceRuntime empty() {
        return new OverrideSourceRuntime(OverrideSources.empty());
    }

    // this happens once per config
    void addFilter(ProviderImpl.ChainConfigFilter targetFilter) {
        if (!dataLoaded) {
            initialLoad();
        }
        if (!source.equals(OverrideSources.empty())) {
            // we need to have a single set of data for a single config
            var data = lastData.get();
            OverrideConfigFilter filter = new OverrideConfigFilter(() -> data);
            targetFilter.addFilter(filter);
        }
    }

    void startChanges() {
        if (!changesStarted && dataLoaded && changesSupported) {
            changesStarted = true;
            changesRunnable.run();
        }
    }

    @Override
    public String toString() {
        return "Runtime for " + source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        OverrideSourceRuntime that = (OverrideSourceRuntime) o;
        return source.equals(that.source);
    }

    void initialLoad() {
        synchronized (source) {
            if (dataLoaded) {
                throw new ConfigException("Attempting to load a single override source multiple times. This is a bug");
            }

            Optional<OverrideData> loadedData = source.retryPolicy()
                    .map(policy -> policy.execute(reloader))
                    .orElseGet(reloader);

            if (loadedData.isEmpty() && !source.optional()) {
                throw new ConfigException("Cannot load data from mandatory source: " + source);
            }

            // initial data do not trigger a change notification
            lastData.set(loadedData.map(OverrideData::data).orElseGet(List::of));

            dataLoaded = true;
        }
    }

    public String description() {
        return source.description();
    }

    private static void setData(AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData,
                                Optional<OverrideData> data,
                                AtomicReference<Runnable> changeListener) {
        lastData.set(data.map(OverrideData::data).orElseGet(List::of));

        Runnable runnable = changeListener.get();
        if (null == runnable) {
            LOGGER.finest("Wrong order - change triggered before a change listener is registered in "
                                  + OverrideSourceRuntime.class.getName());
        } else {
            runnable.run();
        }
    }

    void changeListener(Runnable listener) {
        this.changeListener.set(listener);
    }

    private static final class PollingStrategyStarter implements Runnable {
        private final PollingStrategy pollingStrategy;
        private final PollingStrategyListener listener;

        private PollingStrategyStarter(AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData,
                                       OverrideReloader reloader,
                                       OverrideSource source,
                                       PollableSource<Object> pollable,
                                       PollingStrategy pollingStrategy,
                                       AtomicReference<Object> lastStamp,
                                       AtomicReference<Runnable> changeListener) {

            this.pollingStrategy = pollingStrategy;
            this.listener = new PollingStrategyListener(lastData, reloader, source, pollable, lastStamp, changeListener);
        }

        @Override
        public void run() {
            pollingStrategy.start(listener);
        }
    }

    private static final class PollingStrategyListener implements PollingStrategy.Polled {

        private final AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData;
        private final Supplier<Optional<OverrideData>> reloader;
        private final OverrideSource source;
        private final PollableSource<Object> pollable;
        private final AtomicReference<Object> lastStamp;
        private final AtomicReference<Runnable> changeListener;

        private PollingStrategyListener(AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData,
                                        OverrideReloader reloader,
                                        OverrideSource source,
                                        PollableSource<Object> pollable,
                                        AtomicReference<Object> lastStamp,
                                        AtomicReference<Runnable> changeListener) {

            this.lastData = lastData;
            this.reloader = reloader;
            this.source = source;
            this.pollable = pollable;
            this.lastStamp = lastStamp;
            this.changeListener = changeListener;
        }

        @Override
        public ChangeEventType poll(Instant when) {
            Object lastStampValue = lastStamp.get();

            synchronized (pollable) {
                if ((null == lastStampValue) || pollable.isModified(lastStampValue)) {
                    Optional<OverrideData> overrideData = reloader.get();
                    if (overrideData.isEmpty()) {
                        if (source.optional()) {
                            // this is a valid change
                            setData(lastData, overrideData, changeListener);
                        } else {
                            LOGGER.info("Mandatory config source is not available, ignoring change.");
                        }
                        return ChangeEventType.DELETED;
                    } else {
                        setData(lastData, overrideData, changeListener);
                        return ChangeEventType.CHANGED;
                    }
                }
            }
            return ChangeEventType.UNCHANGED;
        }
    }

    private static final class WatchableChangesStarter implements Runnable {
        private final WatchableSource<Object> watchable;
        private final WatchableListener listener;
        private final ChangeWatcher<Object> changeWatcher;

        private WatchableChangesStarter(AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData,
                                        OverrideReloader reloader,
                                        OverrideSource source,
                                        WatchableSource<Object> watchable,
                                        ChangeWatcher<Object> changeWatcher,
                                        AtomicReference<Runnable> changeListener) {
            this.watchable = watchable;
            this.changeWatcher = changeWatcher;
            this.listener = new WatchableListener(lastData, reloader, source, changeListener);
        }

        @Override
        public void run() {
            Object target = watchable.target();
            changeWatcher.start(target, listener);
        }
    }

    private static final class WatchableListener implements Consumer<ChangeWatcher.ChangeEvent<Object>> {
        private final AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData;
        private final OverrideReloader reloader;
        private final OverrideSource source;
        private final AtomicReference<Runnable> changeListener;

        private WatchableListener(AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> lastData,
                                  OverrideReloader reloader,
                                  OverrideSource source,
                                  AtomicReference<Runnable> changeListener) {

            this.lastData = lastData;
            this.reloader = reloader;
            this.source = source;
            this.changeListener = changeListener;
        }

        @Override
        public void accept(ChangeWatcher.ChangeEvent<Object> change) {
            try {
                Optional<OverrideData> overrideData = reloader.get();
                if (overrideData.isEmpty()) {
                    if (source.optional()) {
                        // this is a valid change
                        setData(lastData, overrideData, changeListener);
                    } else {
                        LOGGER.info("Mandatory config source is not available, ignoring change.");
                    }
                } else {
                    setData(lastData, overrideData, changeListener);
                }
            } catch (Exception e) {
                LOGGER.info("Failed to reload config source "
                                    + source
                                    + ", exception available in finest log level. "
                                    + "Change that triggered this event: "
                                    + change);
                LOGGER.log(Level.FINEST, "Failed to reload config source", e);
            }
        }
    }

    private static final class OverrideReloader implements Supplier<Optional<OverrideData>> {
        private final AtomicReference<Object> lastStamp;
        private final OverrideSource overrideSource;

        private OverrideReloader(AtomicReference<Object> lastStamp,
                                 OverrideSource overrideSource) {
            this.lastStamp = lastStamp;
            this.overrideSource = overrideSource;
        }

        @Override
        public Optional<OverrideData> get() {
            synchronized (overrideSource) {
                return overrideSource.load()
                        .map(content -> {
                            lastStamp.set(content.stamp().orElse(null));
                            return content.data();
                        });
            }
        }
    }
}
