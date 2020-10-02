/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Fallback;

/**
 * A stateful bean that defines some async methods.
 */
class AsynchronousBean {

    private AtomicBoolean called = new AtomicBoolean(false);

    boolean wasCalled() {
        return called.get();
    }

    void reset() {
        called.set(false);
    }

    /**
     * Normal asynchronous call.
     *
     * @return A future.
     */
    @Asynchronous
    CompletableFuture<String> async() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::async", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Async call with fallback.
     *
     * @return A future.
     */
    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    CompletableFuture<String> asyncWithFallback() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncWithFallback", "failure");
        return CompletableFuture.failedFuture(new RuntimeException("Oops"));
    }

    CompletableFuture<String> onFailure() {
        FaultToleranceTest.printStatus("AsynchronousBean::onFailure", "success");
        return CompletableFuture.completedFuture("fallback");
    }

    /**
     * Async call with fallback and Future. Fallback should be ignored in this case.
     *
     * @return A future.
     */
    @Asynchronous
    @Fallback(fallbackMethod = "onFailureFuture")
    Future<String> asyncWithFallbackFuture() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncWithFallbackFuture", "failure");
        return CompletableFuture.failedFuture(new RuntimeException("Oops"));
    }

    Future<String> onFailureFuture() {
        FaultToleranceTest.printStatus("AsynchronousBean::onFailure", "success");
        return CompletableFuture.completedFuture("fallback");
    }


    /**
     * Regular test, not asynchronous.
     *
     * @return A future.
     */
    CompletableFuture<String> notAsync() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::notAsync", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Normal asynchronous call using {@link java.util.concurrent.CompletionStage}.
     *
     * @return A completion stage.
     */
    @Asynchronous
    CompletionStage<String> asyncCompletionStage() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletionStage", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Async call with fallback.
     *
     * @return A completion stage.
     */
    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    CompletionStage<String> asyncCompletionStageWithFallback() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletionStageWithFallback", "failure");
        return CompletableFuture.failedFuture(new RuntimeException("Oops"));
    }

    /**
     * Normal asynchronous call using {@link java.util.concurrent.CompletableFuture}.
     *
     * @return A completable future.
     */
    @Asynchronous
    CompletableFuture<String> asyncCompletableFuture() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletableFuture", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Async call with fallback using {@link java.util.concurrent.CompletableFuture}.
     *
     * @return A completable future.
     */
    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    CompletableFuture<String> asyncCompletableFutureWithFallback() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletableFutureWithFallback", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Async call with fallback using a {@link java.util.concurrent.CompletableFuture}
     * that fails.
     *
     * @return A completable future.
     */
    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    CompletableFuture<String> asyncCompletableFutureWithFallbackFailure() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletableFutureWithFallbackFailure", "failure");
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IOException("oops"));
        return future;
    }
}
