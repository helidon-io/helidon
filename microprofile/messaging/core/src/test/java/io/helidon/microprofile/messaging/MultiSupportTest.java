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
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
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
@AddConfig(key = OUTGOING_PREFIX + "test-channel-1.connector", value = CONNECTOR_NAME)
@AddConfig(key = OUTGOING_PREFIX + "test-channel-4.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-5.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-5.mock-data-type", value = "java.lang.Integer")
@AddConfig(key = INCOMING_PREFIX + "test-channel-5.mock-data", value = "6,7,8")
@AddConfig(key = INCOMING_PREFIX + "test-channel-6-in.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-6-in.mock-data", value = "a,b,c")
@AddConfig(key = OUTGOING_PREFIX + "test-channel-6-out.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-7-in.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "test-channel-7-in.mock-data", value = "a,b,c")
@AddConfig(key = OUTGOING_PREFIX + "test-channel-7-out.connector", value = CONNECTOR_NAME)
public class MultiSupportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    @TestConnector
    private MockConnector mockConnector;

    @Inject
    @Channel("test-channel-5")
    private Multi<Integer> multiChannel5;

    @Outgoing("test-channel-1")
    Multi<String> multiWithPayload() {
        return Multi.just("a", "b", "c");
    }

    @Outgoing("test-channel-4")
    Multi<Message<Integer>> multiWithMsg() {
        return Multi.range(3, 3).map(Message::of);
    }

    @Incoming("test-channel-6-in")
    @Outgoing("test-channel-6-out")
    Multi<Message<String>> multiPubProcFlatMapMsg(Message<String> msg) {
        String payload = msg.getPayload();
        return Multi.just(payload, payload.toUpperCase()).map(Message::of);
    }

    @Incoming("test-channel-7-in")
    @Outgoing("test-channel-7-out")
    Multi<String> multiPubProcFlatMapPay(String payload) {
        return Multi.just(payload, payload.toUpperCase());
    }

    @Test
    void multiWithPayloadTest() {
        mockConnector.outgoing("test-channel-1", String.class)
                .awaitData(TIMEOUT, Message::getPayload, "a", "b", "c");
    }

    @Test
    void multiWithMsgTest() {
        mockConnector.outgoing("test-channel-4", Integer.class)
                .awaitData(TIMEOUT, Message::getPayload, 3, 4, 5);
    }

    @Test
    void injectedMulti() {
        List<Integer> actual = multiChannel5
                .limit(3)
                .collectList()
                .await(TIMEOUT);
        assertThat(actual, contains(6, 7, 8));
    }

    @Test
    void multiPubProcFlatMapMsgTest() {
        mockConnector.outgoing("test-channel-6-out", String.class)
                .awaitData(TIMEOUT, Message::getPayload, "a", "A", "b", "B", "c", "C");
    }

    @Test
    void multiPubProcFlatMapPayTest() {
        mockConnector.outgoing("test-channel-7-out", String.class)
                .awaitData(TIMEOUT, Message::getPayload, "a", "A", "b", "B", "c", "C");
    }
}
