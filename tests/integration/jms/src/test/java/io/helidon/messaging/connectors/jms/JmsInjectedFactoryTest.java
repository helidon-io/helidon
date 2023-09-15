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
import io.helidon.messaging.connectors.jms.shim.JakartaJms;
import io.helidon.messaging.connectors.mock.MockConnector;
import io.helidon.messaging.connectors.mock.TestConnector;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;

import static io.helidon.messaging.connectors.jms.JmsConnector.CONNECTOR_NAME;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.CONNECTOR_PREFIX;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.INCOMING_PREFIX;
import static org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory.OUTGOING_PREFIX;

@HelidonTest
@DisableDiscovery
@AddBean(JmsConnector.class)
@AddBean(MockConnector.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
@AddConfig(key = CONNECTOR_PREFIX + CONNECTOR_NAME + ".period-executions", value = "5")
@AddConfig(key = CONNECTOR_PREFIX + CONNECTOR_NAME + ".destination", value = "TestQueue1")

@AddConfig(key = OUTGOING_PREFIX + "to-jms.connector", value = CONNECTOR_NAME)
@AddConfig(key = OUTGOING_PREFIX + "to-jms.named-factory", value = "activemq-cf-jakarta")
@AddConfig(key = INCOMING_PREFIX + "from-jms.connector", value = CONNECTOR_NAME)
@AddConfig(key = INCOMING_PREFIX + "from-jms.named-factory", value = "activemq-cf-jakarta")
@AddConfig(key = OUTGOING_PREFIX + "to-mock.connector", value = MockConnector.CONNECTOR_NAME)
public class JmsInjectedFactoryTest {

    static final Duration TIME_OUT = Duration.ofSeconds(15);
    static final Integer[] TEST_DATA = IntStream.range(0, 10).boxed().toArray(Integer[]::new);

    @Inject
    @TestConnector
    private MockConnector mockConnector;

    @Produces
    @ApplicationScoped
    @Named("activemq-cf-jakarta")
    public jakarta.jms.ConnectionFactory connectionFactory() {
        return JakartaJms.create(new ActiveMQConnectionFactory(AbstractJmsTest.BROKER_URL));
    }

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
