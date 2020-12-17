/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.aq;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;

import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AckTest {

    @Test
    void ackPropagationTest() throws InterruptedException, JMSException {
        Message mockedMessage = Mockito.mock(Message.class);
        SessionMetadata sessionMetadata = Mockito.mock(SessionMetadata.class);

        CountDownLatch latch = new CountDownLatch(1);

        Mockito.doReturn(null).when(sessionMetadata).session();

        Mockito.doAnswer(im -> {
            latch.countDown();
            return null;
        }).when(mockedMessage).acknowledge();

        AqConnectorImpl aqConnector = new AqConnectorImpl(Map.of(), null, null);
        JmsMessage<?> jmsMessage = aqConnector.createMessage(mockedMessage, null, sessionMetadata);
        AqMessage<String> aqMessage = new AqMessageImpl<>(jmsMessage, sessionMetadata);
        aqMessage.ack();
        assertThat("Ack not propagated to JmsMessage",
                latch.await(100, TimeUnit.MILLISECONDS),
                Matchers.is(true));
    }

}
