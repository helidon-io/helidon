/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microrofile.reactive;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;

public class MultiLimitProcessorTest extends AbstractProcessorTest {
    @Override
    protected Processor<Long, Long> getProcessor() {
        return ReactiveStreams.<Long>builder().limit(Long.MAX_VALUE).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return null;
    }

    @Test
    void ignoreErrorsAfterDone() {
        MockPublisher p = new MockPublisher();
        testProcessor(ReactiveStreams.<Long>fromPublisher(p).limit(2).buildRs(), s -> {
            s.request(4);
            p.sendNext(2);
            p.sendNext(4);
            s.expectSum(6);
            p.sendNext(8);
            s.expectSum(6);
            p.sendOnError(new TestThrowable());
        });
    }

    @Test
    void ignoreErrorsAfterDone2() throws InterruptedException, ExecutionException, TimeoutException {
        AtomicLong seq = new AtomicLong(0);
        List<Long> result = ReactiveStreams.generate(seq::incrementAndGet).flatMap((i) -> {
            return i == 4 ? ReactiveStreams.failed(new RuntimeException("failed")) : ReactiveStreams.of(i);
        })
                .limit(3L)
                .toList()
                .run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(1L, 2L, 3L), result);
    }
}
