/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

class AsyncTest {
    private static final long WAIT_TIMEOUT_MILLIS = 2000;

    @Test
    void testDefaultExecutorCreate() {
        Thread thread = testAsync(Async.create());
        assertThat(thread.isVirtual(), is(true));
    }

    @Test
    void testDefaultExecutorBuilder() {
        Async async = Async.create();
        Thread thread = testAsync(async);
        assertThat(thread.isVirtual(), is(true));
    }

    @Test
    void testCustomExecutorBuilder() {
        Async async = AsyncConfig.builder()
                .executor(FaultTolerance.executor().get())
                .build();
        Thread thread = testAsync(async);
        assertThat(thread.isVirtual(), is(true));
    }

    @Test
    void testThreadName() throws Exception {
        String threadName = Async.create()
                .invoke(() -> Thread.currentThread().getName())
                .get(10, TimeUnit.SECONDS);

        assertThat(threadName, startsWith("helidon-ft-"));
        assertThat(threadName, endsWith(": async"));
    }

    private Thread testAsync(Async async) {
        try {
            CompletableFuture<Thread> cf = new CompletableFuture<>();
            async.invoke(() -> {
                cf.complete(Thread.currentThread());
                return null;
            });
            return cf.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
