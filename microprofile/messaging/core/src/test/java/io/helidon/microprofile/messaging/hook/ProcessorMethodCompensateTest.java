/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.hook;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class ProcessorMethodCompensateTest extends AbstractHookTest {

    CompletableFuture<String> compensated = new CompletableFuture<>();

    @Outgoing("channel1")
    public PublisherBuilder<Message<String>> publish() {
        return ReactiveStreams.fromIterable(TEST_DATA)
                .map(Message::of);
    }

    @Incoming("channel1")
    @Outgoing("channel2")
    @MockLRA(MockLRA.Type.NEW)
    public Message<String> process(Message<String> msg) {
        return msg;
    }

    @Incoming("channel2")
    @MockLRA(MockLRA.Type.REQUIRED)
    public CompletionStage<Void> consume(Message<String> msg) {
        if (TEST_DATA.get(1).equals(msg.getPayload())) {
            throw new RuntimeException("xxx");
        }
        addActual(msg);
        return CompletableFuture.completedStage(null);
    }

    @MockCompensate
    public void compensate(Message<String> message) {
        compensated.complete(message.getPayload());
    }

    @Override
    protected List<String> expected() {
        return List.of(super.expected().get(0));
    }

    @Test
    void compensationTest() throws InterruptedException, ExecutionException, TimeoutException {
        assertThat(compensated.get(2, TimeUnit.SECONDS), Matchers.equalTo(super.TEST_DATA.get(1)));
    }
}
