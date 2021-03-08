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
 *
 */
package io.helidon.common.reactive;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Test
public class MultiCollectorTckTest extends FlowPublisherVerification<List<Integer>> {

    public MultiCollectorTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<List<Integer>> createFlowPublisher(long l) {
        return Multi.create(() -> IntStream.range(0, (int)l).boxed().iterator()).collectStream(Collectors.toList());
    }

    @Override
    public Flow.Publisher<List<Integer>> createFailedFlowPublisher() {
        return Multi.create(() -> { throw new RuntimeException(); });
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }
}
