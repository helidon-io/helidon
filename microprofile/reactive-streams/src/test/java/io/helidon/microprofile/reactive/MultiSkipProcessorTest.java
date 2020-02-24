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

package io.helidon.microprofile.reactive;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;

public class MultiSkipProcessorTest extends AbstractProcessorTest {
    @Override
    protected Processor<Long, Long> getProcessor() {
        return ReactiveStreams.<Long>builder().skip(0).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return null;
    }

    @Test
    void skipItems() throws InterruptedException, ExecutionException, TimeoutException {
        List<Long> result = ReactiveStreams.of(1L, 2L, 3L, 4L)
                .peek(System.out::println)
                .skip(2)
                .toList()
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(3L, 4L), result);
    }
}
