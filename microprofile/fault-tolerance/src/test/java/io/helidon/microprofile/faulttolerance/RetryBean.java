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
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;

import static io.helidon.microprofile.faulttolerance.FaultToleranceTest.printStatus;

/**
 * Class RetryBean.
 */
@Dependent
@Retry(maxRetries = 2, delay = 50L)
public class RetryBean {

    private AtomicInteger invocations = new AtomicInteger(0);

    public int getInvocations() {
        return invocations.get();
    }

    // See class annotation @Retry(maxRetries = 2, delay = 50L)
    public String retry() {
        if (invocations.incrementAndGet() <= 2) {
            printStatus("RetryBean::retryOne()", "failure");
            throw new RuntimeException("Oops");
        }
        printStatus("RetryBean::retryOne()", "success");
        return "success";
    }

    @Retry(maxRetries = 1, jitter = 400L)
    @Fallback(fallbackMethod = "onFailure")
    public String retryWithFallback() {
        if (invocations.incrementAndGet() <= 2) {
            printStatus("RetryBean::retryWithFallback()", "failure");
            throw new RuntimeException("Oops");
        }
        printStatus("RetryBean::retryWithFallback()", "success");
        return "success";
    }

    public String onFailure() {
        printStatus("RetryBean::onFailure()", "success");
        return "fallback";
    }

    @Asynchronous
    @Retry(maxRetries = 2)
    public Future<String> retryAsync() {
        if (invocations.incrementAndGet() <= 2) {
            printStatus("RetryBean::retryAsync()", "failure");
            throw new RuntimeException("Oops");
        }
        printStatus("RetryBean::retryAsync()", "success");
        return CompletableFuture.completedFuture("success");
    }

    @Retry(maxRetries = 4, delay = 100L, jitter = 50L)
    public String retryWithDelayAndJitter() {
        if (invocations.incrementAndGet() <= 4) {
            printStatus("RetryBean::retryWithDelayAndJitter()",
                        "failure " + System.currentTimeMillis());
            throw new RuntimeException("Oops");
        }
        printStatus("RetryBean::retryWithDelayAndJitter()",
                    "success " + System.currentTimeMillis());
        return "success";
    }
}
