/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiOnCompleteResumeWithTest {

    @Test
    public void nullFallbackPublisher() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        assertThrows(NullPointerException.class, () -> Multi.<Integer>empty()
                .onCompleteResumeWith(null)
                .subscribe(ts));

    }

    @Test
    public void fallbackToError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .onCompleteResumeWith(Multi.error(new IllegalArgumentException()))
                .subscribe(ts);


        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void errorSource() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>error(new IOException())
                .onCompleteResume(666)
                .subscribe(ts);

        ts.assertItemCount(0);
        ts.assertError(IOException.class);
    }

    @Test
    public void emptyFallback() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .onCompleteResumeWith(Multi.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void appendAfterItems() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.concat(Multi.range(1, 3), Multi.<Integer>empty())
                .onCompleteResumeWith(Multi.range(4, 2))
                .subscribe(ts);

        ts.assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void appendAfterItemsBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.concat(Multi.range(1, 3), Multi.<Integer>empty())
                .onCompleteResumeWith(Multi.range(4, 2))
                .subscribe(ts);

        ts.assertEmpty()
                .request(3)
                .assertValuesOnly(1, 2, 3)
                .request(2)
                .assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void multipleAppendAfterItemsBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.concat(Multi.<Integer>empty(), Multi.just(1, 2, 3))
                .onCompleteResumeWith(Multi.just(4, 5))
                .onCompleteResumeWith(Multi.just(6, 7))
                .subscribe(ts);

        ts.assertEmpty()
                .request(3)
                .assertValuesOnly(1, 2, 3)
                .request(2)
                .assertValuesOnly(1, 2, 3, 4, 5)
                .request(1)
                .assertValuesOnly(1, 2, 3, 4, 5, 6)
                .request(1)
                .assertResult(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    public void appendChain() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>just(1, 2, 3)
                .onCompleteResume(4)
                .onCompleteResume(5)
                .onCompleteResumeWith(Multi.just(6, 7))
                .onCompleteResume(8)
                .subscribe(ts);

        ts.assertEmpty()
                .requestMax()
                .assertComplete()
                .assertResult(1, 2, 3, 4, 5, 6, 7, 8);
    }
}
