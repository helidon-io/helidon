/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.microprofile.messaging.AbstractCDITest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ConnectorTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //Starting container manually
    }

    @Test
    void connectorTest() throws InterruptedException {
        cdiContainer = startCdiContainer(
                Map.of("mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector"),
                IterableConnector.class,
                ConnectedBean.class);
        assertTrue(ConnectedBean.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    void connectorWithProcessorTest() throws InterruptedException {
        cdiContainer = startCdiContainer(
                Map.of("mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector"),
                IterableConnector.class,
                ConnectedProcessorBean.class);
        assertTrue(ConnectedProcessorBean.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    void connectorWithProcessorOnlyTest() throws InterruptedException {
        Map<String, String> p = Map.of(
                "mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector",
                "mp.messaging.outgoing.iterable-channel-out.connector", "iterable-connector");
        cdiContainer = startCdiContainer(p, IterableConnector.class, ConnectedOnlyProcessorBean.class);
        assertTrue(IterableConnector.LATCH.await(2, TimeUnit.SECONDS));
    }

    @Test
    void missingConnectorTest() {
        assertThrows(DeploymentException.class, () ->
                cdiContainer = startCdiContainer(
                        Map.of("mp.messaging.incoming.iterable-channel-in.connector", "iterable-connector"),
                        ConnectedBean.class));
    }
}
