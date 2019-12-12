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

package io.helidon.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.internal.ConfigKeyImpl;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.internal.ObjectNodeBuilderImpl;
import io.helidon.config.internal.OverrideConfigFilter;
import io.helidon.config.internal.ValueNodeImpl;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;

/**
 * Config provider represents initialization context used to create new instance of Config again and again.
 */
class ProviderImpl implements Config.Context {

    private static final Logger LOGGER = Logger.getLogger(ConfigFactory.class.getName());

    private final ConfigMapperManager configMapperManager;
    private final BuilderImpl.ConfigSourceConfiguration configSource;
    private final OverrideSource overrideSource;
    private final List<Function<Config, ConfigFilter>> filterProviders;
    private final boolean cachingEnabled;

    private final Executor changesExecutor;
    private final SubmissionPublisher<ConfigDiff> changesSubmitter;
    private final Flow.Publisher<ConfigDiff> changesPublisher;
    private final boolean keyResolving;
    private final Function<String, List<String>> aliasGenerator;
    private ConfigSourceChangeEventSubscriber configSourceChangeEventSubscriber;

    private ConfigDiff lastConfigsDiff;
    private AbstractConfigImpl lastConfig;
    private OverrideSourceChangeEventSubscriber overrideSourceChangeEventSubscriber;
    private volatile boolean overrideChangeComplete;
    private volatile boolean configChangeComplete;

    @SuppressWarnings("ParameterNumber")
    ProviderImpl(ConfigMapperManager configMapperManager,
                 BuilderImpl.ConfigSourceConfiguration configSource,
                 OverrideSource overrideSource,
                 List<Function<Config, ConfigFilter>> filterProviders,
                 boolean cachingEnabled,
                 Executor changesExecutor,
                 int changesMaxBuffer,
                 boolean keyResolving,
                 Function<String, List<String>> aliasGenerator) {
        this.configMapperManager = configMapperManager;
        this.configSource = configSource;
        this.overrideSource = overrideSource;
        this.filterProviders = Collections.unmodifiableList(filterProviders);
        this.cachingEnabled = cachingEnabled;
        this.changesExecutor = changesExecutor;

        this.lastConfigsDiff = null;
        this.lastConfig = (AbstractConfigImpl) Config.empty();

        this.keyResolving = keyResolving;
        this.aliasGenerator = aliasGenerator;

        changesSubmitter = new RepeatLastEventPublisher<>(changesExecutor, changesMaxBuffer);
        changesPublisher = ConfigHelper.suspendablePublisher(changesSubmitter,
                                                             this::subscribeSources,
                                                             this::cancelSourcesSubscriptions);

        configSourceChangeEventSubscriber = null;
    }

    public AbstractConfigImpl newConfig() {
        lastConfig = build(configSource.compositeSource().load());
        return lastConfig;
    }

    @Override
    public Config reload() {
        rebuild(configSource.compositeSource().load(), true);
        return lastConfig;
    }

    @Override
    public Instant timestamp() {
        return lastConfig.timestamp();
    }

    @Override
    public Config last() {
        return lastConfig;
    }

    private synchronized AbstractConfigImpl build(Optional<ObjectNode> rootNode) {

        // resolve tokens
        rootNode = rootNode.map(this::resolveKeys);
        // filtering
        ChainConfigFilter targetFilter = new ChainConfigFilter();
        // add override filter
        if (!overrideSource.equals(OverrideSources.empty())) {
            OverrideConfigFilter filter = new OverrideConfigFilter(
                    () -> overrideSource.load().orElse(OverrideSource.OverrideData.empty()).data());
            targetFilter.addFilter(filter);
        }
        // factory
        ConfigFactory factory = new ConfigFactory(configMapperManager,
                                                  rootNode.orElseGet(ObjectNode::empty),
                                                  targetFilter,
                                                  this,
                                                  aliasGenerator,
                                                  configSource.allSources());
        AbstractConfigImpl config = factory.config();
        // initialize filters
        initializeFilters(config, targetFilter);
        // caching
        if (cachingEnabled) {
            targetFilter.enableCaching();
        }
        config.initMp();
        return config;
    }

    private ObjectNode resolveKeys(ObjectNode rootNode) {
        Function<String, String> resolveTokenFunction = Function.identity();
        if (keyResolving) {
            Map<String, String> flattenValueNodes = flattenNodes(rootNode);

            if (flattenValueNodes.isEmpty()) {
                return rootNode;
            }

            Map<String, String> tokenValueMap = tokenToValueMap(flattenValueNodes);

            resolveTokenFunction = (token) -> {
                if (token.startsWith("$")) {
                    return tokenValueMap.get(parseTokenReference(token));
                }
                return token;
            };
        }
        return ObjectNodeBuilderImpl.create(rootNode, resolveTokenFunction).build();
    }

