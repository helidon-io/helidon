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

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class SingleRetryTckTest extends FlowPublisherVerification<Long> {

    public SingleRetryTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<Long> createFlowPublisher(long l) {
        return Single.<Long>defer(() -> {
            AtomicInteger count = new AtomicInteger();
            return Single.<Long>defer(() ->
                    count.incrementAndGet() < 5
                            ? Single.error(new IOException()) : Single.just(1L))
                    .retry(10);
        });
    }

    @Override
    public Flow.Publisher<Long> createFailedFlowPublisher() {
        return Single.<Long>error(new IOException())
                .retry(0L);
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }
}
