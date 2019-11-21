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

package io.helidon.microprofile.messaging.inner.ack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.CompletableTestBean;
import io.helidon.microprofile.reactive.MultiRS;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class ChainWithPayloadAckBean implements CompletableTestBean {

    private CompletableFuture<Void> future = new CompletableFuture<>();

    @Outgoing("inner-processor")
    public Publisher<Message<String>> produceMessage() {
        return MultiRS.just(Message.of("test-data", () -> future));
    }

    @Incoming("inner-processor")
    @Outgoing("inner-consumer")
    public String process(String msg) {
        return msg.toUpperCase();
    }

    @Incoming("inner-consumer")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public void receiveMessage(Message<String> msg) {
        msg.ack().toCompletableFuture().complete(null);
    }

    @Override
    public CompletionStage getTestCompletion() {
        return future;
    }
}
