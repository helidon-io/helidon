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

package io.helidon.common.reactive;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests cancellation of {@code Future} and {@code Single}.
 */
public class CancellationTest {

    /**
     * Test cancellation of underlying {@code CompletableFuture} when wrapped
     * by a {@code Single}.
     */
    @Test
    public void testCompletableFutureCancel() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<Object> future = new CompletableFuture<>();
        Single<Object> single = Single.create(future, true);
        future.whenComplete((o, t) -> {
            if (t instanceof CancellationException) {
                cancelled.set(true);
            }
        });
        single.cancel();        // should cancel future
        assertThat(cancelled.get(), is(true));
    }

    /**
     * Test cancellation of {@code Single} after it has been converted to
     * a {@code CompletableFuture}.
     */
    @Test
    public void testSingleCancel() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Single<Object> single = Single.create(new CompletableFuture<>());
        CompletableFuture<Object> future = single.toStage().toCompletableFuture();
        single.whenComplete((o, t) -> {
            if (t instanceof CancellationException) {
                cancelled.set(true);
            }
        });
        single.cancel();
        future.cancel(true);        // should cancel single
        assertThat(cancelled.get(), is(true));
    }
}
