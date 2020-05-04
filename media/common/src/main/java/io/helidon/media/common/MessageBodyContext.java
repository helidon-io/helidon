/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;

/**
 * Base message body context implementation.
 */
public abstract class MessageBodyContext implements MessageBodyFilters {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(MessageBodyContext.class.getName());

    /**
     * Message body content subscription event listener.
     */
    public interface EventListener {

        /**
         * Handle a subscription event.
         * @param event subscription event
         */
        void onEvent(Event event);
    }

    /**
     * Message body content subscription event types.
     */
    public enum EventType {

        /**
         * Emitted before {@link Subscriber#onSubscribe(Subscription)}.
         */
        BEFORE_ONSUBSCRIBE,

        /**
         * Emitted after {@link Subscriber#onSubscribe(Subscription)}.
         */
        AFTER_ONSUBSCRIBE,

        /**
         * Emitted before {@link Subscriber#onNext(Object)}.
         */
        BEFORE_ONNEXT,

        /**
         * Emitted after {@link Subscriber#onNext(Object)}.
         */
        AFTER_ONNEXT,

        /**
         * Emitted after {@link Subscriber#onError(Throwable)}.
         */
        BEFORE_ONERROR,

        /**
         * Emitted after {@link Subscriber#onError(Throwable)}.
         */
        AFTER_ONERROR,

        /**
         * Emitted after {@link Subscriber#onComplete()}.
         */
        BEFORE_ONCOMPLETE,

        /**
         * Emitted after {@link Subscriber#onComplete()}.
         */
        AFTER_ONCOMPLETE
    }

    /**
     * Message body content subscription event contract.
     */
    public interface Event {

        /**
         * Get the event type of this event.
         * @return EVENT_TYPE
         */
        EventType eventType();

        /**
         * Get the type requested for conversion.
         * @return never {@code null}
         */
        Optional<GenericType<?>> entityType();

        /**
         * Fluent helper method to cast this event as a {@link ErrorEvent}. This
         * is safe to do when {@link #eventType()} returns
         * {@link EventType#BEFORE_ONERROR} or {@link EventType#AFTER_ONERROR}
         *
         * @return ErrorEvent
         * @throws IllegalStateException if this event is not an instance of
         * {@link ErrorEvent}
         */
        default ErrorEvent asErrorEvent() {
            if (!(this instanceof ErrorEvent)) {
                throw new IllegalStateException("Not an error event");
            }
            return (ErrorEvent) this;
        }
    }

    /**
     * A subscription event emitted for {@link EventType#BEFORE_ONERROR} or
     * {@link EventType#AFTER_ONERROR} that carries the received error.
     */
    public interface ErrorEvent extends Event {

        /**
         * Get the subscription error of this event.
         * @return {@code Throwable}, never {@code null}
         */
        Throwable error();
    }

    /**
     * Singleton event for {@link EventType#BEFORE_ONSUBSCRIBE}.
     */
    private static final Event BEFORE_ONSUBSCRIBE = new EventImpl(EventType.BEFORE_ONSUBSCRIBE, Optional.empty());

    /**
     * Singleton event for {@link EventType#BEFORE_ONNEXT}.
     */
    private static final Event BEFORE_ONNEXT = new EventImpl(EventType.BEFORE_ONNEXT, Optional.empty());

    /**
     * Singleton event for {@link EventType#BEFORE_ONCOMPLETE}.
     */
    private static final Event BEFORE_ONCOMPLETE = new EventImpl(EventType.BEFORE_ONCOMPLETE, Optional.empty());

    /**
     * Singleton event for {@link EventType#AFTER_ONSUBSCRIBE}.
     */
    private static final Event AFTER_ONSUBSCRIBE = new EventImpl(EventType.AFTER_ONSUBSCRIBE, Optional.empty());

    /**
     * Singleton event for {@link EventType#AFTER_ONNEXT}.
     */
    private static final Event AFTER_ONNEXT = new EventImpl(EventType.AFTER_ONNEXT, Optional.empty());

    /**
     * Singleton event for {@link EventType#AFTER_ONCOMPLETE}.
     */
    private static final Event AFTER_ONCOMPLETE = new EventImpl(EventType.AFTER_ONCOMPLETE, Optional.empty());

    /**
     * The filters registry.
     */
    private final MessageBodyOperators<FilterOperator> filters;

    /**
     * Message body content subscription event listener.
     */
    private final EventListener eventListener;

    /**
     * Create a new parented content support instance.
     * @param parent content filters parent
     * @param eventListener event listener
     */
    protected MessageBodyContext(MessageBodyContext parent, EventListener eventListener) {
        if (parent != null) {
            this.filters = new MessageBodyOperators<>(parent.filters);
        } else {
            this.filters = new MessageBodyOperators<>();
        }
        this.eventListener = eventListener;
    }

