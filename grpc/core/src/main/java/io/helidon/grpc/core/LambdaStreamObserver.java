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

package io.helidon.grpc.core;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.stub.StreamObserver;

/**
 * A {@link StreamObserver} that uses lambdas to handle the
 * various messages.
 *
 * @param <V> the type of elements being observed
 */
public class LambdaStreamObserver<V>
        implements StreamObserver<V> {

    private static final Logger LOGGER = Logger.getLogger(LambdaStreamObserver.class.getName());

    private final Consumer<V> onNextConsumer;
    private final Runnable onCompleteTask;
    private final Consumer<Throwable> errorConsumer;

    private LambdaStreamObserver(Consumer<V> onNextConsumer,
                                 Runnable onCompleteTask,
                                 Consumer<Throwable> errorConsumer) {
        this.onNextConsumer = onNextConsumer;
        this.onCompleteTask = onCompleteTask;
        this.errorConsumer = errorConsumer;
    }

    @Override
    public void onNext(V value) {
        onNextConsumer.accept(value);
    }

    @Override
    public void onError(Throwable error) {
        errorConsumer.accept(error);
    }

    @Override
    public void onCompleted() {
        onCompleteTask.run();
    }

    private static void logError(Throwable thrown) {
        LOGGER.log(Level.INFO, thrown, () -> "Uncaught StreamObserver onError");
    }

    /**
     * Create a {@link StreamObserver} that passes values received
     * by its {@link StreamObserver#onNext(Object)} method to a
     * {@link Consumer}.
     *
     * @param onNext  the {@link Consumer} to receive the values
     * @param <T> the type of value to receive
     * @return a {@link StreamObserver}
     */
    public static <T> StreamObserver<T> create(Consumer<T> onNext) {
        return new LambdaStreamObserver<>(onNext, () -> {}, LambdaStreamObserver::logError);
    }

    /**
     * Create a {@link StreamObserver} that uses the specified lambda
     * to handle events.
     *
     * @param onNext  the {@link Consumer} to receive the values
     * @param onComplete the {@link Runnable} to execute when the observer completes
     * @param onError the {@link Consumer} to receive any errors
     * @param <T> the type of value to receive
     * @return a {@link StreamObserver}
     */
    public static <T> StreamObserver<T> create(Consumer<T> onNext, Runnable onComplete, Consumer<Throwable> onError) {
        return new LambdaStreamObserver<>(onNext, onComplete, onError);
    }
}
