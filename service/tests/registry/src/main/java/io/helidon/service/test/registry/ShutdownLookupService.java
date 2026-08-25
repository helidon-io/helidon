/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.test.registry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.service.registry.Service;

@Service.Singleton
class ShutdownLookupService {
    private static volatile CountDownLatch destroyStarted = new CountDownLatch(1);
    private static volatile CountDownLatch lookupCompleted = new CountDownLatch(1);

    static void reset() {
        destroyStarted = new CountDownLatch(1);
        lookupCompleted = new CountDownLatch(1);
    }

    static void awaitDestroyStarted() {
        await(destroyStarted, "Timed out waiting for service destruction to start.");
    }

    static void lookupCompleted() {
        lookupCompleted.countDown();
    }

    @Service.PreDestroy
    void preDestroy() {
        destroyStarted.countDown();
        await(lookupCompleted, "Timed out waiting for the shutdown lookup to finish.");
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating the shutdown test.", e);
        }
    }
}
