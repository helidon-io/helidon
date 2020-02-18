/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * The runtime of a config source. For a single {@link io.helidon.config.Config}, there is one source runtime for each configured
 * config source.
 *
 */
public class ConfigSourceRuntimeImpl extends ConfigSourceRuntimeBase implements org.eclipse.microprofile.config.spi.ConfigSource {
    private static final Logger LOGGER = Logger.getLogger(ConfigSourceRuntimeImpl.class.getName());

    private final List<BiConsumer<String, ConfigNode>> listeners = new LinkedList<>();
    private final BuilderImpl.ConfigContextImpl configContext;
    private final ConfigSource configSource;
    private final boolean changesSupported;
    private final Supplier<Optional<ObjectNode>> reloader;
    private final Runnable changesRunnable;
    private final Function<String, Optional<ConfigNode>> singleNodeFunction;
    private final boolean isLazy;

    // we only want to start change support if somebody listens for changes
    private boolean changesWanted = false;
    // set to true if changes started
    private boolean changesStarted = false;
    // set to true when the content is loaded (to start changes whether the registration for change is before or after load)
    private boolean dataLoaded = false;

    // for eager sources, this is the data we get initially, everything else is handled through change listeners
    private Optional<ObjectNode> initialData;
    private Map<String, ConfigNode> loadedData;
    private Map<String, String> mpData;

    @SuppressWarnings("unchecked")
    ConfigSourceRuntimeImpl(BuilderImpl.ConfigContextImpl configContext, ConfigSource source) {
        this.configContext = configContext;
        this.configSource = source;

        Supplier<Optional<ObjectNode>> reloader;
        Function<String, Optional<ConfigNode>> singleNodeFunction;
        boolean lazy = false;

        // content source
        AtomicReference<Object> lastStamp = new AtomicReference<>();
        if (configSource instanceof ParsableSource) {
            // eager parsable config source
            reloader = new ParsableConfigSourceReloader(configContext, (ParsableSource) source, lastStamp);
            singleNodeFunction = objectNodeToSingleNode();
        } else if (configSource instanceof NodeConfigSource) {
            // eager node config source
            reloader = new NodeConfigSourceReloader((NodeConfigSource) source, lastStamp);
            singleNodeFunction = objectNodeToSingleNode();
        } else if (configSource instanceof LazyConfigSource) {
            LazyConfigSource lazySource = (LazyConfigSource) source;
            // lazy config source
            reloader = Optional::empty;
            singleNodeFunction = lazySource::node;
            lazy = true;
        } else {
            throw new ConfigException("Config source " + source + ", class: " + source.getClass().getName() + " does not "
                                              + "implement any of required interfaces. A config source must at least "
                                              + "implement one of the following: ParsableSource, or NodeConfigSource, or "
                                              + "LazyConfigSource");
        }

        this.isLazy = lazy;
        this.reloader = reloader;
        this.singleNodeFunction = singleNodeFunction;

        // change support
        boolean changesSupported = false;
        Runnable changesRunnable = null;

        if (configSource instanceof WatchableSource) {
            WatchableSource<Object> watchable = (WatchableSource<Object>) source;
            Optional<ChangeWatcher<Object>> changeWatcher = watchable.changeWatcher();

            if (changeWatcher.isPresent()) {
                changesStarted = true;
                changesSupported = true;
                changesRunnable = new WatchableChangesStarter(configContext,
                                                              listeners,
                                                              reloader,
                                                              source,
                                                              watchable,
                                                              changeWatcher.get());
            }
        }

        if (!changesSupported && (configSource instanceof PollableSource)) {
            PollableSource<Object> pollable = (PollableSource<Object>) source;
            Optional<PollingStrategy> pollingStrategy = pollable.pollingStrategy();

            if (pollingStrategy.isPresent()) {
                changesStarted = true;
                changesSupported = true;
                changesRunnable = new PollingStrategyStarter(configContext,
                                                             listeners,
                                                             reloader,
                                                             source,
                                                             pollable,
                                                             pollingStrategy.get(),
                                                             lastStamp);
            }
        }

        if (!changesSupported && (configSource instanceof EventConfigSource)) {
            EventConfigSource event = (EventConfigSource) source;
            changesStarted = true;
            changesSupported = true;
            changesRunnable = () -> event.onChange((key, config) -> listeners.forEach(it -> it.accept(key, config)));
        }

        this.changesRunnable = changesRunnable;
        this.changesSupported = changesSupported;
    }

    @Override
    public synchronized void onChange(BiConsumer<String, ConfigNode> change) {
        if (!changesSupported) {
            return;
        }

        this.listeners.add(change);
        this.changesWanted = true;
        startChanges();
    }

    @Override
    public synchronized Optional<ObjectNode> load() {
        if (dataLoaded) {
            throw new ConfigException("Attempting to load a single config source multiple times. This is a bug.");
        }

        initialLoad();

        return this.initialData;
    }

    @Override
    public boolean isLazy() {
        return isLazy;
    }

