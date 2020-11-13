/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.se.SeContainer;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddConfigs;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.AddExtensions;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddBeans({
        @AddBean(JmsConnector.class),
        @AddBean(AbstractSampleBean.ChannelAck.class),
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

        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.acknowledge-mode", value = "CLIENT_ACKNOWLEDGE"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-ack-1.destination", value = AckMpTest.TEST_QUEUE_ACK),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AckMpTest extends AbstractMPTest {

    static final String TEST_QUEUE_ACK = "queue-ack";

    @PostConstruct
    void cleanupBefore() {
        //cleanup not acked messages
        consumeAllCurrent(TEST_QUEUE_ACK)
                .map(JmsMessage::of)
                .forEach(Message::ack);
    }

    @Test
    @Order(1)
    void resendAckTestPart1(SeContainer cdi) {
        //Messages starting with NO_ACK is not acked by ChannelAck bean
        List<String> testData = List.of("0", "1", "2", "NO_ACK-1", "NO_ACK-2", "NO_ACK-3");
        AbstractSampleBean bean = cdi.select(AbstractSampleBean.ChannelAck.class).get();
        produceAndCheck(bean, testData, TEST_QUEUE_ACK, testData);
        bean.restart();
    }

    @Test
    @Order(2)
    void resendAckTestPart2(SeContainer cdi) {
        try {
            AbstractSampleBean bean = cdi.select(AbstractSampleBean.ChannelAck.class).get();
            //Send nothing just check if not acked messages are redelivered
            produceAndCheck(bean, List.of(), TEST_QUEUE_ACK, List.of("NO_ACK-1", "NO_ACK-2", "NO_ACK-3"));
        } finally {
            //cleanup not acked messages
            consumeAllCurrent(TEST_QUEUE_ACK)
                    .map(JmsMessage::of)
                    .forEach(Message::ack);
        }
    }
}
