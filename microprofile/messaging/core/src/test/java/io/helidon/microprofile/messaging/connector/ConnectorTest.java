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

package io.helidon.microprofile.messaging.connector;

import java.util.concurrent.TimeUnit;

import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.AddExtension;
import io.helidon.microprofile.testing.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.AnnotationLiteral;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddExtension(MessagingCdiExtension.class)
public class ConnectorTest {

    @Test
    @AddConfig(key = "mp.messaging.incoming.iterable-channel-in.connector", value = "iterable-connector")
    @AddBean(IterableConnector.class)
    @AddBean(ConnectedBean.class)
    void connectorTest() throws InterruptedException {
        assertThat("Not connected in time.", ConnectedBean.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    @AddConfigBlock(value = """
            mp.messaging.outgoing:
                to-connector-1.connector: iterable-connector
                to-connector-2.connector: iterable-connector
                to-connector-3.connector: iterable-connector
                to-connector-4.connector: iterable-connector
            """, type = "yaml")
    @AddBean(IterableConnector.class)
    @AddBean(LeakingPayloadBean.class)
    void payloadLeakTest() throws InterruptedException {
        IterableConnector connector = (IterableConnector) CDI.current().select(ConnectorLiteral.INSTANCE).get();
        assertThat("Not connected in time.", connector.await());
    }

    @Test
    @AddConfig(key = "mp.messaging.incoming.iterable-channel-in.connector", value = "iterable-connector")
    @AddBean(IterableConnector.class)
    @AddBean(ConnectedProcessorBean.class)
    void connectorWithProcessorTest() throws InterruptedException {
        assertThat("Not connected in time.", ConnectedProcessorBean.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    @AddConfigBlock(value = """
            mp.messaging.incoming:
                iterable-channel-in.connector: iterable-connector
            mp.messaging.outgoing:
                iterable-channel-out.connector: iterable-connector
            """, type = "yaml")
    @AddBean(IterableConnector.class)
    @AddBean(ConnectedOnlyProcessorBean.class)
    void connectorWithProcessorOnlyTest() throws InterruptedException {
        IterableConnector connector = (IterableConnector) CDI.current().select(ConnectorLiteral.INSTANCE).get();
        assertThat("Not connected in time.", connector.await());
    }

    public static final class ConnectorLiteral extends AnnotationLiteral<Connector> implements Connector {

        public static final ConnectorLiteral INSTANCE = new ConnectorLiteral();

        private static final long serialVersionUID = 1L;

        @Override
        public String value() {
            return "iterable-connector";
        }
    }
}
