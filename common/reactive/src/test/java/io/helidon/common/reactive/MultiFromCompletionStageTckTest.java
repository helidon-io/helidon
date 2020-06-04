/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.Test;

@Test
public class MultiFromCompletionStageTckTest extends FlowPublisherVerification<Long> {

    public MultiFromCompletionStageTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<Long> createFlowPublisher(long l) {
        CompletableFuture<Long> cf = CompletableFuture.completedFuture(1L);
        return Multi.create(cf);
    }

    @Override
    public Flow.Publisher<Long> createFailedFlowPublisher() {
        CompletableFuture<Long> cf = new CompletableFuture<>();
        cf.completeExceptionally(new IOException());
        return Multi.create(cf);
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }
}