    /**
     * Create a new parented content support instance.
     *
     * @param parent content filters parent
     */
    protected MessageBodyContext(MessageBodyContext parent) {
        this(parent, parent.eventListener);
    }

    /**
     * Derive the charset to use from the {@code Content-Type} header value or
     * using a default charset as fallback.
     *
     * @return Charset, never {@code null}
     * @throws IllegalStateException if an error occurs loading the charset
     * specified by the {@code Content-Type} header value
     */
    public abstract Charset charset() throws IllegalStateException;

    @Override
    public MessageBodyContext registerFilter(MessageBodyFilter filter) {
        Objects.requireNonNull(filter, "filter is null!");
        filters.registerLast(new FilterOperator(filter));
        return this;
    }

    /**
     * Register a function filter.
     *
     * @param function filter function
     * @deprecated use {@link #registerFilter(MessageBodyFilter)} instead
     */
    @Deprecated
    public void registerFilter(Function<Publisher<DataChunk>, Publisher<DataChunk>> function) {
        Objects.requireNonNull(function, "filter function is null!");
        filters.registerLast(new FilterOperator(new FunctionFilter(function)));
    }

    /**
     * Apply the filters on the given input publisher to form a publisher chain.
     * @param publisher input publisher
     * @return tail of the publisher chain
     */
    public Publisher<DataChunk> applyFilters(Publisher<DataChunk> publisher) {
        return doApplyFilters(publisher, eventListener);
    }

    /**
     * Apply the filters on the given input publisher to form a publisher chain.
     * @param publisher input publisher
     * @param type type information associated with the input publisher
     * @return tail of the publisher chain
     */
    protected Publisher<DataChunk> applyFilters(Publisher<DataChunk> publisher, GenericType<?> type) {
        Objects.requireNonNull(type, "type cannot be null!");
        if (eventListener != null) {
            return doApplyFilters(publisher, new TypedEventListener(eventListener, type));
        } else {
            return doApplyFilters(publisher, eventListener);
        }
    }

    /**
     * Perform the filter chaining.
     * @param publisher input publisher
     * @param listener subscription listener
     * @return tail of the publisher chain
     */
    private Publisher<DataChunk> doApplyFilters(Publisher<DataChunk> publisher, EventListener listener) {
        if (publisher == null) {
            publisher = Single.<DataChunk>empty();
        }
        try {
            Publisher<DataChunk> last = publisher;
            for (MessageBodyFilter filter : filters) {
                last.subscribe(filter);
                last = filter;
            }
            return new EventingPublisher(last, listener);
        } finally {
            filters.close();
        }
    }

    /**
     * Delegating publisher that subscribes a delegating
     * {@link EventingSubscriber} during {@link Publisher#subscribe }.
     */
    private static final class EventingPublisher implements Publisher<DataChunk> {

        private final Publisher<DataChunk> publisher;
        private final EventListener listener;

        EventingPublisher(Publisher<DataChunk> publisher, EventListener listener) {
            this.publisher = publisher;
            this.listener = listener;
        }

        @Override
        public void subscribe(Subscriber<? super DataChunk> subscriber) {
            publisher.subscribe(new EventingSubscriber(subscriber, listener));
        }
    }

    /**
     * Delegating subscriber that emits the events.
     */
    private static final class EventingSubscriber implements Subscriber<DataChunk> {

        private final Subscriber<? super DataChunk> delegate;
        private final EventListener listener;

        EventingSubscriber(Subscriber<? super DataChunk> delegate, EventListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        private void fireEvent(Event event) {
            if (listener != null) {
                try {
                    listener.onEvent(event);
                } catch (Throwable ex) {
                    LOGGER.log(Level.WARNING, "An exception occurred in EventListener.onEvent", ex);
                }
            }
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            fireEvent(BEFORE_ONSUBSCRIBE);
            try {
                delegate.onSubscribe(subscription);
            } finally {
                fireEvent(AFTER_ONSUBSCRIBE);
            }
        }

        @Override
        public void onNext(DataChunk item) {
            fireEvent(BEFORE_ONNEXT);
            try {
                delegate.onNext(item);
            } finally {
                fireEvent(AFTER_ONNEXT);
            }
        }

        @Override
        public void onError(Throwable error) {
            fireEvent(new ErrorEventImpl(error, EventType.BEFORE_ONERROR));
            try {
                delegate.onError(error);
            } finally {
                fireEvent(new ErrorEventImpl(error, EventType.AFTER_ONERROR));
            }
        }

        @Override
        public void onComplete() {
            fireEvent(BEFORE_ONCOMPLETE);
            try {
                delegate.onComplete();
            } finally {
                fireEvent(AFTER_ONCOMPLETE);
            }
        }
    }

