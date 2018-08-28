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

package io.helidon.config.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.PollingStrategies;
import io.helidon.config.RetryPolicies;
import io.helidon.config.internal.ConfigThreadFactory;

/**
 * Abstract base implementation for a variety of sources.
 * <p>
 * The inner {@link Builder} class is ready-to-extend with built-in support of
 * changes (polling strategy, executor, buffer size) and mandatory/optional
 * attribute.
 *
 * @param <T> a type of source data
 * @param <S> a type of data stamp
 */
public abstract class AbstractSource<T, S> implements Source<T> {

    private static final Logger LOGGER = Logger.getLogger(AbstractSource.class.getName());

    private final boolean mandatory;
    private final PollingStrategy pollingStrategy;
    private final Executor changesExecutor;
    private final RetryPolicy retryPolicy;
    private final SubmissionPublisher<Optional<T>> changesSubmitter;
    private final Flow.Publisher<Optional<T>> changesPublisher;
    private Optional<Data<T, S>> lastData;
    private PollingEventSubscriber pollingEventSubscriber;

    AbstractSource(Builder<?, ?, ?> builder) {
        mandatory = builder.isMandatory();
        pollingStrategy = builder.getPollingStrategy();
        changesExecutor = builder.getChangesExecutor();
        retryPolicy = builder.getRetryPolicy();
        changesSubmitter = new SubmissionPublisher<>(changesExecutor, builder.getChangesMaxBuffer());
        changesPublisher = ConfigHelper.suspendablePublisher(changesSubmitter,
                                                             this::subscribePollingStrategy,
                                                             this::cancelPollingStrategy);
        lastData = Optional.empty();
    }

    /**
     * Reloads the source and fires an event if the data was changed.
     */
    void reload() {
        LOGGER.log(Level.FINEST, "reload");

        boolean hasChanged = false;
        // find new data
        Optional<Data<T, S>> newData = loadDataChangedSinceLastLoad();

        if (newData.isPresent()) { // something has changed
            Optional<T> newObjectNode = newData.get().data();
            if (lastData.isPresent()) {
                Optional<T> lastObjectNode = lastData.get().data();
                hasChanged = hasChanged(lastObjectNode, newObjectNode);
            } else {
                hasChanged = true;
            }
            lastData = newData;
        }

        // fire event
        if (hasChanged) {
            fireChangeEvent();
        } else {
            LOGGER.log(Level.FINE, String.format("Source data %s has not changed.", description()));
        }
    }

    SubmissionPublisher<Optional<T>> getChangesSubmitter() {
        return changesSubmitter;
    }

    void subscribePollingStrategy() {
        pollingEventSubscriber = new PollingEventSubscriber();
        pollingStrategy.ticks().subscribe(pollingEventSubscriber);
    }

    /**
     * Returns universal id of source to be used to construct {@link #description()}.
     *
     * @return universal id of source
     */
    protected String uid() {
        return "";
    }

    void cancelPollingStrategy() {
        pollingEventSubscriber.cancelSubscription();
        pollingEventSubscriber = null;
    }

    Flow.Publisher<Optional<T>> getChangesPublisher() {
        return changesPublisher;
    }

    PollingStrategy getPollingStrategy() {
        return pollingStrategy;
    }

    protected boolean isMandatory() {
        return mandatory;
    }

    /**
     * Fires a change event when source has changed.
     */
    protected void fireChangeEvent() {
        changesSubmitter.offer(lastData.flatMap(Data::data),
                               (subscriber, event) -> {
                                   LOGGER.log(Level.FINER,
                                              String.format("Event %s has not been delivered to %s.", event, subscriber));
                                   return false;
                               });
    }

    /**
     * Performs any postprocessing of config data after loading.
     * By default, the method simply returns the provided input {@code Data}.
     *
     * @param data an input data
     * @return a post-processed data
     */
    protected Data<T, S> processLoadedData(Data<T, S> data) {
        return data;
    }

