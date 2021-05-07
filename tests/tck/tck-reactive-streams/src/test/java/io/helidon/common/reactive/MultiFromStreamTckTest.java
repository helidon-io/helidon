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
 *
 */
package io.helidon.common.reactive;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.Test;

import java.util.concurrent.Flow;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Test
public class MultiFromStreamTckTest extends FlowPublisherVerification<Integer> {

    public MultiFromStreamTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<Integer> createFlowPublisher(long l) {
        return Multi.create(IntStream.range(0, (int)l).boxed());
    }

    @Override
    public Flow.Publisher<Integer> createFailedFlowPublisher() {
        Stream<Integer> stream = IntStream.range(0, 10).boxed();
        stream.iterator();
        return Multi.create(stream);
    }

    @Override
    public long maxElementsFromPublisher() {
        return 10;
    }
}
