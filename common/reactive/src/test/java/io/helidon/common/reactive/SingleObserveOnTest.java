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
 *
 */
package io.helidon.common.reactive;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SingleObserveOnTest {

    private static ScheduledExecutorService executor;

    @BeforeAll
    public static void beforeClass() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    public static void afterClass() {
        executor.shutdown();
    }

    @Test
    public void empty() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.empty()
                .observeOn(executor)
                .subscribe(ts);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertResult();
    }

    // The TCK doesn't test this for sequences shorter than 10 for some reason
    @Test
    public void zeroRequest() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.never()
                .observeOn(executor)
                .subscribe(ts);

        ts.getSubcription().request(0L);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertFailure(IllegalArgumentException.class);
    }

    // The TCK doesn't test this for sequences shorter than 10 for some reason
    @Test
    public void negativeRequest() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.never()
                .observeOn(executor)
                .subscribe(ts);

        ts.getSubcription().request(-1L);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertFailure(IllegalArgumentException.class);
    }
}
