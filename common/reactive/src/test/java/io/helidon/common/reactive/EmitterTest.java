/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 */

package io.helidon.common.reactive;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class EmitterTest {

    @Test
    void testBackPressure() {
        BufferedEmittingPublisher<Integer> emitter = BufferedEmittingPublisher.create();

        List<Integer> data = IntStream.range(0, 10)
                .boxed()
                .collect(Collectors.toList());


        data.forEach(emitter::emit);

        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        emitter.subscribe(subscriber);

        subscriber
                .assertEmpty()
                .request(1)
                .assertItemCount(1)
                .request(2)
                .assertItemCount(3)
                .assertNotTerminated();

        assertThat(emitter.emit(10), is(equalTo(8)));

        subscriber
                .request(3)
                .assertItemCount(6);

        assertThat(emitter.emit(11), is(equalTo(6)));

        subscriber.requestMax()
                .assertNotTerminated();

        emitter.complete();
        subscriber
                .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
                .assertComplete();
    }
}
