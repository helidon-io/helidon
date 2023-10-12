/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.reactive.Multi;
import io.helidon.messaging.connectors.mock.MockConnector;
import io.helidon.messaging.connectors.mock.TestConnector;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.helidon.messaging.connectors.mock.MockConnector.CONNECTOR_NAME;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.INCOMING_PREFIX;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.OUTGOING_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@HelidonTest
@DisableDiscovery
@AddBean(MockConnector.class)
@AddExtension(MessagingCdiExtension.class)
@AddConfig(key = INCOMING_PREFIX + "test-channel-1.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-1.mock-data", value = "a,b,c,d")
@AddConfig(key = INCOMING_PREFIX + "test-channel-3.connector", value = CONNECTOR_NAME)
@AddConfig(key = OUTGOING_PREFIX + "test-channel-2.connector", value = CONNECTOR_NAME)
@AddConfig(key = OUTGOING_PREFIX + "test-channel-4.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-5.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-5.mock-data-type", value = "java.lang.Long")
@AddConfig(key = INCOMING_PREFIX + "test-channel-5.mock-data", value = "9,10,11,12")
@AddConfig(key = OUTGOING_PREFIX + "test-channel-6.connector", value = CONNECTOR_NAME)
public class MockConnectorTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final List<Long> consumeTest3Result = new ArrayList<>();

    @Inject
    @TestConnector
    private MockConnector mockConnector;

    @Incoming("test-channel-1")
    @Outgoing("test-channel-2")
    public String receive(String msg) {
        return msg.toUpperCase();
    }

    @Incoming("test-channel-3")
    public void consumeTest3Method(long payload) {
        consumeTest3Result.add(payload);
    }

    @Outgoing("test-channel-4")
    public Multi<Message<Long>> produceTest4Method() {
        return Multi.just(1L, 2L, 3L, 4L).map(Message::of);
    }

    @Incoming("test-channel-5")
    @Outgoing("test-channel-6")
    public long consumeTest5Method(long payload) {
        return payload;
    }

    @Test
    void processorTest12() {
        mockConnector.outgoing("test-channel-2", String.class)
                .awaitPayloads(TIMEOUT, "A", "B", "C", "D");
        mockConnector.incoming("test-channel-1", String.class)
                .emit("e");
        mockConnector.outgoing("test-channel-2", String.class)
                .awaitPayloads(TIMEOUT, "A", "B", "C", "D", "E");
    }

    @Test
    void consumeTest3() {
        mockConnector.incoming("test-channel-3", Long.class)
                .emit(1L, 2L, 3L)
                .complete();
        assertThat(consumeTest3Result, contains(1L, 2L, 3L));
    }

    @Test
    void produceTest4() {
        mockConnector.outgoing("test-channel-4", Long.class)
                .awaitData(TIMEOUT, Message::getPayload, 1L, 2L, 3L, 4L)
                .assertPayloads(Matchers.containsInAnyOrder(4L, 1L, 2L, 3L))
                .assertPayloads(1L, 2L, 3L, 4L);
    }

    @Test
    void processTest6() {
        mockConnector.outgoing("test-channel-6", Long.TYPE)
                .awaitMessage(TIMEOUT, longMessage -> longMessage.getPayload() == 12L)
                .assertPayloads(9L, 10L, 11L, 12L);
    }
}

