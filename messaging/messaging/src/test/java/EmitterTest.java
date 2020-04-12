/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.messaging.Channel;
import io.helidon.messaging.Emitter;
import io.helidon.messaging.Messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

public class EmitterTest {


    @Test
    void emit() throws InterruptedException {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3", "test4"));

        Channel<String> simpleChannel = Channel.create("simple-channel");

        Emitter<String> emitter = Emitter.create(simpleChannel);

        Messaging messaging = Messaging.builder()
                .emitter(emitter)
                .listener(simpleChannel, testData::add)
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
    void broadcast() throws InterruptedException {
        final List<String> TEST_DATA = List.of("test1", "test2", "test3", "test4");

        LatchedTestData<String> testData1 = new LatchedTestData<>(TEST_DATA);
        LatchedTestData<String> testData2 = new LatchedTestData<>(TEST_DATA);
        LatchedTestData<String> testData3 = new LatchedTestData<>(TEST_DATA);

        Channel<String> simpleChannel1 = Channel.create("simple-channel-1");
        Channel<String> simpleChannel2 = Channel.create("simple-channel-2");
        Channel<String> simpleChannel3 = Channel.create("simple-channel-3");


        Emitter<String> emitter = Emitter.builder(String.class)
                .channel(simpleChannel1)
                .channel(simpleChannel2)
                .channel(simpleChannel3)
                .build();

        Messaging messaging = Messaging.builder()
                // register one emitter for 3 channels
                .emitter(emitter)
                .listener(simpleChannel1, testData1::add)
                .listener(simpleChannel2, testData2::add)
                .listener(simpleChannel3, testData3::add)
                .build();

        messaging.start();

        emitter.send(Message.of("test1"));
        emitter.send("test2");
        emitter.send(Message.of("test3"));
        emitter.send("test4");

        testData3.await(200, TimeUnit.MILLISECONDS);
        messaging.stop();

        testData1.assertEquals();
        testData2.assertEquals();
        testData3.assertEquals();
    }
}
