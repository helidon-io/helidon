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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class MultiOnErrorResumeTest {

    @Test
    public void fallbackFunctionCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>error(new IOException())
                .onErrorResume(e -> { throw new IllegalArgumentException(); })
                .subscribe(ts);


        ts.assertFailure(IllegalArgumentException.class);

        assertThat(ts.getLastError().getSuppressed()[0], instanceOf(IOException.class));
    }

    @Test
    public void emptySource() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .onErrorResume(Object::hashCode)
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void failAfterItems() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.concat(Multi.range(1, 3), Multi.<Integer>error(new IOException()))
                .onErrorResume(v -> 4)
                .subscribe(ts);

        ts.assertResult(1, 2, 3, 4);
    }

    @Test
    public void failAfterItemsBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.concat(Multi.range(1, 3), Multi.<Integer>error(new IOException()))
                .onErrorResume(v -> 4)
                .subscribe(ts);

        ts.assertEmpty()
                .request(3)
                .assertValuesOnly(1, 2, 3)
                .request(1)
                .assertResult(1, 2, 3, 4);
    }

    @Test
    public void noSelfSuppressionFailure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>error(new IllegalArgumentException())
                .onErrorResume(e -> { throw (IllegalArgumentException)e; })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }
}
