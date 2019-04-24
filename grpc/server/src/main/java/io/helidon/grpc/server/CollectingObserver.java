/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

import io.grpc.stub.StreamObserver;

/**
 * A {@link StreamObserver}.
 *
 * @param <T>  ToDo: Add JavaDoc
 * @param <V>  ToDo: Add JavaDoc
 * @param <U>  ToDo: Add JavaDoc
 * @param <A>  ToDo: Add JavaDoc
 * @param <R>  ToDo: Add JavaDoc
 */
public class CollectingObserver<T, V, U, A, R> implements StreamObserver<V> {
    private final Collector<T, A, R> collector;
    private final StreamObserver<U> responseObserver;
    private final Function<V, T> requestConverter;
    private final Function<R, U> responseConverter;
    private final Consumer<Throwable> errorHandler;

    private final A accumulator;

    /**
     * ToDo: Add JavaDoc.
     *
     * @param collector         ToDo: Add JavaDoc
     * @param responseObserver  ToDo: Add JavaDoc
     */
    public CollectingObserver(Collector<T, A, R> collector, StreamObserver<U> responseObserver) {
        this(collector, responseObserver, null, null, null);
    }

    /**
     * ToDo: Add JavaDoc.
     *
     * @param collector        ToDo: Add JavaDoc
     * @param responseObserver ToDo: Add JavaDoc
     * @param errorHandler     ToDo: Add JavaDoc
     */
    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> responseObserver,
                              Consumer<Throwable> errorHandler) {
        this(collector, responseObserver, null, null, errorHandler);
    }

    /**
     * ToDo: Add JavaDoc.
     *
     * @param collector         ToDo: Add JavaDoc
     * @param responseObserver  ToDo: Add JavaDoc
     * @param requestConverter  ToDo: Add JavaDoc
     * @param responseConverter ToDo: Add JavaDoc
     */
    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> responseObserver,
                              Function<V, T> requestConverter,
                              Function<R, U> responseConverter) {
        this(collector, responseObserver, requestConverter, responseConverter, null);
    }

    /**
     * ToDo: Add JavaDoc.
     *
     * @param collector         ToDo: Add JavaDoc
     * @param observer  ToDo: Add JavaDoc
     * @param requestConverter  ToDo: Add JavaDoc
     * @param responseConverter ToDo: Add JavaDoc
     * @param errorHandler      ToDo: Add JavaDoc
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
