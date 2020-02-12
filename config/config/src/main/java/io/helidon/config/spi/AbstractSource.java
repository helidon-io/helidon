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

package io.helidon.config.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.MetaConfig;
import io.helidon.config.PollingStrategies;
import io.helidon.config.RetryPolicies;
import io.helidon.config.internal.ConfigThreadFactory;

/**
 * Abstract base implementation for a variety of sources.
 * <p>
 * The inner {@link io.helidon.config.spi.AbstractSource.Builder} class is ready-to-extend with built-in support of
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
    private Optional<Data<T>> lastData;
    private PollingEventSubscriber pollingEventSubscriber;

    AbstractSource(Builder<?, ?, ?> builder) {
        mandatory = builder.isMandatory();
        pollingStrategy = builder.pollingStrategy();
        changesExecutor = builder.changesExecutor();
        retryPolicy = builder.retryPolicy();
        changesSubmitter = new SubmissionPublisher<>(changesExecutor, builder.changesMaxBuffer());
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
        Optional<Data<T>> newData = loadDataChangedSinceLastLoad();

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

    SubmissionPublisher<Optional<T>> changesSubmitter() {
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

    /**
     * Publisher of changes of this source.
     * @return publisher of source data
     */
    protected Flow.Publisher<Optional<T>> changesPublisher() {
        return changesPublisher;
    }

    PollingStrategy pollingStrategy() {
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
    protected Data<T> processLoadedData(Data<T> data) {
        return data;
    }

    /**
     * Returns current stamp of data in config source.
     *
     * @return current datastamp of data in config source
     */
    protected abstract Optional<? extends Object> dataStamp();

    Optional<Data<T>> lastData() {
        return lastData;
    }

    /**
     * Loads data from source when {@code data} expires.
     *
     * @return the last loaded data
     */
    @Override
    public final Optional<T> load() {
        Optional<Data<T>> loadedData = loadDataChangedSinceLastLoad();
        if (loadedData.isPresent()) {
            lastData = loadedData;
        }
        if (lastData.isPresent()) {
            return lastData.get().data();
        } else {
            return Optional.empty();
        }
    }

    Optional<Data<T>> loadDataChangedSinceLastLoad() {
        Optional<Object> lastDatastamp = lastData.flatMap(Data::stamp);
        Optional<? extends Object> dataStamp = dataStamp();

        if (lastData.isEmpty() || !dataStamp.equals(lastData.get().stamp())) {
            LOGGER.log(Level.FINE,
                       String.format("Source %s has changed to %s from %s.", description(), dataStamp,
                                     lastDatastamp));
            try {
                Data<T> data = retryPolicy.execute(this::loadData);
                if (!data.stamp().equals(lastDatastamp)) {
                    LOGGER.log(Level.FINE,
                               String.format("Source %s has changed to %s from %s.", description(), dataStamp,
                                             lastDatastamp));
                    return Optional.of(processLoadedData(data));
                } else {
                    if (lastData.isEmpty()) {
                        return Optional.of(processLoadedData(data));
                    }
                    LOGGER.log(Level.FINE,
                               String.format("Config data %s has not changed, last stamp was %s.", description(), lastDatastamp));
                }
            } catch (ConfigException ex) {
                processMissingData(ex);

                if (lastData.isPresent() && lastData.get().data().isPresent()) {
                    LOGGER.log(Level.FINE,
                               String.format("Config data %s has has been removed.", description()));
                    return Optional.of(Data.create());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Loads new data from config source.
     *
     * @return newly loaded data with appropriate data timestamp used for future method calls
     * @throws io.helidon.config.ConfigException in case it is not possible to load configuration data
     */
    protected abstract Data<T> loadData() throws ConfigException;

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
                LOGGER.log(Level.FINEST,
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
                + (pollingStrategy().equals(PollingStrategies.nop()) ? "" : "*");
    }

    /**
     * A common {@link io.helidon.config.spi.AbstractSource} builder, suitable for concrete {@code Builder} implementations
     * related to {@link io.helidon.config.spi.AbstractSource} extensions to extend.
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
    public abstract static class Builder<B extends Builder<B, T, S>, T, S extends Source<?>>
            implements io.helidon.common.Builder<S> {

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
        @SuppressWarnings("unchecked")
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
         * Returns current builder instance.
         *
         * @return builder instance
         */
        protected B thisBuilder() {
            return thisBuilder;
        }

        /**
         * Configure this builder from an existing configuration (we use the term meta configuration for this
         * type of configuration, as it is a configuration that builds configuration).
         * <p>
         * Supported configuration {@code properties}:
         * <ul>
         *  <li>{@code optional} - type {@code boolean}, see {@link #optional()}</li>
         *  <li>{@code polling-strategy} - see {@link io.helidon.config.spi.PollingStrategy} for details about configuration
         *  format,
         *          see {@link #pollingStrategy(java.util.function.Supplier)} or
         *          {@link #pollingStrategy(java.util.function.Function)}</li>
         *  <li>{@code retry-policy} - see {@link RetryPolicy} for details about
         *      configuration format</li>
         * </ul>
         *
         *
         * @param metaConfig configuration to configure this source
         * @return modified builder instance
         */
        @SuppressWarnings("unchecked")
        public B config(Config metaConfig) {
            //optional / mandatory
            metaConfig.get(OPTIONAL_KEY)
                    .asBoolean()
                    .ifPresent(this::optional);

            //polling-strategy
            metaConfig.get(POLLING_STRATEGY_KEY)
                    .ifExists(cfg -> pollingStrategy((t -> MetaConfig.pollingStrategy(cfg).apply(t))));

            //retry-policy
            metaConfig.get(RETRY_POLICY_KEY)
                    .ifExists(cfg -> retryPolicy(MetaConfig.retryPolicy(cfg)));

            return thisBuilder;
        }

        /**
         * Sets a polling strategy.
         *
         * @param pollingStrategySupplier a polling strategy
         * @return a modified builder instance
         * @see io.helidon.config.PollingStrategies#regular(java.time.Duration)
         */
        public B pollingStrategy(Supplier<PollingStrategy> pollingStrategySupplier) {
            Objects.requireNonNull(pollingStrategySupplier, "pollingStrategy cannot be null");

            this.pollingStrategySupplier = pollingStrategySupplier;
            return thisBuilder;
        }

        /**
         * Sets the polling strategy that accepts key source attributes.
         * <p>
         * Concrete subclasses should override {@link #target()} to provide
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
         * @see #pollingStrategy(java.util.function.Supplier)
         * @see #target()
         */
        public final B pollingStrategy(Function<T, Supplier<PollingStrategy>> pollingStrategyProvider) {
            pollingStrategy(() -> pollingStrategyProvider.apply(target()).get());

            return thisBuilder;
        }

        /**
         * Returns key source attributes (target).
         *
         * @return key source attributes (target).
         */
        protected T target() {
            return null;
        }

        /**
         * Type of target used by this builder.
         *
         * @return target type, used by {@link #pollingStrategy(java.util.function.Function)}
         */
        public Class<T> targetType() {
            return targetType;
        }

        /**
         * Built {@link io.helidon.config.spi.ConfigSource} will not be mandatory, i.e. it is ignored if configuration target
         * does not exists.
         *
         * @return a modified builder instance
         */
        public B optional() {
            this.mandatory = false;

            return thisBuilder;
        }

        /**
         * Built {@link io.helidon.config.spi.ConfigSource} will be optional ({@code true}) or mandatory ({@code false}).
         *
         * @param optional set to {@code true} to mark this source optional.
         * @return a modified builder instance
         */
        public B optional(boolean optional) {
            this.mandatory = !optional;

            return thisBuilder;
        }

        /**
         * Specifies "observe-on" {@link java.util.concurrent.Executor} to be used to deliver
         * {@link io.helidon.config.spi.ConfigSource#changes() config source changes}. The same
         * executor is also used to reload the source, as triggered by the
         * {@link io.helidon.config.spi.PollingStrategy#ticks() polling strategy event}.
         * <p>
         * The default executor is from a dedicated thread pool which reuses
         * threads as possible.
         *
         * @param changesExecutor the executor to use for async delivery of
         * {@link io.helidon.config.spi.ConfigSource#changes()} events
         * @return a modified builder instance
         * @see #changesMaxBuffer(int)
         * @see io.helidon.config.spi.ConfigSource#changes()
         * @see io.helidon.config.spi.PollingStrategy#ticks()
         */
        public B changesExecutor(Executor changesExecutor) {
            Objects.requireNonNull(changesExecutor, "changesExecutor cannot be null");

            this.changesExecutor = changesExecutor;
            return thisBuilder;
        }

        /**
         * Specifies maximum capacity for each subscriber's buffer to be used to deliver
         * {@link io.helidon.config.spi.ConfigSource#changes() config source changes}.
         * <p>
         * By default {@link java.util.concurrent.Flow#defaultBufferSize()} is used.
         * <p>
         * Note: Not consumed events will be dropped off.
         *
         * @param changesMaxBuffer the maximum capacity for each subscriber's buffer of
         * {@link io.helidon.config.spi.ConfigSource#changes()} events.
         * @return a modified builder instance
         * @see #changesExecutor(java.util.concurrent.Executor)
         * @see io.helidon.config.spi.ConfigSource#changes()
         */
        public B changesMaxBuffer(int changesMaxBuffer) {
            this.changesMaxBuffer = changesMaxBuffer;
            return thisBuilder;
        }

        /**
         * Sets a supplier of {@link io.helidon.config.spi.RetryPolicy} that will be responsible for invocation of
         * {@link io.helidon.config.spi.AbstractSource#load()}.
         * <p>
         * The default reply policy is {@link io.helidon.config.RetryPolicies#justCall()}.
         * <p>
         * Create a custom policy or use the built-in policy constructed with a
         * {@link io.helidon.config.RetryPolicies#repeat(int) builder}.
         *
         * @param retryPolicySupplier a execute policy supplier
         * @return a modified builder instance
         */
        public B retryPolicy(Supplier<RetryPolicy> retryPolicySupplier) {
            this.retryPolicySupplier = retryPolicySupplier;
            return thisBuilder;
        }

        /**
         * Set a {@link io.helidon.config.spi.RetryPolicy} that will be responsible for invocation of
         * {@link io.helidon.config.spi.AbstractSource#load()}.
         * <p>
         * The default reply policy is {@link io.helidon.config.RetryPolicies#justCall()}.
         * <p>
         * Create a custom policy or use the built-in policy constructed with a
         * {@link io.helidon.config.RetryPolicies#repeat(int) builder}.
         *
         * @param retryPolicy retry policy
         * @return a modified builder instance
         */
        public B retryPolicy(RetryPolicy retryPolicy) {
            return retryPolicy(() -> retryPolicy);
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
        protected PollingStrategy pollingStrategy() {
            PollingStrategy pollingStrategy = pollingStrategySupplier.get();

            Objects.requireNonNull(pollingStrategy, "pollingStrategy cannot be null");

            return pollingStrategy;
        }

        /**
         * Returns changes-executor property.
         *
         * @return changes-executor property.
         */
        protected Executor changesExecutor() {
            return changesExecutor;
        }

        /**
         * Returns changes-max-buffer property.
         *
         * @return changes-max-buffer property.
         */
        protected int changesMaxBuffer() {
            return changesMaxBuffer;
        }

        /**
         * Retry policy configured in this builder.
         * @return retry policy
         */
        protected RetryPolicy retryPolicy() {
            return retryPolicySupplier.get();
        }
    }

    /**
     * Data loaded at appropriate time.
     *
     * @param <D> an type of loaded data
     */
    public static final class Data<D> {
        private final Optional<D> data;
        private final Optional<Object> stamp;

        private Data(Builder<D> builder) {
            data = Optional.ofNullable(builder.data);
            stamp = Optional.ofNullable(builder.stamp);
        }

        /**
         * Fluent API builder.
         *
         * @return a new instance of a builder
         */
        public static <T> Builder<T> builder() {
            return new Builder<>();
        }

        /**
         * Create an instance of empty data.
         * @param <T> type of data
         * @return empty data
         */
        public static <T> Data<T> create() {
            return Data.<T>builder().build();
        }

        /**
         * Create data with content and a stamp.
         * @param content the data content
         * @param stamp the data stamp (such as digest, timestamp)
         * @param <T> type of data
         * @return empty data
         */
        public static <T> Data<T> create(T content, Object stamp) {
            return Data.<T>builder()
                    .data(content)
                    .stamp(stamp)
                    .build();
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
        public Optional<Object> stamp() {
            return stamp;
        }

        /**
         * Fluent API builder for {@code Data}.
         * @param <T> type of the data
         */
        public static final class Builder<T> {
            private T data;
            private Object stamp;

            private Builder() {
            }

            /**
             * Returns a {@code Data} built from the parameters previously set.
             *
             * @return a {@code Data} built with parameters of this {@code Data.Builder}
             */
            public Data<T> build() {
                return new Data<>(this);
            }

            /**
             * Data content.
             *
             * @param data data to use
             * @return updated builder instance
             */
            public Builder<T> data(T data) {
                this.data = data;
                return this;
            }

            /**
             * Data stamp of the content.
             *
             * @param stamp stamp to use
             * @return updated builder instance
             */
            public Builder<T> stamp(Object stamp) {
                this.stamp = stamp;
                return this;
            }
        }
    }

    /**
     * {@link java.util.concurrent.Flow.Subscriber} on {@link io.helidon.config.spi.PollingStrategy#ticks() polling strategy}
     * to listen on {@link
     * io.helidon.config.spi.PollingStrategy.PollingEvent}.
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
