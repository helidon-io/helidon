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

import java.util.concurrent.Flow;
import java.util.stream.IntStream;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;

public class MultiOnErrorTckTest extends FlowPublisherVerification<Integer> {

    public MultiOnErrorTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<Integer> createFlowPublisher(long l) {
        return Multi.create(() -> IntStream.range(0, (int)l).boxed().iterator())
                .onError(e -> { });
    }

    @Override
    public Flow.Publisher<Integer> createFailedFlowPublisher() {
        return null;
    }

    @Override
    public long maxElementsFromPublisher() {
        return 10;
    }
}