    /**
     * Returns current stamp of data in config source.
     *
     * @return current datastamp of data in config source
     */
    protected abstract Optional<S> dataStamp();

    Optional<Data<T, S>> getLastData() {
        return lastData;
    }

    /**
     * Loads data from source when {@code data} expires.
     *
     * @return the last loaded data
     */
    @Override
    public final Optional<T> load() {
        Optional<Data<T, S>> loadedData = loadDataChangedSinceLastLoad();
        if (loadedData.isPresent()) {
            lastData = loadedData;
        }
        if (lastData.isPresent()) {
            return lastData.get().data();
        } else {
            return Optional.empty();
        }
    }

    Optional<Data<T, S>> loadDataChangedSinceLastLoad() {
        Optional<S> lastDatastamp = lastData.flatMap(Data::stamp);
        Optional<S> dataStamp = dataStamp();

        if (!lastData.isPresent() || !dataStamp.equals(lastData.get().stamp())) {
            LOGGER.log(Level.FINE,
                       String.format("Source %s has changed to %s from %s.", description(), dataStamp,
                                     lastDatastamp));
            try {
                Data<T, S> data = retryPolicy.execute(this::loadData);
                if (!data.stamp().equals(lastDatastamp)) {
                    LOGGER.log(Level.FINE,
                               String.format("Source %s has changed to %s from %s.", description(), dataStamp,
                                             lastDatastamp));
                    return Optional.of(processLoadedData(data));
                } else {
                    LOGGER.log(Level.FINE,
                               String.format("Config data %s has not changed, last stamp was %s.", description(), lastDatastamp));
                }
            } catch (ConfigException ex) {
                processMissingData(ex);

                if (lastData.isPresent() && lastData.get().data().isPresent()) {
                    LOGGER.log(Level.FINE,
                               String.format("Config data %s has has been removed.", description()));
                    return Optional.of(new Data<>(Optional.empty(), Optional.empty()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Loads new data from config source.
     *
     * @return newly loaded data with appropriate data timestamp used for future method calls
     * @throws ConfigException in case it is not possible to load configuration data
     */
    protected abstract Data<T, S> loadData() throws ConfigException;

    /**
     * An action is proceeded when an attempt to load data failed.
     * <p>
     * Method logs at an appropriate level and possibly throw a wrapped exception.
     *
     * @param cause an original exception
     */
    private void processMissingData(ConfigException cause) {
        if (isMandatory()) {
            String message = String.format("Cannot load data from mandatory source %s.", description());
            if (cause == null) {
                throw new ConfigException(message);
            } else {
                throw new ConfigException(message + " " + cause.getLocalizedMessage(), cause);
            }
        } else {
            String message = String.format("Cannot load data from optional source %s."
                                                   + " Will not be used to load from.",
                                           description());
            if (cause == null) {
                LOGGER.log(Level.CONFIG, message);
            } else {
                if (cause instanceof ConfigParserException) {
                    LOGGER.log(Level.WARNING, message + " " + cause.getLocalizedMessage());
                } else {
                    LOGGER.log(Level.CONFIG, message + " " + cause.getLocalizedMessage());
                }
                LOGGER.log(Level.FINE,
                           String.format("Load of '%s' source failed with an exception.",
                                         description()),
                           cause);
            }
        }
    }

    boolean hasChanged(Optional<T> lastObject, Optional<T> newObject) {
        if (lastObject.isPresent()) {
            if (newObject.isPresent()) {
                //last DATA & new DATA => CHANGED = COMPARE DATA
                if (!lastObject.get().equals(newObject.get())) {
                    return true;
                }
            } else {
                //last DATA & new NO_DATA => CHANGED = TRUE
                return true;
            }
        } else {
            if (newObject.isPresent()) {
                //last NO_DATA & new DATA => CHANGED = TRUE
                return true;
            }

        }
        return false;
    }

    @Override
    public final String description() {
        return formatDescription(uid());
    }

    /**
     * Formats config source description.
     *
     * @param uid description of key parameters to be used in description.
     * @return config source description
     */
    String formatDescription(String uid) {
        return Source.super.description() + "[" + uid + "]" + (isMandatory() ? "" : "?")
                + (getPollingStrategy().equals(PollingStrategies.nop()) ? "" : "*");
    }

    /**
     * A common {@link AbstractSource} builder, suitable for concrete {@code Builder} implementations
     * related to {@link AbstractSource} extensions to extend.
     * <p>
     * The application can control this behavior:
     * <ul>
     * <li>{@code mandatory} - whether the resource is mandatory (by default) or
     * optional</li>
     * <li>{@code pollingStrategy} - which source reload policy to use</li>
     * <li>changes {@code executor} and subscriber's {@code buffer size} -
     * related to propagating source changes</li>
     * </ul>
     *
     * @param <B> type of Builder implementation
     * @param <T> type of key source attributes (target) used to construct
     * polling strategy from
     * @param <S> type of source that should be built
     */
    public abstract static class Builder<B extends Builder<B, T, S>, T, S> {
        /**
         * Default executor where the changes threads are run.
         */
        static final Executor DEFAULT_CHANGES_EXECUTOR = Executors.newCachedThreadPool(new ConfigThreadFactory("source"));
        private static final String OPTIONAL_KEY = "optional";
        private static final String POLLING_STRATEGY_KEY = "polling-strategy";
        private static final String RETRY_POLICY_KEY = "retry-policy";

        private final B thisBuilder;
        private final Class<T> targetType;
        private boolean mandatory;
        private Supplier<PollingStrategy> pollingStrategySupplier;
        private Executor changesExecutor;
        private int changesMaxBuffer;
        private Supplier<RetryPolicy> retryPolicySupplier;

        /**
         * Initializes builder.
         *
         * @param targetType target type
         */
        protected Builder(Class<T> targetType) {
            this.targetType = targetType;

            thisBuilder = (B) this;
            mandatory = true;
            pollingStrategySupplier = PollingStrategies::nop;
            changesExecutor = DEFAULT_CHANGES_EXECUTOR;
            changesMaxBuffer = Flow.defaultBufferSize();
            retryPolicySupplier = RetryPolicies::justCall;
        }

        /**
         * Initialize builder from specified configuration properties.
         * <p>
         * Supported configuration {@code properties}:
         * <ul>
         * <li>{@code optional} - type {@code boolean}, see {@link #optional()}</li>
         * <li>{@code polling-strategy} - see {@link PollingStrategy} for details about configuration format,
         * see {@link #pollingStrategy(Supplier)} or {@link #pollingStrategy(Function)}</li>
         * </ul>
         *
         * @param metaConfig configuration properties used to initialize a builder instance.
         * @return modified builder instance
         */
        protected B init(Config metaConfig) {
            //optional / mandatory
            metaConfig.get(OPTIONAL_KEY).asOptionalBoolean()
                    .filter(value -> value) //filter `true` only
                    .ifPresent(value -> optional());
            //polling-strategy
            metaConfig.get(POLLING_STRATEGY_KEY)
                    .ifExists(cfg -> pollingStrategy(PollingStrategyConfigMapper.instance().apply(cfg, targetType)));
            //retry-policy
            metaConfig.get(RETRY_POLICY_KEY)
                    .asOptional(RetryPolicy.class)
                    .ifPresent(this::retryPolicy);

            return thisBuilder;
        }

        /**
         * Sets a polling strategy.
         *
         * @param pollingStrategySupplier a polling strategy
         * @return a modified builder instance
         * @see PollingStrategies#regular(java.time.Duration)
         */
        public B pollingStrategy(Supplier<PollingStrategy> pollingStrategySupplier) {
            Objects.requireNonNull(pollingStrategySupplier, "pollingStrategy cannot be null");

            this.pollingStrategySupplier = pollingStrategySupplier;
            return thisBuilder;
        }

        /**
         * Sets the polling strategy that accepts key source attributes.
         * <p>
         * Concrete subclasses should override {@link #getTarget()} to provide
         * the key source attributes (target). For example, the {@code Builder}
         * for a {@code FileConfigSource} or {@code ClasspathConfigSource} uses
         * the {@code Path} to the corresponding file or resource as the key
         * source attribute (target), while the {@code Builder} for a
         * {@code UrlConfigSource} uses the {@code URL}.
         *
         * @param pollingStrategyProvider a polling strategy provider
         * @return a modified builder instance
         * @throws UnsupportedOperationException if the concrete {@code Builder}
         * implementation does not support the polling strategy
         * @see #pollingStrategy(Supplier)
         * @see #getTarget()
         */
        public final B pollingStrategy(Function<T, Supplier<PollingStrategy>> pollingStrategyProvider) {
            pollingStrategy(() -> pollingStrategyProvider.apply(getTarget()).get());

            return thisBuilder;
        }

        /**
         * Returns key source attributes (target).
         *
         * @return key source attributes (target).
         */
        protected T getTarget() {
            return null;
        }

        /**
         * Built {@link ConfigSource} will not be mandatory, i.e. it is ignored if configuration target does not exists.
         *
         * @return a modified builder instance
         */
        public B optional() {
            this.mandatory = false;

            return thisBuilder;
        }

        /**
         * Specifies "observe-on" {@link Executor} to be used to deliver
         * {@link ConfigSource#changes() config source changes}. The same
         * executor is also used to reload the source, as triggered by the
         * {@link PollingStrategy#ticks() polling strategy event}.
         * <p>
         * The default executor is from a dedicated thread pool which reuses
         * threads as possible.
         *
         * @param changesExecutor the executor to use for async delivery of
         * {@link ConfigSource#changes()} events
         * @return a modified builder instance
         * @see #changesMaxBuffer(int)
         * @see ConfigSource#changes()
         * @see PollingStrategy#ticks()
         */
        public B changesExecutor(Executor changesExecutor) {
            Objects.requireNonNull(changesExecutor, "changesExecutor cannot be null");

            this.changesExecutor = changesExecutor;
            return thisBuilder;
        }

        /**
         * Specifies maximum capacity for each subscriber's buffer to be used to deliver
         * {@link ConfigSource#changes() config source changes}.
         * <p>
         * By default {@link Flow#DEFAULT_BUFFER_SIZE} is used.
         * <p>
         * Note: Not consumed events will be dropped off.
         *
         * @param changesMaxBuffer the maximum capacity for each subscriber's buffer of {@link ConfigSource#changes()} events.
         * @return a modified builder instance
         * @see #changesExecutor(Executor)
         * @see ConfigSource#changes()
         */
        public B changesMaxBuffer(int changesMaxBuffer) {
            this.changesMaxBuffer = changesMaxBuffer;
            return thisBuilder;
        }

        /**
         * Sets a supplier of {@link RetryPolicy} that will be responsible for invocation of {@link AbstractSource#load()}.
         * <p>
         * The default reply policy is {@link RetryPolicies#justCall()}.
         * <p>
         * Create a custom policy or use the built-in policy constructed with a {@link RetryPolicies#repeat(int) builder}.
         *
         * @param retryPolicySupplier a execute policy supplier
         * @return a modified builder instance
         */
        public B retryPolicy(Supplier<RetryPolicy> retryPolicySupplier) {
            this.retryPolicySupplier = retryPolicySupplier;
            return thisBuilder;
        }

        /**
         * Builds new instance of {@code S}.
         *
         * @return new instance of {@code S}.
         */
        public abstract S build();

        /**
         * Returns mandatory property.
         *
         * @return mandatory property.
         */
        protected boolean isMandatory() {
            return mandatory;
        }

        /**
         * Returns polling-strategy property.
         *
         * @return polling-strategy property.
         */
        protected PollingStrategy getPollingStrategy() {
            PollingStrategy pollingStrategy = pollingStrategySupplier.get();

            Objects.requireNonNull(pollingStrategy, "pollingStrategy cannot be null");

            return pollingStrategy;
        }

        /**
         * Returns changes-executor property.
         *
         * @return changes-executor property.
         */
        protected Executor getChangesExecutor() {
            return changesExecutor;
        }

        /**
         * Returns changes-max-buffer property.
         *
         * @return changes-max-buffer property.
         */
        protected int getChangesMaxBuffer() {
            return changesMaxBuffer;
        }

        protected RetryPolicy getRetryPolicy() {
            return retryPolicySupplier.get();
        }
    }

    /**
     * Data loaded at appropriate time.
     *
     * @param <D> an type of loaded data
     * @param <S> a type of data stamp
     */
    public static final class Data<D, S> {
        private final Optional<D> data;
        private final Optional<S> stamp;

        /**
         * Initialize data object for specified timestamp and covered data.
         */
        public Data() {
            this.stamp = Optional.empty();
            this.data = Optional.empty();
        }

        /**
         * Initialize data object for specified timestamp and covered data.
         *
         * @param data  covered object node. Can be {@code null} in case source does not exist.
         * @param stamp data stamp
         */
        public Data(Optional<D> data, Optional<S> stamp) {
            Objects.requireNonNull(data);
            Objects.requireNonNull(stamp);
            this.stamp = stamp;
            this.data = data;
        }

        /**
         * Returns loaded data.
         *
         * @return loaded data.
         */
        public Optional<D> data() {
            return data;
        }

        /**
         * Returns stamp of data.
         *
         * @return stamp of data.
         */
        public Optional<S> stamp() {
            return stamp;
        }
    }

    /**
     * {@link Flow.Subscriber} on {@link PollingStrategy#ticks() polling strategy} to listen on {@link
     * PollingStrategy.PollingEvent}.
     */
    private class PollingEventSubscriber implements Flow.Subscriber<PollingStrategy.PollingEvent> {

        private Flow.Subscription subscription;
        private volatile boolean reloadLogged = false;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;

            subscription.request(1);
        }

        @Override
        public void onNext(PollingStrategy.PollingEvent item) {
            AbstractSource.this.changesExecutor.execute(this::safeReload);
        }

        private void safeReload() {
            try {
                AbstractSource.this.reload();
                if (reloadLogged) {
                    String message = String.format("Reload of override source [%s] succeeded again. Polling will continue.",
                                                   description());
                    LOGGER.log(isMandatory() ? Level.WARNING : Level.CONFIG, message);
                    reloadLogged = false;
                }
            } catch (Exception ex) {
                if (!reloadLogged) {
                    String message = String.format("Reload of override source [%s] failed. Polling will continue. %s",
                                                   description(),
                                                   ex.getLocalizedMessage());
                    LOGGER.log(isMandatory() ? Level.WARNING : Level.CONFIG, message);
                    LOGGER.log(Level.CONFIG,
                               String.format("Reload of '%s' override source failed with an exception.",
                                             description()),
                               ex);
                    reloadLogged = true;
                }
            } finally {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            AbstractSource.this.changesSubmitter
                    .closeExceptionally(new ConfigException(
                            String.format("Polling strategy '%s' has failed. Polling of '%s' source will not continue. %s",
                                          pollingStrategy,
                                          description(),
                                          throwable.getLocalizedMessage()
                            ),
                            throwable));
        }

        @Override
        public void onComplete() {
            LOGGER.fine(String.format("Polling strategy '%s' has completed. Polling of '%s' source will not continue.",
                                      pollingStrategy,
                                      description()));

            AbstractSource.this.changesSubmitter.close();
        }

        private void cancelSubscription() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }
}
