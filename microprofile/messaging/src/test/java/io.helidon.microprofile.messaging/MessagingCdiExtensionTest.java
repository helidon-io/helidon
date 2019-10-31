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

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.messaging.kafka.SimpleKafkaProducer;
import io.helidon.messaging.kafka.connector.KafkaConnectorFactory;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.config.MpConfigProviderResolver;
import io.helidon.microprofile.server.Server;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MessagingCdiExtensionTest {

    private static SeContainer cdiContainer;

    @RegisterExtension
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource();
    public static final String TEST_TOPIC = "graph-done";
    public static final String TEST_MESSAGE = "this is first test message";


    private static final Connector CONNECTOR_LITERAL = new Connector() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Connector.class;
        }

        @Override
        public String value() {
            return KafkaConnectorFactory.CONNECTOR_NAME;
        }
    };

    @BeforeAll
    public synchronized static void startCdiContainer() {
        setupLogging();
        Properties p = new Properties();
        p.setProperty("mp.messaging.incoming.test-channel.connector", KafkaConnectorFactory.CONNECTOR_NAME);
        p.setProperty("mp.messaging.incoming.test-channel.bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.incoming.test-channel.topic", TEST_TOPIC);
        p.setProperty("mp.messaging.incoming.test-channel.key.deserializer", LongDeserializer.class.getName());
        p.setProperty("mp.messaging.incoming.test-channel.value.deserializer", StringDeserializer.class.getName());

        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC, 10, (short) 1);

        final Server.Builder builder = Server.builder();
        assertNotNull(builder);
        builder.config(config);
        MpConfigProviderResolver.instance().registerConfig((MpConfig) MpConfig.builder().config(config).build(), Thread.currentThread().getContextClassLoader());
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        initializer.addBeanClasses(KafkaConnectorFactory.class);
        initializer.addBeanClasses(KafkaConsumingTestBean.class);
        cdiContainer = initializer.initialize();

        cdiContainer.select(KafkaConnectorFactory.class).stream().forEach(f -> f.getConsumers().forEach(c -> {
            try {
                c.waitForPartitionAssigment(10, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                fail(e);
            }
        }));

    }

    @AfterAll
    public synchronized static void shutDownCdiContainer() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }

    @Test
    void incomingKafkaTest() throws InterruptedException {
        Properties p = new Properties();
        p.setProperty("mp.messaging.outcoming.test-channel.bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.outcoming.test-channel.topic", TEST_TOPIC);
        p.setProperty("mp.messaging.outcoming.test-channel.key.serializer", LongSerializer.class.getName());
        p.setProperty("mp.messaging.outcoming.test-channel.value.serializer", StringSerializer.class.getName());

        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        cdiContainer.select(KafkaConnectorFactory.class, CONNECTOR_LITERAL).stream()
                .forEach(f -> f.getConsumers().forEach(c -> {
                    try {
                        c.waitForPartitionAssigment(10, TimeUnit.SECONDS);
                    } catch (InterruptedException | TimeoutException e) {
                        fail(e);
                    }
                }));

        // Producer
        SimpleKafkaProducer<Long, String> producer = new SimpleKafkaProducer<>(config.get("mp.messaging.outcoming.test-channel"));
        List<Future<RecordMetadata>> producerFutures = new ArrayList<>(3);
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 1));
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 2));
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 3));

        // Wait for all sent(this is example usage, sent doesn't mean delivered)
        producerFutures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                fail(e);
            }
        });

        // Wait till 3 records are delivered
        assertTrue(KafkaConsumingTestBean.testChannelLatch.await(15, TimeUnit.SECONDS)
                , "All messages not delivered in time, number of unreceived messages: "
                        + KafkaConsumingTestBean.testChannelLatch.getCount());
        producer.close();
    }

    @Test
    void directOutgoingIncomingTest() throws InterruptedException {
        // Wait till 2 messages are delivered
        assertTrue(KafkaConsumingTestBean.selfCallLatch.await(15, TimeUnit.SECONDS)
                , "All messages not delivered in time, number of unreceived messages: "
                        + KafkaConsumingTestBean.selfCallLatch.getCount());
    }

    /**
     * Configure logging from logging.properties file.
     */
    private static void setupLogging() {
        try (InputStream is = MessagingCdiExtensionTest.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            fail(e);
        }
    }
}