/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.jms.JMSException;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static java.lang.System.Logger.Level.DEBUG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddBeans({
        @AddBean(JmsConnector.class),
        @AddBean(AckMpTest.ChannelAck.class),
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
})
public class AckMpTest extends AbstractMPTest {

    static final String TEST_QUEUE_ACK = "queue-ack";
    static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final System.Logger LOGGER = System.getLogger(AckMpTest.class.getName());

    @Test
    void resendAckTest(SeContainer cdi) {
        ChannelAck channelAck = cdi.select(ChannelAck.class).get();
        //Messages starting with NO_ACK are not acked by ChannelAck bean
        List<String> testData = List.of("0", "1", "2", "NO_ACK-1", "NO_ACK-2", "NO_ACK-3");
        produce(TEST_QUEUE_ACK, testData, m -> {});
        assertThat("Not all initial items delivered in time.", channelAck.awaitDelivered(), is(true));
        assertThat(channelAck.delivered(), contains(testData.toArray(String[]::new)));
        assertThat("Not all redelivered items delivered in time.", channelAck.awaitRedelivered(), is(true));
        assertThat(channelAck.redelivered(), contains("NO_ACK-1", "NO_ACK-2", "NO_ACK-3"));
    }

    @AfterAll
    static void afterAll() {
        AbstractJmsTest.clearQueue(TEST_QUEUE_ACK);
    }

    @ApplicationScoped
    public static class ChannelAck {
        private final List<String> delivered = Collections.synchronizedList(new ArrayList<>());
        private final List<String> redelivered = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch deliveredLatch = new CountDownLatch(6);
        private final CountDownLatch redeliveredLatch = new CountDownLatch(3);
        private final CountDownLatch ackedLatch = new CountDownLatch(3);
        private final AtomicBoolean recovered = new AtomicBoolean();

        @Incoming("test-channel-ack-1")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<Void> channelAck(JmsMessage<String> msg) {
            String payload = msg.getPayload();
            try {
                if (msg.getJmsMessage().getJMSRedelivered()) {
                    LOGGER.log(DEBUG, () -> String.format("Acking redelivered %s", payload));
                    return msg.ack().thenCompose(unused -> {
                        redelivered.add(payload);
                        redeliveredLatch.countDown();
                        return CompletableFuture.completedFuture(null);
                    });
                }

                LOGGER.log(DEBUG, () -> String.format("Received %s", payload));
                delivered.add(payload);
                if (payload.startsWith("NO_ACK")) {
                    LOGGER.log(DEBUG, () -> String.format("NOT Acked %s", payload));
                    deliveredLatch.countDown();
                    return maybeRecover(msg);
                } else {
                    LOGGER.log(DEBUG, () -> String.format("Acking %s", payload));
                    deliveredLatch.countDown();
                    return msg.ack().thenCompose(unused -> {
                        ackedLatch.countDown();
                        return maybeRecover(msg);
                    });
                }
            } catch (JMSException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        boolean awaitDelivered() {
            return await(deliveredLatch);
        }

        boolean awaitRedelivered() {
            return await(redeliveredLatch);
        }

        List<String> delivered() {
            return delivered;
        }

        List<String> redelivered() {
            return redelivered;
        }

        private CompletionStage<Void> maybeRecover(JmsMessage<String> msg) {
            if (deliveredLatch.getCount() == 0
                    && ackedLatch.getCount() == 0
                    && recovered.compareAndSet(false, true)) {
                try {
                    LOGGER.log(DEBUG, "Recovering JMS session for unacked messages");
                    msg.getJmsSession().recover();
                } catch (JMSException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        private boolean await(CountDownLatch latch) {
            try {
                return latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
