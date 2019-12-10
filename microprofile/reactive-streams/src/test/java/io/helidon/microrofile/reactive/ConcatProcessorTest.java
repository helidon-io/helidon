/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

public class ConcatProcessorTest extends AbstractProcessorTest {

    @Override
    protected Publisher<Long> getPublisher(long items) {
        ;
        return ReactiveStreams.concat(
                ReactiveStreams.fromIterable(LongStream.range(0, items / 2).boxed().collect(Collectors.toList())),
                ReactiveStreams.fromIterable(LongStream.range(items / 2, items).boxed().collect(Collectors.toList()))
        ).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return ReactiveStreams.<Long>builder().peek(o -> {
            throw new TestRuntimeException();
        }).buildRs();
    }
}
