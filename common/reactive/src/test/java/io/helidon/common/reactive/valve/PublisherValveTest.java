/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.common.reactive.SubmissionPublisher;
import org.junit.jupiter.api.Test;

import static io.helidon.common.reactive.valve.TestUtils.generate;
import static io.helidon.common.reactive.valve.TestUtils.generateList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublisherValveTest {

    @Test
    void publish() throws Exception {
        SubmissionPublisher<Integer> pub = new SubmissionPublisher<>();
        PublisherValve<Integer> valve = new PublisherValve<>(pub);
        CompletableFuture<List<Integer>> cf = valve.collect(Collectors.toList()).toCompletableFuture();
        generate(0, 10, pub::submit);
        pub.close();
        assertEquals(generateList(0, 10), cf.get());
    }

    @Test
    void twoHandlers() {
        List<Integer> buffer = Collections.synchronizedList(new ArrayList<>(10));
        SubmissionPublisher<Integer> pub = new SubmissionPublisher<>();
        PublisherValve<Integer> valve = new PublisherValve<>(pub);
        valve.handle((Consumer<Integer>) buffer::add);
        assertThrows(IllegalStateException.class, () -> valve.handle((Consumer<Integer>) buffer::add));
    }

    @Test
    void pauseResume() throws Exception {
        List<Integer> buffer = Collections.synchronizedList(new ArrayList<>(10));
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        SubmissionPublisher<Integer> pub = new SubmissionPublisher<>();
        Valve<Integer> valve = Valves.from(pub);
        valve.handle((i, p) -> {
            buffer.add(i);
            if (i == 5) {
                p.pause();
                latch.countDown();
            }
        }, null, doneLatch::countDown);
        generate(0, 10, pub::submit);
        pub.close();
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Wait timeout!");
        }
        assertEquals(generateList(0, 6), buffer);
        assertEquals(1, doneLatch.getCount());
        valve.resume();
        if (!doneLatch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Wait timeout!");
        }
        assertEquals(generateList(0, 10), buffer);
    }
}
