/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessagingTest {

    @AfterEach
    void cleanUp() {
        TestConnector.reset();
    }


    @Test
    void pubBuilderToListenerConsumer() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, ReactiveStreams.fromIterable(testData.expected).map(Message::of))
                .listener(channel1, testData::add)
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void pubBuilderToSubBuilder() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, ReactiveStreams.fromIterable(testData.expected).map(Message::of))
                .subscriber(channel1, ReactiveStreams.<Message<String>>builder()
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void pubToSubAck() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, ReactiveStreams.fromIterable(testData.expected).buildRs(), Message::of)
                .subscriber(channel1, ReactiveStreams.<Message<String>>builder()
                        .map(Message::getPayload)
                        .forEach(testData::add)
                        .build())
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void multiToListener() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, Multi.create(testData.expected), Message::of)
                .listener(channel1, testData::add)
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void simpleChannelRS() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, ReactiveStreams.fromIterable(testData.expected).map(Message::of))
                .subscriber(channel1, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void simpleChannelWithMulti() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, Multi.create(testData.expected).map(Message::of))
                .listener(channel1, testData::add)
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void pubConsumer() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));
        TestMessages<String> testMessages = new TestMessages<>();

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, ReactiveStreams.fromIterable(testData.expected).map(testMessages::of))
                .listener(channel1, testData::add)
                .build()
                .start();

        testData.assertEquals();
        testMessages.assertAllAcked();
    }

    @Test
    void pubConsumerMsg() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));
        TestMessages<String> testMessages = new TestMessages<>();

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, Multi.create(testData.expected).map(testMessages::of))
                .subscriber(channel1, multi -> multi.map(Message::getPayload).forEach(testData::add))
                .build()
                .start();

        testData.assertEquals();
        testMessages.assertNoneAcked();
    }

    @Test
    void outgoingGenerate() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("Infinite Hello!!", "Infinite Hello!!", "Infinite Hello!!"));

        Channel<String> channel1 = Channel.create("channel1");

        Messaging.builder()
                .publisher(channel1, ReactiveStreams.generate(() -> Message.of("Infinite Hello!!")))
                .subscriber(channel1, ReactiveStreams.<Message<String>>builder()
                        .limit(3)
                        .map(Message::getPayload)
                        .forEach(testData::add)
                )
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void simpleChannelWithProcessorRS() {
        LatchedTestData<String> testData = new LatchedTestData<>(List.of("test1", "test2", "test3"));

        Channel<String> simpleChannel = Channel.create("simple-channel");
        Channel<String> middleChannel = Channel.create("middle-channel");

        Messaging.builder()
                .publisher(middleChannel, ReactiveStreams.fromIterable(testData.expected).map(Message::of))
                .processor(middleChannel, simpleChannel, ReactiveStreams.<Message<String>>builder()
                        .map(Message::getPayload)
                        .map(s -> ">>" + s)
                        .map(Message::of)
                )
                .subscriber(simpleChannel, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start();

        testData.assertEquals(testData.expected.stream().map(s -> ">>" + s).collect(Collectors.toList()));
    }

    @Test
    void incomingFromConnector() {
        LatchedTestData<CharSequence> testData =
                new LatchedTestData<>(TestConnector.TEST_DATA.stream()
                        .map(s -> (CharSequence) s)
                        .collect(Collectors.toList()));

        Channel<String> channel = Channel.create("from-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.incoming." + channel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        Messaging.builder()
                .config(config)
                .connector(TestConnector.create())
                .subscriber(channel, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start();

        testData.assertEquals();
    }

    @Test
    void unknownIncomingConnector() {
        LatchedTestData<CharSequence> testData =
                new LatchedTestData<>(TestConnector.TEST_DATA.stream()
                        .map(s -> (CharSequence) s)
                        .collect(Collectors.toList()));

        Channel<String> channel = Channel.create("from-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.incoming." + channel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        assertThrows(MessagingException.class, () -> Messaging.builder()
                .config(config)
                .subscriber(channel, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start());
    }

    @Test
    void incomingFromStoppableConnector() {
        LatchedTestData<String> testData = new LatchedTestData<>(TestConnector.TEST_DATA);

        Channel<String> channel = Channel.create("from-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.incoming." + channel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();


        TestConnector connector = TestConnector.create();

        Messaging messaging = Messaging.builder()
                .config(config)
                .connector(connector)
                .listener(channel, testData::add)
                .build();

        messaging.start();
        testData.assertEquals();

        assertThat(connector.stoppedFuture.isDone(), is(false));
        messaging.stop();
        assertThat(connector.stoppedFuture.isDone(), is(true));
    }

    @Test
    void outgoingToConnector() throws InterruptedException {
        Channel<String> channel = Channel.create("to-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.outgoing." + channel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        Messaging.builder()
                .config(config)
                .connector(TestConnector.create())
                .publisher(channel, ReactiveStreams.fromIterable(TestConnector.TEST_DATA).map(Message::of))
                .build()
                .start();

        TestConnector.latch.await(200, TimeUnit.MILLISECONDS);
    }

    @Test
    void unknownOutgoingConnector() throws InterruptedException {
        Channel<String> channel = Channel.create("to-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.outgoing." + channel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThrows(MessagingException.class, () -> Messaging.builder()
                .config(config)
                .publisher(channel, ReactiveStreams.fromIterable(TestConnector.TEST_DATA).map(Message::of))
                .build()
                .start()
        );
    }

    @Test
    void processorFunctionBetweenConnectors() throws InterruptedException {

        Channel<String> fromConnectorChannel = Channel.create("from-test-connector");
        Channel<String> toConnectorChannel = Channel.create("to-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.incoming." + fromConnectorChannel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        p.put("mp.messaging.outgoing." + toConnectorChannel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        Messaging.builder()
                .config(config)
                .connector(TestConnector.create())
                .processor(fromConnectorChannel, toConnectorChannel, payload -> ">>" + payload)
                .build()
                .start();

        TestConnector.latch.await(200, TimeUnit.MILLISECONDS);
        assertThat(TestConnector.receivedData, equalTo(TestConnector.TEST_DATA.stream().map(s -> ">>" + s).collect(Collectors.toList())));
    }


    @Test
    void processorBetweenConnectors() throws InterruptedException {

        Channel<String> fromConnectorChannel = Channel.create("from-test-connector");
        Channel<String> toConnectorChannel = Channel.create("to-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.incoming." + fromConnectorChannel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        p.put("mp.messaging.outgoing." + toConnectorChannel.name() + ".connector", TestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        Messaging.builder()
                .config(config)
                .connector(TestConnector.create())
                .processor(fromConnectorChannel, toConnectorChannel, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .map(s -> ">>" + s)
                        .map(Message::of)
                )
                .build()
                .start();

        TestConnector.latch.await(200, TimeUnit.MILLISECONDS);
        assertThat(TestConnector.receivedData, equalTo(TestConnector.TEST_DATA.stream().map(s -> ">>" + s).collect(Collectors.toList())));
    }

    @Test
    void processorConfigApi() throws InterruptedException, TimeoutException, ExecutionException {

        CompletableFuture<Map<String, String>> completableFuture = new CompletableFuture<>();
        HashSet<String> publisherProps = new HashSet<>();

        Channel<String> fromConnectorChannel = Channel.<String>builder()
                .name("from-test-connector")
                .publisherConfig(TestConfigurableConnector.configBuilder()
                        .url("http://source.com")
                        .port(8888)
                        .build()
                )
                .build();

        Channel<CompletableFuture<Map<String, String>>> toConnectorChannel = Channel.<CompletableFuture<Map<String, String>>>builder()
                .name("to-test-connector")
                .subscriberConfig(TestConfigurableConnector.configBuilder()
                        .url("http://sink.com")
                        .port(9999)
                        .build()
                )
                .build();

        Messaging.builder()
                .connector(TestConfigurableConnector.create())

                .processor(fromConnectorChannel, toConnectorChannel,
                        ReactiveStreams.<Message<String>>builder()
                                .map(Message::getPayload)
                                .peek(publisherProps::add)
                                .map(s -> Message.of(completableFuture))
                )

                .build()
                .start();

        Map<String, String> subscriberProps = completableFuture.get(200, TimeUnit.MILLISECONDS);

        assertThat(subscriberProps, equalTo(Map.of(
                "channel-name", "to-test-connector",
                "connector", "test-configurable-connector",
                "port", "9999",
                "url", "http://sink.com"
        )));

        assertThat(publisherProps, equalTo(Set.of(
                "channel-name=from-test-connector",
                "connector=test-configurable-connector",
                "port=8888",
                "url=http://source.com"
        )));
    }


    @Test
    void connectorsTypeTest() throws InterruptedException {
        LatchedTestData<CharSequence> testData =
                new LatchedTestData<>(TestConnector.TEST_DATA.stream()
                        .map(s -> (CharSequence) s)
                        .collect(Collectors.toList()));

        Channel<String> channelIn = Channel.create("from-test-connector");
        Channel<String> channelOut = Channel.create("to-test-connector");

        HashMap<String, String> p = new HashMap<>();
        p.put("mp.messaging.incoming." + channelIn.name() + ".connector", TestConnector.IncomingOnlyTestConnector.CONNECTOR_NAME);
        p.put("mp.messaging.outgoing." + channelOut.name() + ".connector", TestConnector.OutgoingOnlyTestConnector.CONNECTOR_NAME);
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        Messaging.builder()
                .config(config)
                .connector(TestConnector.createIncomingOnly())
                .connector(TestConnector.createOutgoingOnly())
                .publisher(channelOut, ReactiveStreams.fromIterable(TestConnector.TEST_DATA).map(Message::of))
                .subscriber(channelIn, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start();

        testData.assertEquals();
        assertTrue(TestConnector.latch.await(500, TimeUnit.MILLISECONDS));
    }
}