    @Override
    boolean changesSupported() {
        return changesSupported;
    }

    @Override
    public String toString() {
        return "Runtime for " + configSource;
    }

    @Override
    public int hashCode() {
        return Objects.hash(configSource);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        ConfigSourceRuntimeImpl that = (ConfigSourceRuntimeImpl) o;
        return configSource.equals(that.configSource);
    }

    private synchronized void initialLoad() {
        if (dataLoaded) {
            return;
        }

        configSource.init(configContext);

        Optional<ObjectNode> loadedData = configSource.retryPolicy()
                .map(policy -> policy.execute(reloader))
                .orElseGet(reloader);

        // we may have media type mapping per node configured as well
        if (configSource instanceof AbstractConfigSource) {
            loadedData = loadedData.map(it -> ((AbstractConfigSource) configSource)
                    .processNodeMapping(configContext::findParser, ConfigKeyImpl.of(), it));
        }

        this.initialData = loadedData;

        this.loadedData = new HashMap<>();
        mpData = new HashMap<>();

        initialData.ifPresent(data -> {
            Map<ConfigKeyImpl, ConfigNode> keyToNodeMap = ConfigHelper.createFullKeyToNodeMap(data);

            keyToNodeMap.forEach((key, value) -> {
                Optional<String> directValue = value.value();
                directValue.ifPresent(stringValue -> mpData.put(key.toString(), stringValue));
                this.loadedData.put(key.toString(), value);
            });
        });

        dataLoaded = true;
        startChanges();
    }

    @Override
    public Optional<ConfigNode> node(String key) {
        return singleNodeFunction.apply(key);
    }

    @Override
    public org.eclipse.microprofile.config.spi.ConfigSource asMpSource() {
        return this;
    }

    @Override
    public String description() {
        return configSource.description();
    }

    /*
     * MP Config related methods
     */

    @Override
    public Map<String, String> getProperties() {
        initialLoad();
        return new HashMap<>(mpData);
    }

    @Override
    public String getValue(String propertyName) {
        initialLoad();
        return mpData.get(propertyName);
    }

    @Override
    public String getName() {
        return configSource.description();
    }

    /*
     Runtime impl base
     */

    @Override
    boolean isSystemProperties() {
        return configSource instanceof ConfigSources.SystemPropertiesConfigSource;
    }

    @Override
    boolean isEnvironmentVariables() {
        return configSource instanceof ConfigSources.EnvironmentVariablesConfigSource;
    }

    private Function<String, Optional<ConfigNode>> objectNodeToSingleNode() {
        return key -> {
            if (null == loadedData) {
                throw new IllegalStateException("Single node of an eager source requested before load method was called."
                                                        + " This is a bug.");
            }

            return Optional.ofNullable(loadedData.get(key));
        };
    }

    private void startChanges() {
        if (!changesStarted && dataLoaded && changesWanted) {
            changesStarted = true;
            changesRunnable.run();
        }
    }

    private static void triggerChanges(BuilderImpl.ConfigContextImpl configContext,
                                       List<BiConsumer<String, ConfigNode>> listeners,
                                       Optional<ObjectNode> objectNode) {

        configContext.changesExecutor()
                .execute(() -> {
                    for (BiConsumer<String, ConfigNode> listener : listeners) {
                        listener.accept("", objectNode.orElse(ObjectNode.empty()));
                    }
                });

    }

    private static final class PollingStrategyStarter implements Runnable {
        private final PollingStrategy pollingStrategy;
        private final PollingStrategyListener listener;

        private PollingStrategyStarter(BuilderImpl.ConfigContextImpl configContext,
                                       List<BiConsumer<String, ConfigNode>> listeners,
                                       Supplier<Optional<ObjectNode>> reloader,
                                       ConfigSource source,
                                       PollableSource<Object> pollable,
                                       PollingStrategy pollingStrategy,
                                       AtomicReference<Object> lastStamp) {

            this.pollingStrategy = pollingStrategy;
            this.listener = new PollingStrategyListener(configContext, listeners, reloader, source, pollable, lastStamp);
        }

        @Override
        public void run() {
            pollingStrategy.start(listener);
        }
    }

    private static final class PollingStrategyListener implements PollingStrategy.Polled {

        private final BuilderImpl.ConfigContextImpl configContext;
        private final List<BiConsumer<String, ConfigNode>> listeners;
        private final Supplier<Optional<ObjectNode>> reloader;
        private final ConfigSource source;
        private final PollableSource<Object> pollable;
        private AtomicReference<Object> lastStamp;

        public PollingStrategyListener(BuilderImpl.ConfigContextImpl configContext,
                                       List<BiConsumer<String, ConfigNode>> listeners,
                                       Supplier<Optional<ObjectNode>> reloader,
                                       ConfigSource source,
                                       PollableSource<Object> pollable,
                                       AtomicReference<Object> lastStamp) {

            this.configContext = configContext;
            this.listeners = listeners;
            this.reloader = reloader;
            this.source = source;
            this.pollable = pollable;
            this.lastStamp = lastStamp;
        }

