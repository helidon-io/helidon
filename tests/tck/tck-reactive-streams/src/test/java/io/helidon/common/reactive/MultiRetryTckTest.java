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
 */

package io.helidon.common.reactive;

import java.io.IOException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.Test;

@Test
public class MultiRetryTckTest extends FlowPublisherVerification<Long> {

    public MultiRetryTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<Long> createFlowPublisher(long l) {
        return Multi.<Long>defer(() -> {
            AtomicInteger count = new AtomicInteger();
            return Multi.<Long>defer(() ->
                    count.incrementAndGet() < 5
                            ? Multi.<Long>error(new IOException()) : Multi.rangeLong(0L, l))
                    .retry(10);
        });
    }

    @Override
    public Flow.Publisher<Long> createFailedFlowPublisher() {
        return Multi.<Long>error(new IOException())
                .retry(0L);
    }

    @Override
    public long maxElementsFromPublisher() {
        return 10;
    }
}
