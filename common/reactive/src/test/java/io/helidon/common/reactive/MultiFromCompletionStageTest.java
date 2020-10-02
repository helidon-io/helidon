/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletableFuture;

import org.testng.annotations.Test;

public class MultiFromCompletionStageTest {

    @Test
    public void nullItemDisallowed() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.create(CompletableFuture.completedStage(null))
        .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
    }

    @Test
    public void nullItemDisallowed2() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.create(CompletableFuture.completedStage(null), false)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
    }

    @Test
    public void nullItemMeansEmpty() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.create(CompletableFuture.completedStage(null), true)
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void cancel() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        Multi.create(cf)
                .subscribe(ts);

        ts.cancel();

        cf.complete(1);

        ts.assertEmpty();
    }

}
