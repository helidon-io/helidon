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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class MultiRangeTest {

    @Test
    public void cancelAfterItem() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 3)
                .limit(2)
                .subscribe(ts);

        ts.requestMax();

        ts.assertResult(1, 2);
    }

    @Test
    public void negativeCount() {
        assertThrows(IllegalArgumentException.class, () -> Multi.range(1, -1));
    }

    @Test
    public void zero() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 0)
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void one() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.range(1, 1)
                .subscribe(ts);

        ts.assertResult(1);
    }
}
