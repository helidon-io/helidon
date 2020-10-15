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
 */

package io.helidon.microprofile.messaging.connector;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.util.AnnotationLiteral;

import io.helidon.microprofile.messaging.AbstractCDITest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

public class ConnectorTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //starting container manually
    }

    @Test
    void connectorTest() throws InterruptedException {
        cdiContainer = startCdiContainer(
                Map.of("mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector"),
                IterableConnector.class,
                ConnectedBean.class);
        assertThat("Not connected in time.", ConnectedBean.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    void payloadLeakTest() throws InterruptedException {
        cdiContainer = startCdiContainer(
                Map.of(
                        "mp.messaging.outgoing.to-connector-1.connector", "iterable-connector",
                        "mp.messaging.outgoing.to-connector-2.connector", "iterable-connector",
                        "mp.messaging.outgoing.to-connector-3.connector", "iterable-connector",
                        "mp.messaging.outgoing.to-connector-4.connector", "iterable-connector"
                        ),
                IterableConnector.class,
                LeakingPayloadBean.class);
        IterableConnector connector = (IterableConnector) cdiContainer.select(ConnectorLiteral.INSTANCE).get();
        assertThat("Not connected in time.", connector.await());
    }

    @Test
    void connectorWithProcessorTest() throws InterruptedException {
        cdiContainer = startCdiContainer(
                Map.of("mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector"),
                IterableConnector.class,
                ConnectedProcessorBean.class);
        assertThat("Not connected in time.", ConnectedProcessorBean.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    void connectorWithProcessorOnlyTest() throws InterruptedException {
        Map<String, String> p = Map.of(
                "mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector",
                "mp.messaging.outgoing.iterable-channel-out.connector", "iterable-connector");
        cdiContainer = startCdiContainer(p, IterableConnector.class, ConnectedOnlyProcessorBean.class);
        IterableConnector connector = (IterableConnector) cdiContainer.select(ConnectorLiteral.INSTANCE).get();
        assertThat("Not connected in time.", connector.await());
    }

    @Test
    void missingConnectorTest() {
        assertThrows(DeploymentException.class, () ->
                cdiContainer = startCdiContainer(
                        Map.of("mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector"),
                        ConnectedBean.class));
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
