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

package io.helidon.microprofile.messaging.inner.subscriber;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class SubscriberPublMsgToPaylRetComplStringBean implements AssertableTestBean {

    CopyOnWriteArraySet<String> RESULT_DATA = new CopyOnWriteArraySet<>();
    private final CountDownLatch countDownLatch = new CountDownLatch(TEST_DATA.size());

    @Outgoing("cs-string-payload")
    public Publisher<Message<String>> sourceForCsStringPayload() {
        return ReactiveStreams.fromIterable(TEST_DATA).map(Message::of).buildRs();
    }


    @Incoming("cs-string-payload")
    public CompletionStage<String> consumePayloadAndReturnCompletionStageOfString(String payload) {
        return CompletableFuture.supplyAsync(() -> {
            RESULT_DATA.add(payload);
            countDownLatch.countDown();
            return "test";
        }, Executors.newWorkStealingPool());
    }

    @Override
    public void assertValid() {
        try {
            countDownLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail(e);
        }
        assertTrue(RESULT_DATA.containsAll(TEST_DATA));
        assertEquals(TEST_DATA.size(), RESULT_DATA.size());
    }
}
