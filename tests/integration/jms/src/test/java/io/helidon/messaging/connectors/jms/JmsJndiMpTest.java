/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.stream.IntStream;

import io.helidon.common.reactive.Multi;
import io.helidon.messaging.connectors.mock.MockConnector;
import io.helidon.messaging.connectors.mock.TestConnector;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;

import static io.helidon.messaging.connectors.jms.JmsConnector.CONNECTOR_NAME;
import static io.helidon.messaging.connectors.jms.JmsJndiMpTest.JNDI_ENV_PFX;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.CONNECTOR_PREFIX;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.INCOMING_PREFIX;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.OUTGOING_PREFIX;

@HelidonTest
@DisableDiscovery
@AddBean(JmsConnector.class)
@AddBean(MockConnector.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
@AddConfig(key = JNDI_ENV_PFX + "java.naming.provider.url", value = AbstractJmsTest.BROKER_URL)
@AddConfig(key = JNDI_ENV_PFX + "java.naming.factory.initial", value = "org.apache.activemq.jndi.ActiveMQInitialContextFactory")
@AddConfig(key = JNDI_ENV_PFX + "queue.TestQueue1", value = "TestQueue1")
@AddConfig(key = CONNECTOR_PREFIX + CONNECTOR_NAME + ".period-executions", value = "5")

@AddConfig(key = CONNECTOR_PREFIX + CONNECTOR_NAME + ".jndi.destination", value = "TestQueue1")

@AddConfig(key = OUTGOING_PREFIX + "to-jms.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "from-jms.connector", value = CONNECTOR_NAME)
@AddConfig(key = OUTGOING_PREFIX + "to-mock.connector", value = MockConnector.CONNECTOR_NAME)
public class JmsJndiMpTest {
    static final String JNDI_ENV_PFX = CONNECTOR_PREFIX + "helidon-jms.jndi.env-properties.";
    static final Duration TIME_OUT = Duration.ofSeconds(15);
    static final Integer[] TEST_DATA = IntStream.range(0, 10).boxed().toArray(Integer[]::new);

    @Inject
    @TestConnector
    private MockConnector mockConnector;

    @Outgoing("to-jms")
    public Multi<String> produceData() {
        return Multi.just(TEST_DATA)
                .map(String::valueOf);
    }

    @Incoming("from-jms")
    @Outgoing("to-mock")
    public ProcessorBuilder<String, Integer> resendToMock() {
        return ReactiveStreams.<String>builder()
                .map(Integer::parseInt);
    }

    @Test
    void jmsInOutTest() {
        mockConnector.outgoing("to-mock", Integer.TYPE)
                .awaitPayloads(TIME_OUT, TEST_DATA);
    }
}
