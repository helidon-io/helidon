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

import java.util.function.Function;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;

public class MultiPeekProcessorTest extends AbstractProcessorTest {
    @Override
    protected Processor<Long, Long> getProcessor() {
        return ReactiveStreams.<Long>builder().peek(i -> Function.identity()).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return ReactiveStreams.<Long>builder().peek(i -> {
            throw t;
        }).buildRs();
    }
}
