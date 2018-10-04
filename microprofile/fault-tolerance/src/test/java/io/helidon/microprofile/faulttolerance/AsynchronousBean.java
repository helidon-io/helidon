/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Fallback;

/**
 * Class AsynchronousBean.
 */
@Dependent
public class AsynchronousBean {

    private AtomicBoolean called = new AtomicBoolean(false);

    public boolean getCalled() {
        return called.get();
    }

    /**
     * Normal asynchronous call.
     *
     * @return A future.
     */
    @Asynchronous
    public Future<String> async() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::async", "success");
        return CompletableFuture.completedFuture("success");
    }

    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    public Future<String> asyncWithFallback() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::asyncWithFallback", "failure");
        throw new RuntimeException("Oops");
    }

    public String onFailure() {
        FaultToleranceTest.printStatus("AsynchronousBean::onFailure", "success");
        return "fallback";
    }

    /**
     * Regular test, not asynchronous.
     *
     * @return A future.
     */
    public Future<String> notAsync() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::notAsync", "success");
        return CompletableFuture.completedFuture("success");
    }

    /**
     * Asynchronmous method must return {@code Future}. Calling this method should result in
     * {@link org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException}.
     *
     * @return String value.
     */
    @Asynchronous
    public String asyncError() {
        called.set(true);
        FaultToleranceTest.printStatus("AsynchronousBean::async", "failure");
        return "failure";
    }
}
