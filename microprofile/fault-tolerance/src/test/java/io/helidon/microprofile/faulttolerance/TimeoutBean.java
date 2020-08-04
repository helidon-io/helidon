/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Class TimeoutBean.
 */
@Dependent
@Retry(maxRetries = 2)
public class TimeoutBean {

    private AtomicLong duration = new AtomicLong(1600);

    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    public String forceTimeout() throws InterruptedException {
        FaultToleranceTest.printStatus("TimeoutBean::forceTimeout()", "failure");
        Thread.sleep(1500);
        return "failure";
    }

    @Asynchronous
    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    public Future<String> forceTimeoutAsync() throws InterruptedException {
        FaultToleranceTest.printStatus("TimeoutBean::forceTimeoutAsync()", "failure");
        Thread.sleep(1500);
        return CompletableFuture.completedFuture("failure");
    }

    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    public String noTimeout() throws InterruptedException {
        FaultToleranceTest.printStatus("TimeoutBean::noTimeout()", "success");
        Thread.sleep(500);
        return "success";
    }

    // See class annotation @Retry(maxRetries = 2)
    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    public String timeoutWithRetries() throws InterruptedException {
        FaultToleranceTest.printStatus("TimeoutBean::timeoutWithRetries()",
                    duration.get() < 1000 ? "success" : "failure");
        Thread.sleep(duration.getAndAdd(-400));     // needs 2 retries
        return duration.get() < 1000 ? "success" : "failure";
    }

    @Fallback(fallbackMethod = "onFailure")
    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    public String timeoutWithFallback() throws InterruptedException {
        FaultToleranceTest.printStatus("TimeoutBean::forceTimeoutWithFallback()", "failure");
        Thread.sleep(1500);
        return "failure";
    }

    // See class annotation @Retry(maxRetries = 2)
    @Fallback(fallbackMethod = "onFailure")
    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    public String timeoutWithRetriesAndFallback() throws InterruptedException {
        FaultToleranceTest.printStatus("TimeoutBean::timeoutWithRetriesAndFallback()", "failure");
        Thread.sleep(duration.getAndAdd(-100));     // not enough, need fallback
        return "failure";
    }

    public String onFailure() {
        FaultToleranceTest.printStatus("TimeoutBean::onFailure()", "success");
        return "fallback";
    }
}
