/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@SuppressWarnings("ALL")
class FaultToleranceSnippets {

    // stub
    static String getMyValue() {
        return "";
    }

    // stub
    static CompletableFuture<String> getMyValueAsync() {
        return CompletableFuture.completedFuture("");
    }

    // tag::snippet_1[]
    @Retry(maxRetries = 2, delay = 400L)
    @Fallback(fallbackMethod = "onFailure")
    String retryWithFallback() {
        return getMyValue();
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Timeout(1500)
    @CircuitBreaker(requestVolumeThreshold = 10,
                    failureRatio = .4,
                    successThreshold = 3)
    void timedCircuitBreaker() throws InterruptedException {
        //...
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    @Bulkhead(value = 2, waitingTaskQueue = 10)
    CompletableFuture<String> executeWithQueueAndFallback() {
        return getMyValueAsync();
    }
    // end::snippet_3[]

}
