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

public class MultiIntervalTest {

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
    public void normal() {
        TestSubscriber<Long> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.interval(1, TimeUnit.MILLISECONDS, executor)
                .subscribe(ts);

        ts.awaitCount(10)
                .cancel()
                .assertNotTerminated();
    }

    @Test
    public void normalBackpressured() throws Exception {
        TestSubscriber<Long> ts = new TestSubscriber<>();

        Multi.interval(10, TimeUnit.MILLISECONDS, executor)
                .subscribe(ts);

        Thread.sleep(100);

        ts.assertEmpty()
        .request(15);

        ts.awaitCount(15)
                .cancel()
                .assertNotTerminated();
    }
}
