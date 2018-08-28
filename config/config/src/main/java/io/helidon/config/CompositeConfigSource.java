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

package io.helidon.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.internal.ConfigThreadFactory;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.internal.ObjectNodeImpl;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

/**
 * A {@link ConfigSource} that wraps an ordered collection of {@code ConfigSource} instances
 * as a single configuration source.
 * <p>
 * The constructor accepts a {@link ConfigSources.MergingStrategy} which it uses
 * to resolve same-named properties loaded from different underlying sources.
 *
 * @see ConfigSources.CompositeBuilder
 * @see ConfigSources.MergingStrategy
 */
class CompositeConfigSource implements ConfigSource {
    //TODO would be possible to extend AbstractConfigSource also by CompositeConfigSource?

    static final ScheduledExecutorService DEFAULT_CHANGES_EXECUTOR_SERVICE =
            Executors.newScheduledThreadPool(0, new ConfigThreadFactory("composite-source"));

    private static final Logger LOGGER = Logger.getLogger(CompositeConfigSource.class.getName());

    private final Map<ConfigSource, Optional<ObjectNode>> lastObjectNodes;
    private final ConfigSources.MergingStrategy mergingStrategy;
    private final String description;
    private final SubmissionPublisher<Optional<ObjectNode>> changesSubmitter;
    private final Flow.Publisher<Optional<ObjectNode>> changesPublisher;
    private final ConfigUtils.ScheduledTask reloadTask;

    private Optional<ObjectNode> lastObjectNode;
    private List<ConfigSourceChangeEventSubscriber> compositeConfigSourcesSubscribers;

    CompositeConfigSource(List<ConfigSource> configSources,
                          ConfigSources.MergingStrategy mergingStrategy,
                          ScheduledExecutorService reloadExecutorService,
                          Duration debounceTimeout,
                          int changesMaxBuffer) {
        this.mergingStrategy = mergingStrategy;

        description = configSources.stream()
                .map(ConfigSource::description)
                .collect(Collectors.joining("->"));

        changesSubmitter = new SubmissionPublisher<>(Runnable::run, //deliver events on current thread
                                                     changesMaxBuffer);
        changesPublisher = ConfigHelper.suspendablePublisher(
                changesSubmitter,
                this::subscribeConfigSourceChangesSubscriptions,
                this::cancelConfigSourceChangesSubscriptions);

        lastObjectNodes = new LinkedHashMap<>(configSources.size());
        configSources.forEach(source -> lastObjectNodes.put(source, Optional.empty()));

        reloadTask = new ConfigUtils.ScheduledTask(reloadExecutorService, this::reload, debounceTimeout);
    }

    private void subscribeConfigSourceChangesSubscriptions() {
        compositeConfigSourcesSubscribers = new LinkedList<>();
        for (ConfigSource source : lastObjectNodes.keySet()) {
            ConfigSourceChangeEventSubscriber subscriber = new ConfigSourceChangeEventSubscriber(source);
            source.changes().subscribe(subscriber);
            compositeConfigSourcesSubscribers.add(subscriber);
        }
    }

