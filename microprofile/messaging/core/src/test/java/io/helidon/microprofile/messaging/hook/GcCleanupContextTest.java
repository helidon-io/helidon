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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.microprofile.messaging.MessageContextTestUtils;

import static org.hamcrest.Matchers.equalTo;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

public class GcCleanupContextTest extends AbstractHookTest {

    static final int GC_ATTEMPT_COUNT = 10;

    List<Integer> hashes = new ArrayList<>();


    @Outgoing("channel1")
    @MockLRA(MockLRA.Type.NEW)
    public PublisherBuilder<Message<String>> publish() {
        return ReactiveStreams.fromIterable(TEST_DATA)
                .map(Message::of);
    }

    @Incoming("channel1")
    @MockLRA(MockLRA.Type.REQUIRED)
    public CompletionStage<Void> consume(Message<String> msg) {
        hashes.add(msg.hashCode());
        return CompletableFuture.completedStage(null);
    }

    @Test
    @Override
    void run() {
        MatcherAssert.assertThat(guardedMessageCount(), equalTo(3L));

        this.lraManager.releaseMessageReferences();

        for (int i = 0; i < GC_ATTEMPT_COUNT; i++) {
            System.gc();
            if (guardedMessageCount() == 0) {
                break;
            }
        }

        MatcherAssert.assertThat(guardedMessageCount(), equalTo(0L));
    }

    long guardedMessageCount() {
        return MessageContextTestUtils.getContextMap().keySet().stream()
                .filter(m -> hashes.contains(m.hashCode()))
                .count();
    }
}