    /**
     * A filter adapter to support the old deprecated filter as function.
     */
    private static final class FunctionFilter implements MessageBodyFilter {

        private final Function<Publisher<DataChunk>, Publisher<DataChunk>> function;
        private Subscriber<? super DataChunk> subscriber;
        private Subscription subscription;
        private Throwable error;
        private boolean completed;
        private Publisher<DataChunk> downstream;

        FunctionFilter(Function<Publisher<DataChunk>, Publisher<DataChunk>> function) {
            this.function = function;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            downstream = function.apply(new Publisher<DataChunk>() {
                @Override
                public void subscribe(Subscriber<? super DataChunk> subscriber) {
                    if (FunctionFilter.this.subscriber != null) {
                        subscriber.onError(new IllegalStateException("Already subscribed to!"));
                    } else {
                        FunctionFilter.this.subscriber = subscriber;
                        if (error != null) {
                            subscriber.onError(error);
                        } else if (completed) {
                            subscriber.onComplete();
                        } else {
                            subscriber.onSubscribe(subscription);
                        }
                    }
                }
            });
        }

        @Override
        public void onNext(DataChunk item) {
            this.subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            if (subscriber != null) {
                subscriber.onError(throwable);
            } else {
                error = throwable;
            }
        }

        @Override
        public void onComplete() {
            if (this.subscriber != null) {
                this.subscriber.onComplete();
            } else {
                completed = true;
            }
        }

        @Override
        public void subscribe(Subscriber<? super DataChunk> s) {
            if (downstream != null) {
                downstream.subscribe(s);
            } else {
                if (subscriber == null) {
                    subscriber = s;
                }
                if (error != null) {
                    subscriber.onError(error);
                } else if (completed) {
                    subscriber.onComplete();
                } else if (subscription != null) {
                    subscriber.onSubscribe(subscription);
                } else {
                    subscriber.onError(new IllegalStateException("Not ready!"));
                }
            }
        }
    }

    /**
     * {@link Operator} adapter for {@link Filter}.
     */
    private static final class FilterOperator implements MessageBodyOperator<MessageBodyContext>, MessageBodyFilter {

        private final MessageBodyFilter filter;

        FilterOperator(MessageBodyFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean accept(GenericType<?> type, MessageBodyContext context) {
            return this.getClass().equals(type.rawType());
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            filter.onSubscribe(subscription);
        }

        @Override
        public void onNext(DataChunk item) {
            filter.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            filter.onError(throwable);
        }

        @Override
        public void onComplete() {
            filter.onComplete();
        }

        @Override
        public void subscribe(Subscriber<? super DataChunk> subscriber) {
            filter.subscribe(subscriber);
        }
    }

    /**
     * Delegating listener that creates copies of the emitted events to add the
     * entityType.
     */
    private static final class TypedEventListener implements EventListener {

        private final EventListener delegate;
        private final Optional<GenericType<?>> entityType;

        TypedEventListener(EventListener delegate, GenericType<?> entityType) {
            this.delegate = delegate;
            this.entityType = Optional.of(entityType);
        }

        @Override
        public void onEvent(Event event) {
            Event copy;
            if (event instanceof ErrorEventImpl) {
                copy = new ErrorEventImpl((ErrorEventImpl) event, entityType);
            } else if (event instanceof EventImpl) {
                copy = new EventImpl((EventImpl) event, entityType);
            } else {
                throw new IllegalStateException("Unknown event type " + event);
            }
            delegate.onEvent(copy);
        }
    }

    /**
     * {@link Event} implementation.
     */
    private static class EventImpl implements Event {

        private final EventType eventType;
        private final Optional<GenericType<?>> entityType;

        EventImpl(EventImpl event, Optional<GenericType<?>> entityType) {
            this(event.eventType, entityType);
        }

        EventImpl(EventType eventType, Optional<GenericType<?>> entityType) {
            this.eventType = eventType;
            this.entityType = entityType;
        }

        @Override
        public Optional<GenericType<?>> entityType() {
            return entityType;
        }

        @Override
        public EventType eventType() {
            return eventType;
        }
    }

    /**
     * {@link ErrorEvent} implementation.
     */
    private static final class ErrorEventImpl extends EventImpl implements ErrorEvent {

        private final Throwable error;

        ErrorEventImpl(ErrorEventImpl event, Optional<GenericType<?>> type) {
            super(event.eventType(), type);
            error = event.error;
        }

        ErrorEventImpl(Throwable error, EventType eventType) {
            super(eventType, Optional.empty());
            Objects.requireNonNull(error, "error cannot be null!");
            this.error = error;
        }

        @Override
        public Throwable error() {
            return error;
        }
    }
}