        @Override
        public ChangeEventType poll(Instant when) {
            Object lastStampValue = lastStamp.get();
            if (null == lastStampValue || pollable.isModified(lastStampValue)) {
                Optional<ObjectNode> objectNode = reloader.get();
                if (objectNode.isEmpty()) {
                    if (source.optional()) {
                        // this is a valid change
                        triggerChanges(configContext, listeners, objectNode);
                    } else {
                        LOGGER.info("Mandatory config source is not available, ignoring change.");
                    }
                    return ChangeEventType.DELETED;
                } else {
                    triggerChanges(configContext, listeners, objectNode);
                    return ChangeEventType.CHANGED;
                }
            }
            return ChangeEventType.UNCHANGED;
        }
    }

    private static final class WatchableChangesStarter implements Runnable {
        private final WatchableSource<Object> watchable;
        private final WatchableListener listener;
        private final ChangeWatcher<Object> changeWatcher;

        private WatchableChangesStarter(BuilderImpl.ConfigContextImpl configContext,
                                        List<BiConsumer<String, ConfigNode>> listeners,
                                        Supplier<Optional<ObjectNode>> reloader,
                                        ConfigSource configSource,
                                        WatchableSource<Object> watchable,
                                        ChangeWatcher<Object> changeWatcher) {
            this.watchable = watchable;
            this.changeWatcher = changeWatcher;
            this.listener = new WatchableListener(configContext, listeners, reloader, configSource);
        }

        @Override
        public void run() {
            Object target = watchable.target();
            changeWatcher.start(target, listener);
        }
    }

    private static final class WatchableListener implements Consumer<ChangeWatcher.ChangeEvent<Object>> {
        private final BuilderImpl.ConfigContextImpl configContext;
        private final List<BiConsumer<String, ConfigNode>> listeners;
        private final Supplier<Optional<ObjectNode>> reloader;
        private final ConfigSource configSource;

        private WatchableListener(BuilderImpl.ConfigContextImpl configContext,
                                  List<BiConsumer<String, ConfigNode>> listeners,
                                  Supplier<Optional<ObjectNode>> reloader,
                                  ConfigSource configSource) {

            this.configContext = configContext;
            this.listeners = listeners;
            this.reloader = reloader;
            this.configSource = configSource;
        }

        @Override
        public void accept(ChangeWatcher.ChangeEvent<Object> change) {
            try {
                Optional<ObjectNode> objectNode = reloader.get();
                if (objectNode.isEmpty()) {
                    if (configSource.optional()) {
                        // this is a valid change
                        triggerChanges(configContext, listeners, objectNode);
                    } else {
                        LOGGER.info("Mandatory config source is not available, ignoring change.");
                    }
                } else {
                    triggerChanges(configContext, listeners, objectNode);
                }
            } catch (Exception e) {
                LOGGER.info("Failed to reload config source "
                                    + configSource
                                    + ", exception available in finest log level. "
                                    + "Change that triggered this event: "
                                    + change);
                LOGGER.log(Level.FINEST, "Failed to reload config source", e);
            }
        }
    }

    private static final class NodeConfigSourceReloader implements Supplier<Optional<ObjectNode>> {
        private final NodeConfigSource configSource;
        private final AtomicReference<Object> lastStamp;

        private NodeConfigSourceReloader(NodeConfigSource configSource,
                                         AtomicReference<Object> lastStamp) {
            this.configSource = configSource;
            this.lastStamp = lastStamp;
        }

        @Override
        public Optional<ObjectNode> get() {
            return configSource.load()
                    .map(content -> {
                        lastStamp.set(content.stamp().orElse(null));
                        return content.data();
                    });
        }
    }

    private static final class ParsableConfigSourceReloader implements Supplier<Optional<ObjectNode>> {
        private final BuilderImpl.ConfigContextImpl configContext;
        private final ParsableSource configSource;
        private final AtomicReference<Object> lastStamp;

        private ParsableConfigSourceReloader(BuilderImpl.ConfigContextImpl configContext,
                                             ParsableSource configSource,
                                             AtomicReference<Object> lastStamp) {

            this.configContext = configContext;
            this.configSource = configSource;
            this.lastStamp = lastStamp;
        }

        @Override
        public Optional<ObjectNode> get() {
            return configSource.load()
                    .map(content -> {
                        lastStamp.set(content.stamp().orElse(null));
                        Optional<ConfigParser> parser = configSource.parser();

                        if (parser.isPresent()) {
                            return parser.get().parse(content);
                        }

                        // media type should either be configured on config source, or in content
                        Optional<String> mediaType = configSource.mediaType().or(content::mediaType);

                        if (mediaType.isPresent()) {
                            parser = configContext.findParser(mediaType.get());
                            if (parser.isEmpty()) {
                                throw new ConfigException("Could not find parser for media type " + mediaType.get());
                            }
                            return parser.get().parse(content);
                        }

                        throw new ConfigException("Could not find media type of config source " + configSource);
                    });
        }
    }
}
