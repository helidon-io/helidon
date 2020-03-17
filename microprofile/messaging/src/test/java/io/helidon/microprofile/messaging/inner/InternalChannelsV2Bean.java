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
 */

package io.helidon.microprofile.messaging.inner;

import java.util.concurrent.CountDownLatch;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.CountableTestBean;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Subscriber;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class InternalChannelsV2Bean implements CountableTestBean {

    public static CountDownLatch testLatch = new CountDownLatch(20);

    @Outgoing("intenal-publisher-msg")
    public int produceInt2() {
        return 5;
    }

    @Incoming("intenal-publisher-msg")
    public Subscriber<Message<Integer>> receiveMsg() {
        return ReactiveStreams.<Message<Integer>>builder()
                .limit(10)
                .map(Message::getPayload)
                .flatMap(i -> ReactiveStreams.of(i, i))
                .map(i -> Integer.toString(i))
                .forEach(s -> testLatch.countDown())
                .build();
    }

    @Override
    public CountDownLatch getTestLatch() {
        return testLatch;
    }
}
