/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.inner.msg;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.messaging.CountableTestBean;
import io.helidon.microprofile.messaging.ExtendedMessage;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class ExtendedMessageV1Bean implements CountableTestBean {

    private static final Set<String> TEST_DATA = new HashSet<>(Arrays.asList("test1", "test2"));
    public static CountDownLatch testLatch = new CountDownLatch(TEST_DATA.size()*2);

    @Outgoing("extended-msg-v1-string-1")
    public Publisher<ExtendedMessage<String>> produceMessage() {
        return FlowAdapters.toPublisher(Multi.create(() -> TEST_DATA.stream().iterator())
                .map(payload -> ExtendedMessage.of(payload, () -> {
                    testLatch.countDown();
                    return CompletableFuture.completedStage(null);
                })));
    }

    @Incoming("extended-msg-v1-string-1")
    @Outgoing("extended-msg-v1-string-2")
    public ProcessorBuilder<Message<String>, ExtendedMessage<String>> processor_processor_builder_msg_2_void1() {
        return ReactiveStreams.<Message<String>>builder().map((Message<String> m) -> ((ExtendedMessage<String>) m));
    }

    @Incoming("extended-msg-v1-string-2")
    @Outgoing("extended-msg-v1-string-3")
    public ProcessorBuilder<ExtendedMessage<String>, ExtendedMessage<String>> processor_processor_builder_msg_2_void2() {
        return ReactiveStreams.<ExtendedMessage<String>>builder().map(Function.identity());
    }

    @Incoming("extended-msg-v1-string-3")
    @Outgoing("extended-msg-v1-string-4")
    public ProcessorBuilder<ExtendedMessage<String>, Message<String>> processor_processor_builder_msg_2_void3() {
        return ReactiveStreams.<ExtendedMessage<String>>builder().map(Function.identity());
    }

    @Incoming("extended-msg-v1-string-4")
    @Outgoing("extended-msg-v1-string-5")
    public Processor<Message<String>, ExtendedMessage<String>> processor_processor_builder_msg_2_void4() {
        return ReactiveStreams.<Message<String>>builder().map((Message<String> m) -> ((ExtendedMessage<String>) m)).buildRs();
    }

    @Incoming("extended-msg-v1-string-5")
    @Outgoing("extended-msg-v1-string-6")
    public Processor<ExtendedMessage<String>, ExtendedMessage<String>> processor_processor_builder_msg_2_void5() {
        return ReactiveStreams.<ExtendedMessage<String>>builder().map(Function.identity()).buildRs();
    }

    @Incoming("extended-msg-v1-string-6")
    @Outgoing("extended-msg-v1-string-7")
    public Processor<ExtendedMessage<String>, Message<String>> processor_processor_builder_msg_2_void6() {
        return ReactiveStreams.<ExtendedMessage<String>>builder().map(m -> (Message<String>) m).buildRs();
    }

    @Incoming("extended-msg-v1-string-7")
    @Outgoing("extended-msg-v1-string-8")
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    public Processor<ExtendedMessage<String>, String> processor_processor_builder_msg_2_void7() {
        return ReactiveStreams.<ExtendedMessage<String>>builder().map(ExtendedMessage::getPayload).buildRs();
    }

    @Incoming("extended-msg-v1-string-8")
    public CompletionStage<Void> receiveMethod(String msg) {
        if (TEST_DATA.contains(msg)) {
            testLatch.countDown();
        }
        return CompletableFuture.completedStage(null);
    }

    @Override
    public CountDownLatch getTestLatch() {
        return testLatch;
    }
}
