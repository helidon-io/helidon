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
package io.helidon.common.reactive;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

public class MultiConcatArrayTest {

    @Test
    public void errors() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.concatArray(Multi.singleton(1), Multi.error(new IOException()), Multi.singleton(2))
                .subscribe(ts);

        ts.assertFailure(IOException.class, 1);
    }

    @Test
    public void cancelOnNegativeNumberTest() throws InterruptedException, TimeoutException, ExecutionException {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        CompletableFuture<Void> cancelled = new CompletableFuture<>();

        Multi.concatArray(Multi.just(1, 2).onCancel(() -> cancelled.complete(null)), Multi.just(3, 4))
                .subscribe(ts);
        ts.request(1);
        ts.getSubcription().request(-1);

        cancelled.get(200, TimeUnit.MILLISECONDS);
        ts.assertFailure(IllegalArgumentException.class, 1);
    }

    @Test
    public void millionSources() {
        @SuppressWarnings("unchecked")
        Multi<Integer>[] sources = new Multi[1_000_000];
        Arrays.fill(sources, Multi.singleton(1));

        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.concatArray(sources)
                .subscribe(ts);

        ts.assertItemCount(1_000_000)
                .assertComplete();
    }

}
