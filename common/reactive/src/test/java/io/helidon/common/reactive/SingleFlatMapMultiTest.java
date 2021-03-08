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

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.jupiter.api.Test;

public class SingleFlatMapMultiTest {
    @Test
    public void emptySource() {
        AtomicInteger calls = new AtomicInteger();
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.empty()
                .flatMap(v -> {
                    calls.getAndIncrement();
                    return Multi.just(1, 2, 3);
                })
                .subscribe(ts);
        ;

        ts.assertResult();
        assertThat(calls.get(), is(0));
    }
}
