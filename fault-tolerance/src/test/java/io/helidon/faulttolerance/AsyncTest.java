/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncTest {
    private final AtomicInteger syncCounter = new AtomicInteger();

    @BeforeEach
    void reset() {
        syncCounter.set(0);
    }

    @Test
    void testAsync() {
        Thread result = Async.create()
                .invoke(this::sync)
                .await(1, TimeUnit.SECONDS);

        assertThat(result, is(not(Thread.currentThread())));
        assertThat(syncCounter.get(), is(1));
    }

    @Test
    void testAsyncError() {
        Single<String> result = Async.create()
                .invoke(this::syncError);

        CompletionException exception = assertThrows(CompletionException.class, () -> result.await(1, TimeUnit.SECONDS));

        Throwable cause = exception.getCause();

        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(MyException.class));
    }

    private List<String> syncList() {
        return List.of("hi", "there");
    }

    private String syncError() {
        throw new MyException();
    }

    private Thread sync() {
        syncCounter.incrementAndGet();
        return Thread.currentThread();
    }

    private static class MyException extends RuntimeException {
    }
}