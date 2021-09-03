/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

import javax.enterprise.context.ApplicationScoped;

import java.util.concurrent.CountDownLatch;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class ByRequestProcessorV2Bean implements CountableTestBean {

    public static CountDownLatch testLatch = new CountDownLatch(10);

    @Outgoing("publisher-synchronous-payload")
    public PublisherBuilder<Integer> streamForProcessorOfPayloads() {
        return ReactiveStreams.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Incoming("publisher-synchronous-payload")
    @Outgoing("synchronous-payload")
    public String payloadSynchronous(int value) {
        return Integer.toString(value + 1);
    }

    @Incoming("synchronous-payload")
    public void getMessgesFromProcessorOfPayloads(String value) {
        getTestLatch().countDown();
    }

    @Override
    public CountDownLatch getTestLatch() {
        return testLatch;
    }
}
