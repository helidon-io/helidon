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

package io.helidon.microprofile.messaging.inner;

import io.helidon.microprofile.messaging.CountableTestBean;
import io.helidon.microprofile.reactive.MultiRS;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Publisher;

import javax.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@ApplicationScoped
public class MultipleTypeProcessorChainV1Bean implements CountableTestBean {
    public static Set<String> TEST_DATA = new HashSet<>(Arrays.asList("teST1", "TEst2", "tESt3"));
    public static Set<String> EXPECTED_DATA = TEST_DATA.stream()
            .map(String::toLowerCase)
            .map(s -> s + "-processed")
            .collect(Collectors.toSet());
    public static CountDownLatch testLatch = new CountDownLatch(TEST_DATA.size());

    @Outgoing("inner-processor")
    public Publisher<String> produceMessage() {
        return MultiRS.just(TEST_DATA.stream());
    }

    @Incoming("inner-processor")
    @Outgoing("inner-processor-2")
    public PublisherBuilder<String> process(PublisherBuilder<String> msg) {
        return msg.map(s -> s.toLowerCase());
    }

    @Incoming("inner-processor-2")
    @Outgoing("inner-consumer")
    public String process2(String msg) {
        return msg + "-processed";
    }

    @Incoming("inner-consumer")
    public void receiveMessage(String msg) {
        if (EXPECTED_DATA.contains(msg)) {
            testLatch.countDown();
        }
    }

    @Override
    public CountDownLatch getTestLatch() {
        return testLatch;
    }
}
