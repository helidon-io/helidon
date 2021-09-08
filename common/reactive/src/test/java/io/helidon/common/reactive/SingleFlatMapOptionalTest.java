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
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class SingleFlatMapOptionalTest {

    @Test
    void presentOptional() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Single.just(Optional.of("test"))
                .flatMapOptional(Function.identity())
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.assertComplete();
        subscriber.assertValues("test");
    }

    @Test
    void emptyOptional() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Single.just(Optional.empty())
                .flatMapOptional(Function.identity())
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.assertComplete();
        subscriber.assertItemCount(0);
    }
}
