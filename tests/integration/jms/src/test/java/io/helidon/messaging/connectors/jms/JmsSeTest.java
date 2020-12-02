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

package io.helidon.messaging.connectors.jms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.reactive.Multi;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.jndi.ActiveMQInitialContextFactory;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

public class JmsSeTest extends AbstractJmsTest {

    @Test
    void customFactoryTest() throws InterruptedException {
        final String destination = "custom-fact-test-dest";
        final String factoryName = "custom-connection-factory-1";

        JmsConnector jmsConnector = JmsConnector.builder()
                .connectionFactory(factoryName, connectionFactory)
                .build();

        Channel<String> toJms = Channel.<String>builder()
                .name("to-jms")
                .subscriberConfig(JmsConnector.configBuilder()
                        .namedFactory(factoryName)
                        .destination(destination)
                        .type(Type.QUEUE)
                        .build())
                .build();

        Channel<String> fromJms = Channel.<String>builder()
                .name("from-jms")
                .publisherConfig(JmsConnector.configBuilder()
                        .namedFactory(factoryName)
                        .destination(destination)
                        .type(Type.QUEUE)
                        .build())
                .build();

        CountDownLatch cdl = new CountDownLatch(4);
        List<String> result = new ArrayList<>(4);

        Messaging.builder()
                .connector(jmsConnector)
                .publisher(toJms, Multi.just(1, 2, 3, 4).map(String::valueOf).map(Message::of))
                .listener(fromJms, s -> {
                    result.add(s);
                    cdl.countDown();
                })
                .build()
                .start();

        assertTrue(cdl.await(2, TimeUnit.SECONDS));
        assertThat(result, containsInAnyOrder("1", "2", "3", "4"));
    }

    @Test
    void customExecutorsTest() throws InterruptedException, TimeoutException, ExecutionException {
        final String testThread = "test-thread";
        final String destination = "custom-exec-test-dest";
        final String url = "vm://localhost?broker.persistent=false";

        JmsConnector jmsConnector = JmsConnector.builder()
                .scheduler(ScheduledThreadPoolSupplier.builder()
                        .threadNamePrefix(testThread)
                        .build()
                        .get())
                .build();

        Channel<String> toJms = Channel.<String>builder()
                .name("to-jms")
                .subscriberConfig(JmsConnector.configBuilder()
                        .jndiInitialFactory(ActiveMQInitialContextFactory.class)
                        .jndiProviderUrl(url)
                        .destination(destination)
                        .type(Type.QUEUE)
                        .build())
                .build();

        Channel<String> fromJms = Channel.<String>builder()
                .name("from-jms")
                .publisherConfig(JmsConnector.configBuilder()
                        .jndiInitialFactory(ActiveMQInitialContextFactory.class)
                        .jndiProviderUrl(url)
                        .destination(destination)
                        .type(Type.QUEUE)
                        .build())
                .build();

        CompletableFuture<String> threadNameFuture = new CompletableFuture<>();

        Messaging.builder()
                .connector(jmsConnector)
                .publisher(toJms, Multi.just(1).map(String::valueOf).map(Message::of))
                .listener(fromJms, s -> threadNameFuture.complete(Thread.currentThread().getName()))
                .build()
                .start();

        assertThat(threadNameFuture.get(2, TimeUnit.SECONDS), startsWith(testThread));
    }
}
