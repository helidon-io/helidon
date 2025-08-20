/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.List;

import io.helidon.messaging.connectors.mock.MockConnector;
import io.helidon.messaging.connectors.mock.TestConnector;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddBeans;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.AddConfigs;
import io.helidon.microprofile.testing.AddExtension;
import io.helidon.microprofile.testing.AddExtensions;
import io.helidon.microprofile.testing.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.se.SeContainer;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.lang.System.Logger.Level.DEBUG;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddBeans({
        @AddBean(JmsConnector.class),
        @AddBean(MockConnector.class),
})
@AddExtensions({
        @AddExtension(ConfigCdiExtension.class),
        @AddExtension(MessagingCdiExtension.class),
})
@AddConfigs({
        @AddConfig(key = "mp.messaging.connector.helidon-jms.jndi.env-properties.java.naming.provider.url",
                value = "vm://localhost?broker.persistent=false"),
        @AddConfig(key = "mp.messaging.connector.helidon-jms.jndi.env-properties.java.naming.factory.initial",
                value = "org.apache.activemq.jndi.ActiveMQInitialContextFactory"),

        @AddConfig(key = "mp.messaging.connector.helidon-jms.period-executions", value = "5"),

        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.acknowledge-mode", value = "CLIENT_ACKNOWLEDGE"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.destination", value = AckMpTest.TEST_QUEUE_ACK),

        @AddConfig(key = "mp.messaging.outgoing.mock-conn-channel.connector", value = MockConnector.CONNECTOR_NAME),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AckMpTest extends AbstractMPTest {

    static final String TEST_QUEUE_ACK = "queue-ack";
    static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final System.Logger LOGGER = System.getLogger(AckMpTest.class.getName());
    private static final Annotation TEST_CONNECTOR_ANNOTATION = MockConnector.class.getAnnotation(TestConnector.class);

    @Incoming("test-channel-ack-1")
    @Outgoing("mock-conn-channel")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public Message<String> channelAck(Message<String> msg) {
        LOGGER.log(DEBUG, () -> String.format("Received %s", msg.getPayload()));
        if (msg.getPayload().startsWith("NO_ACK")) {
            LOGGER.log(DEBUG, () -> String.format("NOT Acked %s", msg.getPayload()));
        } else {
            LOGGER.log(DEBUG, () -> String.format("Acked %s", msg.getPayload()));
            msg.ack();
        }
        return msg;
    }

    @Test
    @Order(1)
    void resendAckTestPart1(SeContainer cdi) {
        MockConnector mockConnector = cdi.select(MockConnector.class, TEST_CONNECTOR_ANNOTATION).get();
        //Messages starting with NO_ACK are not acked by ChannelAck bean
        List<String> testData = List.of("0", "1", "2", "NO_ACK-1", "NO_ACK-2", "NO_ACK-3");
        produce(TEST_QUEUE_ACK, testData, m -> {});
        mockConnector.outgoing("mock-conn-channel", String.class)
                        .awaitPayloads(TIMEOUT, testData.toArray(String[]::new));
    }

    @Test
    @Order(2)
    void resendAckTestPart2(SeContainer cdi) {
            MockConnector mockConnector = cdi.select(MockConnector.class, TEST_CONNECTOR_ANNOTATION).get();

            //Check if not acked messages are redelivered
            mockConnector.outgoing("mock-conn-channel", String.class)
                    .awaitPayloads(TIMEOUT, "NO_ACK-1", "NO_ACK-2", "NO_ACK-3");
    }

    @AfterAll
    static void afterAll() {
        AbstractJmsTest.clearQueue(TEST_QUEUE_ACK);
    }
}
