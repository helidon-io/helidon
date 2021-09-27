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

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class MultiFlatMapOptionalTest {

    @Test
    void presentOptionals() {
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        Multi.range(1, 5)
                .flatMapOptional(Optional::of)
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.assertValues(1, 2, 3, 4, 5);
    }

    @Test
    void emptyOptionals() {
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        Multi.range(1, 5)
                .flatMapOptional(i -> Optional.<Integer>empty())
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.assertComplete();
        subscriber.assertItemCount(0);
    }

    @Test
    void mixedOptionals() {
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        Multi.range(1, 5)
                .flatMapOptional(i -> (i % 2) == 0 ? Optional.of(i) : Optional.<Integer>empty())
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.assertComplete();
        subscriber.assertValues(2, 4);
    }
}