    private void cancelConfigSourceChangesSubscriptions() {
        compositeConfigSourcesSubscribers.forEach(ConfigSourceChangeEventSubscriber::cancelSubscription);
        compositeConfigSourcesSubscribers = null;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void init(ConfigContext context) {
        for (ConfigSource configSource : lastObjectNodes.keySet()) {
            configSource.init(context);
        }
    }

    @Override
    public Optional<ObjectNode> load() {
        //load
        for (ConfigSource configSource : lastObjectNodes.keySet()) {
            Optional<ObjectNode> loadedNode = configSource.load()
                    .map(ObjectNodeImpl::wrap)
                    .map(objectNode -> objectNode.initDescription(configSource.description()));
            lastObjectNodes.put(configSource, loadedNode);
        }
        //merge
        lastObjectNode = mergeLoaded();

        return lastObjectNode;
    }

    Optional<ObjectNode> getLastObjectNode() {
        return lastObjectNode;
    }

    List<ConfigSourceChangeEventSubscriber> getCompositeConfigSourcesSubscribers() {
        return compositeConfigSourcesSubscribers;
    }

    private Optional<ObjectNode> mergeLoaded() {
        List<ObjectNode> rootNodes = lastObjectNodes.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (rootNodes.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(mergingStrategy.merge(rootNodes));
        }
    }

    private void scheduleReload(ConfigSource source, Optional<ObjectNode> changedObjectNode) {
        Optional<ObjectNode> originalObjectNode = lastObjectNodes.get(source);

        if (!Objects.equals(originalObjectNode, changedObjectNode)) {
            lastObjectNodes.put(source, changedObjectNode);
            reloadTask.schedule();
        } else {
            LOGGER.log(Level.FINE,
                       String.format("Source data has not changed. Will not try to reload from config source %s.",
                                     source.description()));
        }
    }

    /**
     * Method is executed asynchronously.
     */
    private void reload() {
        try {
            Optional<ObjectNode> newObjectNode = mergeLoaded();

            if (!Objects.equals(lastObjectNode, newObjectNode)) {
                LOGGER.log(Level.FINER, String.format("Config source %s has been reloaded.", description()));

                lastObjectNode = newObjectNode;
                fireChangeEvent();
            } else {
                LOGGER.log(Level.FINE,
                           String.format("Source data has not changed. Will not try to reload from config source %s.",
                                         description()));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                       String.format("Error merging of loaded config sources %s. "
                                             + "New configuration has not been used. Maybe later.",
                                     description()));
            LOGGER.log(Level.CONFIG, String.format("Failing reload of '%s' cause.", description()), ex);
        }
    }

    private void fireChangeEvent() {
        changesSubmitter.offer(lastObjectNode,
                               (subscriber, objectNode) -> {
                                   LOGGER.log(Level.FINER,
                                              String.format("Object node %s has not been delivered to %s.",
                                                            objectNode,
                                                            subscriber));
                                   return false;
                               });
    }

    /**
     * {@inheritDoc}
     * <p>
     * All subscribers are notified on a same thread provided by
     * {@link ConfigSources.CompositeBuilder#changesExecutor(ScheduledExecutorService) changes executor service}.
     */
    @Override
    public Flow.Publisher<Optional<ObjectNode>> changes() {
        return changesPublisher;
    }

    /**
     * Composite {@link Flow.Subscriber} that is used to subscribe on each {@link ConfigSource} the Composite ConfigSource
     * delegates to.
     */
    private class ConfigSourceChangeEventSubscriber implements Flow.Subscriber<Optional<ObjectNode>> {

        private final ConfigSource configSource;
        private Flow.Subscription subscription;

        private ConfigSourceChangeEventSubscriber(ConfigSource configSource) {
            this.configSource = configSource;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;

            subscription.request(1);
        }

        @Override
        public void onNext(Optional<ObjectNode> objectNode) {
            LOGGER.fine(String.format("'%s' config source has changed: %s", configSource, objectNode));

            CompositeConfigSource.this.scheduleReload(configSource, objectNode);

            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            CompositeConfigSource.this.changesSubmitter
                    .closeExceptionally(new ConfigException(
                            String.format("'%s' config source changes support has failed. %s",
                                          configSource.description(),
                                          throwable.getLocalizedMessage()),
                            throwable));

            //TODO whenever the last completed/error we should call: CompositeConfigSource.this.changesPublisher.close()
        }

        @Override
        public void onComplete() {
            LOGGER.fine(String.format("'%s' config source changes support has completed.", configSource.description()));

            //TODO whenever the last completed/error we should call: CompositeConfigSource.this.changesPublisher.close()
        }

        private void cancelSubscription() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }

}
