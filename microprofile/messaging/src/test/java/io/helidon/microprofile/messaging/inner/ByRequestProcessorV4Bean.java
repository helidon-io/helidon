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

package io.helidon.microprofile.messaging.inner;

import io.helidon.microprofile.messaging.CountableTestBean;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

import javax.enterprise.context.ApplicationScoped;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

@ApplicationScoped
public class ByRequestProcessorV4Bean implements CountableTestBean {

    public static CountDownLatch testLatch = new CountDownLatch(10);

    @Outgoing("publisher-asynchronous-message")
    public PublisherBuilder<Integer> streamForProcessorBuilderOfMessages() {
        return ReactiveStreams.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Incoming("publisher-asynchronous-message")
    @Outgoing("asynchronous-message")
    public CompletionStage<Message<String>> messageAsynchronous(Message<Integer> message) {
        return CompletableFuture.supplyAsync(() -> Message.of(Integer.toString(message.getPayload() + 1)), Executors.newSingleThreadExecutor());
    }

    @Incoming("asynchronous-message")
    public void getMessgesFromProcessorBuilderOfMessages(String value) {
        getTestLatch().countDown();
    }

    @Override
    public CountDownLatch getTestLatch() {
        return testLatch;
    }
}
