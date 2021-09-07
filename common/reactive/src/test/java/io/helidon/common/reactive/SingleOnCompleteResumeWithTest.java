/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

public class SingleOnCompleteResumeWithTest {

    @Test
    public void fallbackFunctionCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>empty()
                .onCompleteResumeWithSingle(i -> {
                    throw new IllegalArgumentException();
                })
                .subscribe(ts);

        ts.request1();
        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void fallbackToError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>empty()
                .onCompleteResumeWithSingle(i -> Single.error(new IllegalArgumentException()))
                .subscribe(ts);

        ts.request1();
        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void emptySource() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>empty()
                .onCompleteResumeWithSingle(i -> i.map(Single::just)
                        .orElseGet(Single::empty))
                .subscribe(ts);

        ts.request1();
        ts.assertComplete();
        ts.assertValues();
    }

    @Test
    public void emptyFallback() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1)
                .onCompleteResumeWithSingle(i -> Single.empty())
                .subscribe(ts);

        ts.request1();
        ts.assertResult(1);
    }

    @Test
    void name() {
        Single.empty()
                .onCompleteResume(2)
                .collectList()
                .forSingle(System.out::println);
    }
}
