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

package io.helidon.microprofile.messaging;

import io.helidon.microprofile.messaging.beans.InnerProcessorBean;
import io.helidon.microprofile.messaging.beans.InternalChannelsBean;
import io.helidon.microprofile.messaging.beans.NotConnectedIncommingChannelBean;
import io.helidon.microprofile.messaging.beans.NotConnectedOutgoingChannelBean;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.spi.DeploymentException;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessagingCdiExtensionTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //Starting container manually
    }

    @Test
    void internalChannelsInSameBeanTest() throws InterruptedException {
        cdiContainer = startCdiContainer(new Properties(), InternalChannelsBean.class);

        // Wait till all messages are delivered
        assertTrue(InternalChannelsBean.publisher_string_latch.await(2, TimeUnit.SECONDS)
                , "All messages not delivered in time, number of unreceived messages: "
                        + InternalChannelsBean.publisher_string_latch.getCount());
    }

    @Test
    void processorInSameBeanTest() throws InterruptedException {
        cdiContainer = startCdiContainer(new Properties(), InnerProcessorBean.class);

        // Wait till all messages are delivered
        assertTrue(InnerProcessorBean.testLatch.await(2, TimeUnit.SECONDS)
                , "All messages not delivered in time, number of unreceived messages: "
                        + InnerProcessorBean.testLatch.getCount());
    }

    @Test
    void notConnectedIncomingChannelTest() {
        assertThrows(DeploymentException.class, () ->
                cdiContainer = startCdiContainer(new Properties(), NotConnectedIncommingChannelBean.class));
    }

    @Test
    void notConnectedOutgoingChannelTest() {
        assertThrows(DeploymentException.class, () ->
                cdiContainer = startCdiContainer(new Properties(), NotConnectedOutgoingChannelBean.class));
    }
}
