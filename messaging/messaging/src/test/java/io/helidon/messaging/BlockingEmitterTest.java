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

package io.helidon.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BlockingEmitterTest {

    @Test
    void emit() throws InterruptedException {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3", "test4"));

        Channel<String> simpleChannel = Channel.create("simple-channel");

        Emitter<String> emitter = Emitter.create(simpleChannel);

        Messaging messaging = Messaging.builder()
                .emitter(emitter)
                .blockingListener(simpleChannel, testData::add)
                .build();

        messaging.start();

        emitter.send(Message.of("test1"));
        emitter.send("test2");
        emitter.send(Message.of("test3"));
        emitter.send("test4");

        testData.await(200, TimeUnit.MILLISECONDS);
        messaging.stop();

        testData.assertEquals();
    }

    @Test
    void emitWithMessage() throws InterruptedException {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3", "test4"));

        Channel<String> simpleChannel = Channel.create("simple-channel");

        Emitter<String> emitter = Emitter.create(simpleChannel);

        Messaging messaging = Messaging.builder()
                .emitter(emitter)
                .blockingMessageListener(simpleChannel, m->testData.add(m.getPayload()))
                .build();

        messaging.start();

        emitter.send(Message.of("test1"));
        emitter.send("test2");
        emitter.send(Message.of("test3"));
        emitter.send("test4");

        testData.await(200, TimeUnit.MILLISECONDS);
        messaging.stop();

        testData.assertEquals();
    }

}
