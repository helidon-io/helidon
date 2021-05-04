/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class MultiReduceFullTest {

    @Test
    public void empty() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.<Integer>empty().reduce(() -> 100, Integer::sum)
                .subscribe(ts);

        ts.assertResult(100);
    }

    @Test
    public void single() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.singleton(1).reduce(() -> 100, Integer::sum)
                .subscribe(ts);

        ts.assertResult(101);
    }

    @Test
    public void range() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.range(1, 10).reduce(() -> 100, Integer::sum)
                .subscribe(ts);

        ts.assertResult(155);
    }

    @Test
    public void error() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.<Integer>error(new IOException()).reduce(() -> 100, Integer::sum)
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void supplierNull() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 5)
                .reduce(() -> null, Integer::sum)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
    }

    @Test
    public void supplierCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 5)
                .reduce(() -> { throw new IllegalArgumentException(); }, Integer::sum)
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void reducerNull() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 5)
                .reduce(() -> 100, (a, b) -> null)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
    }

    @Test
    public void reducerCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 5)
                .reduce(() -> 100, (a, b) -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void cancel() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.<Integer>never()
                .reduce(() -> 100, Integer::sum)
                .subscribe(ts);

        ts.cancel();

        ts.assertEmpty();
    }
}
