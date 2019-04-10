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

package io.helidon.grpc.client.util;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.grpc.stub.StreamObserver;

/**
 * A simple {@link io.grpc.stub.StreamObserver} adapter class. Used internally and by test code. This
 * object simply accumulates the values received through the {@code onNext()} into a
 * {@link java.util.LinkedList}.
 *
 * Callers can use the {@code waitForCompletion} to block till all the values
 * are received (and till the {@code onCompleted} is called).
 *
 * @param <T> The type of objects received in this stream.
 *
 * @author Mahesh Kannan
 */
public class AccumulatingResponseStreamObserverAdapter<T>
        implements StreamObserver<T> {

    private LinkedList<T> result = new LinkedList<>();

    private CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

    /**
     * Create a SingleValueResponseStreamObserverAdapter.
     */
    public AccumulatingResponseStreamObserverAdapter() {
    }

    /**
     * Get the result of the accumulation.
     * @return The accumulated result.
     */
    public LinkedList<T> getResult() {
        return result;
    }

    /**
     * Get the Completablefuture that returns true when this object has accumulated
     * all values.
     *
     * @return The CompletableFuture.
     */
    public CompletableFuture<Boolean> getFuture() {
        return resultFuture;
    }

    /**
     * Wait till this object has accumulated all values.
     *
     * @return true when this object has accumulated all values.
     * @throws  java.util.concurrent.ExecutionException if any exception occurs during execution.
     * @throws  java.lang.InterruptedException if interrupted during execution.
     */
    public Boolean waitForCompletion() throws ExecutionException, InterruptedException {
        return getFuture().get();
    }

    @Override
    public void onNext(T value) {
        result.add(value);
    }

    @Override
    public void onError(Throwable t) {
        resultFuture.completeExceptionally(t);
    }

    @Override
    public void onCompleted() {
        resultFuture.complete(true);
    }

}
