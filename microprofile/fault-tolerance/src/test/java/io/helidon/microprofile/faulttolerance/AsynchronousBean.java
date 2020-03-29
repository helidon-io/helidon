/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Fallback;

/**
 * Class AsynchronousBean.
 */
@Dependent
public class AsynchronousBean {

    private boolean called;

    public boolean wasCalled() {
        return called;
    }

    /**
     * Normal asynchronous call.
     *
     * @return A future.
     */
    @Asynchronous
    public Future<String> async() {
        called = true;
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
    public Future<String> asyncWithFallback() {
        called = true;
        FaultToleranceTest.printStatus("AsynchronousBean::asyncWithFallback", "failure");
        throw new RuntimeException("Oops");
    }

    public CompletableFuture<String> onFailure() {
        FaultToleranceTest.printStatus("AsynchronousBean::onFailure", "success");
        return CompletableFuture.completedFuture("fallback");
    }

    /**
     * Regular test, not asynchronous.
     *
     * @return A future.
     */
    public Future<String> notAsync() {
        called = true;
        FaultToleranceTest.printStatus("AsynchronousBean::notAsync", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Normal asynchronous call using {@link java.util.concurrent.CompletionStage}.
     *
     * @return A completion stage.
     */
    @Asynchronous
    public CompletionStage<String> asyncCompletionStage() {
        called = true;
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
    public CompletionStage<String> asyncCompletionStageWithFallback() {
        called = true;
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletionStageWithFallback", "failure");
        throw new RuntimeException("Oops");
    }

    /**
     * Normal asynchronous call using {@link java.util.concurrent.CompletableFuture}.
     *
     * @return A completable future.
     */
    @Asynchronous
    public CompletableFuture<String> asyncCompletableFuture() {
        called = true;
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
    public CompletableFuture<String> asyncCompletableFutureWithFallback() {
        called = true;
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
    public CompletableFuture<String> asyncCompletableFutureWithFallbackFailure() {
        called = true;
        FaultToleranceTest.printStatus("AsynchronousBean::asyncCompletableFutureWithFallbackFailure", "failure");
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IOException("oops"));
        return future;
    }
}