    private Map<String, String> flattenNodes(ConfigNode node) {
        return ConfigFactory.flattenNodes(ConfigKeyImpl.of(), node)
                .filter(e -> e.getValue() instanceof ValueNodeImpl)
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> Config.Key.escapeName(((ValueNodeImpl) e.getValue()).get())
                ));
    }

    private Map<String, String> tokenToValueMap(Map<String, String> flattenValueNodes) {
        return flattenValueNodes.keySet()
                .stream()
                .flatMap(this::tokensFromKey)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), t ->
                        flattenValueNodes.compute(Config.Key.unescapeName(t), (k, v) -> {
                            if (v == null) {
                                throw new ConfigException(String.format("Missing token '%s' to resolve.", t));
                            } else if (v.equals("")) {
                                throw new ConfigException(String.format("Missing value in token '%s' definition.", t));
                            } else if (v.startsWith("$")) {
                                throw new ConfigException(String.format(
                                        "Key token '%s' references to a reference in value. A recursive references is not "
                                                + "allowed.",
                                        t));
                            }
                            return v;
                        })));
    }

    private Stream<String> tokensFromKey(String s) {
        String[] tokens = s.split("\\.+(?![^(\\$\\{)]*\\})");
        return Arrays.stream(tokens).filter(t -> t.startsWith("$")).map(ProviderImpl::parseTokenReference);
    }

    private static String parseTokenReference(String token) {
        if (token.startsWith("${") && token.endsWith("}")) {
            return token.substring(2, token.length() - 1);
        } else if (token.startsWith("$")) {
            return token.substring(1);
        }
        return token;
    }

    private synchronized void rebuild(Optional<ObjectNode> objectNode, boolean force) {
        // 1. build new Config
        AbstractConfigImpl newConfig = build(objectNode);
        // 2. for each subscriber fire event on specific node/key - see AbstractConfigImpl.FilteringConfigChangeEventSubscriber
        // 3. fire event
        ConfigDiff configsDiff = ConfigDiff.from(lastConfig, newConfig);
        if (!configsDiff.isEmpty()) {
            lastConfig = newConfig;
            lastConfigsDiff = configsDiff;

            fireLastChangeEvent();
        } else {
            if (force) {
                lastConfig = newConfig;
            }

            LOGGER.log(Level.FINER, "Change event is not fired, there is no change from the last load.");
        }
    }

    private void fireLastChangeEvent() {
        if (lastConfigsDiff != null) {
            LOGGER.log(Level.FINER, String.format("Firing last event %s (again)", lastConfigsDiff));
            changesSubmitter.offer(lastConfigsDiff,
                                   (subscriber, event) -> {
                                       LOGGER.log(Level.FINER,
                                                  String.format("Event %s has not been delivered to %s.", event, subscriber));
                                       return false;
                                   });
        }
    }

    private void subscribeSources() {
        subscribeConfigSource();
        subscribeOverrideSource();
        //check if source has changed - reload
        rebuild(configSource.compositeSource().load(), false);
    }

    private void cancelSourcesSubscriptions() {
        cancelConfigSource();
        cancelOverrideSource();
    }

    private void subscribeConfigSource() {
        configSourceChangeEventSubscriber = new ConfigSourceChangeEventSubscriber();
        configSource.compositeSource().changes().subscribe(configSourceChangeEventSubscriber);
    }

    private void cancelConfigSource() {
        if (configSourceChangeEventSubscriber != null) {
            configSourceChangeEventSubscriber.cancelSubscription();
            configSourceChangeEventSubscriber = null;
        }
    }

    private void subscribeOverrideSource() {
        overrideSourceChangeEventSubscriber = new OverrideSourceChangeEventSubscriber();
        overrideSource.changes().subscribe(overrideSourceChangeEventSubscriber);
    }

    private void cancelOverrideSource() {
        if (overrideSourceChangeEventSubscriber != null) {
            overrideSourceChangeEventSubscriber.cancelSubscription();
            overrideSourceChangeEventSubscriber = null;
        }
    }

    private void initializeFilters(Config config, ChainConfigFilter chain) {
        chain.init(config);

        filterProviders.stream()
                .map(providerFunction -> providerFunction.apply(config))
                .sorted(ConfigUtils.priorityComparator(ConfigFilter.PRIORITY))
                .forEachOrdered(chain::addFilter);

        chain.filterProviders.stream()
                .map(providerFunction -> providerFunction.apply(config))
                .forEachOrdered(filter -> filter.init(config));
   }

    /**
     * Allows to subscribe on changes of specified ConfigSource that causes creation of new Config instances.
     * <p>
     * The publisher repeats the last change event with any new subscriber.
     *
     * @return {@link Flow.Publisher} to be subscribed in. Never returns {@code null}.
     * @see Config#changes()
     * @see Config#onChange(Function)
     */
    public Flow.Publisher<ConfigDiff> changes() {
        return changesPublisher;
    }

    SubmissionPublisher<ConfigDiff> changesSubmitter() {
        return changesSubmitter;
    }

    /**
     * Config filter chain that can combine a collection of {@link ConfigFilter} and wrap them into one config filter.
     */
    static class ChainConfigFilter implements ConfigFilter {

        private final List<Function<Config, ConfigFilter>> filterProviders;
        private boolean cachingEnabled = false;
        private ConcurrentMap<Config.Key, String> valueCache;
        private Config config;

        /**
         * Creates config filter chain from given filters.
         */
        ChainConfigFilter() {
            this.filterProviders = new ArrayList<>();
        }

        @Override
        public void init(Config config) {
            this.config = config;
        }

        void addFilter(ConfigFilter filter) {
            if (cachingEnabled) {
                throw new IllegalStateException("Cannot add new filter to the chain when cache is already enabled.");
            }
            filterProviders.add((config) -> filter);
        }

        void addFilter(Function<Config, ConfigFilter> filterProvider) {
            if (cachingEnabled) {
                throw new IllegalStateException("Cannot add new filter provider to the chain when cache is already enabled.");
            }
            filterProviders.add(filterProvider);
        }

        @Override
        public String apply(Config.Key key, String stringValue) {
            if (cachingEnabled) {
                if (!valueCache.containsKey(key)){
                    String value = proceedFilters(key, stringValue);
                    valueCache.put(key, value);
                    return value;
                }
                return valueCache.get(key);
            } else {
                return proceedFilters(key, stringValue);
            }

        }

        private String proceedFilters(Config.Key key, String stringValue) {
            for (Function<Config, ConfigFilter> configFilterProvider : filterProviders) {
                stringValue = configFilterProvider.apply(config).apply(key, stringValue);
            }
            return stringValue;
        }

        void enableCaching() {
            this.cachingEnabled = true;
            this.valueCache = new ConcurrentHashMap<>();
        }
    }

    /**
     * {@link Flow.Subscriber} implementation to listen on {@link ConfigSource#changes()}.
     */
    private class ConfigSourceChangeEventSubscriber implements Flow.Subscriber<Optional<ObjectNode>> {

        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Optional<ObjectNode> objectNode) {
            ProviderImpl.this.changesExecutor.execute(() -> ProviderImpl.this.rebuild(objectNode, false));
        }

        @Override
        public void onError(Throwable throwable) {
            ProviderImpl.this.changesSubmitter
                    .closeExceptionally(new ConfigException(
                            String.format("'%s' config source changes support has failed. %s",
                                          ProviderImpl.this.configSource.compositeSource().description(),
                                          throwable.getLocalizedMessage()),
                            throwable));
        }

        @Override
        public void onComplete() {
            LOGGER.fine(String.format("'%s' config source changes support has completed.",
                                      ProviderImpl.this.configSource.compositeSource().description()));

            ProviderImpl.this.configChangeComplete = true;

            if (ProviderImpl.this.overrideChangeComplete) {
                ProviderImpl.this.changesSubmitter.close();
            }
        }

        private void cancelSubscription() {
            subscription.cancel();
        }
    }

    /**
     * {@link Flow.Subscriber} implementation to listen on {@link OverrideSource#changes()}.
     */
    private class OverrideSourceChangeEventSubscriber
            implements Flow.Subscriber<Optional<OverrideSource.OverrideData>> {

        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Optional<OverrideSource.OverrideData> overrideData) {
            ProviderImpl.this.changesExecutor.execute(ProviderImpl.this::reload);
        }

        @Override
        public void onError(Throwable throwable) {
            ProviderImpl.this.changesSubmitter
                    .closeExceptionally(new ConfigException(
                            String.format("'%s' override source changes support has failed. %s",
                                          ProviderImpl.this.overrideSource.description(),
                                          throwable.getLocalizedMessage()),
                            throwable));
        }

        @Override
        public void onComplete() {
            LOGGER.fine(String.format("'%s' override source changes support has completed.",
                                      ProviderImpl.this.overrideSource.description()));

            ProviderImpl.this.overrideChangeComplete = true;

            if (ProviderImpl.this.configChangeComplete) {
                ProviderImpl.this.changesSubmitter.close();
            }
        }

        private void cancelSubscription() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }

    /**
     * {@link Flow.Publisher} implementation that allows to repeat the last event for new-subscribers.
     */
    private class RepeatLastEventPublisher<T> extends SubmissionPublisher<T> {

        private RepeatLastEventPublisher(Executor executor, int maxBufferCapacity) {
            super(executor, maxBufferCapacity);
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            super.subscribe(new RepeatLastEventSubscriber<>(subscriber));
            // repeat the last event for new-subscribers
            ProviderImpl.this.fireLastChangeEvent();
        }

    }

    /**
     * {@link Flow.Subscriber} wrapper implementation that allows to repeat the last event for new-subscribers
     * and do NOT repeat same event more than once to same Subscriber.
     *
     * @see RepeatLastEventPublisher
     */
    private static class RepeatLastEventSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> delegate;
        private Flow.Subscription subscription;
        private T lastEvent;

        private RepeatLastEventSubscriber(Flow.Subscriber<? super T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(T event) {
            if (lastEvent == event) { // do NOT repeat same event more than once to same Subscriber
                //missed event must be requested once more
                subscription.request(1);
            } else {
                lastEvent = event;
                delegate.onNext(event);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

    }

}
