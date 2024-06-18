/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.grpc.core;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

import io.grpc.stub.StreamObserver;

/**
 * Utility {@link StreamObserver} mostly used for testing.
 *
 * @param <T> the type of input elements to the reduction operation
 * @param <A> the mutable accumulation type of the reduction operation
 * @param <R> the result type of the reduction operation
 * @param <U> the type of values observed
 * @param <V> the request type before conversion
 */
public class CollectingObserver<T, V, U, A, R> implements StreamObserver<V> {

    private final Collector<T, A, R> collector;
    private final StreamObserver<U> responseObserver;
    private final Function<V, T> requestConverter;
    private final Function<R, U> responseConverter;
    private final Consumer<Throwable> errorHandler;
    private final A accumulator;

    /**
     * Constructor
     *
     * @param collector the collector
     * @param observer the observer
     */
    public CollectingObserver(Collector<T, A, R> collector, StreamObserver<U> observer) {
        this(collector, observer, null, null, null);
    }

    /**
     * Constructor
     *
     * @param collector the collector
     * @param observer the observer
     * @param errorHandler the error handler
     */
    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> observer,
                              Consumer<Throwable> errorHandler) {
        this(collector, observer, null, null, errorHandler);
    }

    /**
     * Constructor
     *
     * @param collector the collector
     * @param observer the observer
     * @param requestConverter the request converter
     * @param responseConverter the response converterr
     */
    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> observer,
                              Function<V, T> requestConverter,
                              Function<R, U> responseConverter) {
        this(collector, observer, requestConverter, responseConverter, null);
    }

    /**
     * Constructor
     *
     * @param collector the collector
     * @param observer the observer
     * @param requestConverter the request converter
     * @param responseConverter the response converter
     * @param errorHandler the error handler
     */
    @SuppressWarnings("unchecked")
    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> observer,
                              Function<V, T> requestConverter,
                              Function<R, U> responseConverter,
                              Consumer<Throwable> errorHandler) {
        this.collector = Objects.requireNonNull(collector, "The collector parameter cannot be null");
        this.responseObserver = Objects.requireNonNull(observer, "The observer parameter cannot be null");
        this.requestConverter = Optional.ofNullable(requestConverter).orElse(v -> (T) v);
        this.responseConverter = Optional.ofNullable(responseConverter).orElse(r -> (U) r);
        this.errorHandler = Optional.ofNullable(errorHandler).orElse(t -> {});
        this.accumulator = collector.supplier().get();
    }

    @Override
    public void onNext(V value) {
        collector.accumulator().accept(accumulator, requestConverter.apply(value));
    }

    @Override
    public void onError(Throwable t) {
        errorHandler.accept(t);
    }

    @Override
    public void onCompleted() {
        R result = collector.finisher().apply(accumulator);
        responseObserver.onNext(responseConverter.apply(result));
        responseObserver.onCompleted();
    }
}
