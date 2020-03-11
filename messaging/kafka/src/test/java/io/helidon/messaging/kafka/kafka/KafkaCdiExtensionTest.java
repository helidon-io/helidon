/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.messaging.kafka.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MpConfigProviderResolver;
import io.helidon.messaging.kafka.SimpleKafkaConsumer;
import io.helidon.messaging.kafka.SimpleKafkaProducer;
import io.helidon.messaging.kafka.connector.KafkaConnectorFactory;
import io.helidon.microprofile.messaging.MessagingCdiExtension;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class KafkaCdiExtensionTest {

    private static final Logger LOGGER = Logger.getLogger(KafkaCdiExtensionTest.class.getName());
    protected SeContainer cdiContainer;

    protected static final Connector KAFKA_CONNECTOR_LITERAL = new Connector() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Connector.class;
        }

        @Override
        public String value() {
            return KafkaConnectorFactory.CONNECTOR_NAME;
        }
    };

    @RegisterExtension
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource();
    public static final String TEST_TOPIC_1 = "graph-done-1";
    public static final String TEST_TOPIC_2 = "graph-done-2";
    public static final String TEST_MESSAGE = "this is first test message";

    protected Map<String, String> cdiConfig() {
        Map<String, String> p = new HashMap<>();
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-1.connector", KafkaConnectorFactory.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-1.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-1.topic", TEST_TOPIC_1,
                "mp.messaging.incoming.test-channel-1.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-1.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-2.connector", KafkaConnectorFactory.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-2.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-2.topic", TEST_TOPIC_2,
                "mp.messaging.incoming.test-channel-2.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-2.value.deserializer", StringDeserializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-3.connector", KafkaConnectorFactory.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-3.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.outgoing.test-channel-3.topic", TEST_TOPIC_1,
                "mp.messaging.outgoing.test-channel-3.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel-3.value.serializer", StringSerializer.class.getName())
        );
        return p;
    }

    @BeforeAll
    static void prepareTopics() {
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_1, 10, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_2, 10, (short) 1);
    }

    @BeforeEach
    void setUp() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(KafkaConnectorFactory.class);
        classes.add(KafkaSampleBean.class);
        classes.add(MessagingCdiExtension.class);

        Map<String, String> p = new HashMap<>(cdiConfig());
        System.out.println("Starting container ...");
        cdiContainer = startCdiContainer(p, classes);
        assertTrue(cdiContainer.isRunning());
        //Wait till consumers are ready
        forEachBean(KafkaConnectorFactory.class, KAFKA_CONNECTOR_LITERAL, b -> b.getConsumers().forEach(c -> {
            try {
                c.waitForPartitionAssigment(10, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                fail(e);
            }
        }));
    }

    @AfterEach
    public void tearDown() {
        KafkaConnectorFactory factory = getInstance(KafkaConnectorFactory.class, KAFKA_CONNECTOR_LITERAL).get();
        Collection<SimpleKafkaConsumer<Object, Object>> consumers = factory.getConsumers();
        assertFalse(consumers.isEmpty());
        cdiContainer.close();
        assertTrue(consumers.isEmpty());
    }

    @Test
    public void incomingKafkaOk() throws InterruptedException {
        List<String> testData = IntStream.range(0, 499).mapToObj(i -> "test" + i).collect(Collectors.toList());
        CountDownLatch testChannelLatch = new CountDownLatch(testData.size());
        KafkaSampleBean kafkaConsumingBean = cdiContainer.select(KafkaSampleBean.class).get();
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produce(testData, TEST_TOPIC_1, testChannelLatch);
        assertEquals(testData.size(), kafkaConsumingBean.getConsumed().size());
        assertTrue(kafkaConsumingBean.getConsumed().containsAll(testData));
    }

    @Test
    public void processor() throws Exception {
        // This test pushes in topic 2, it is processed and 
        // pushed in topic 1, and finally check the results coming from topic 1.
        List<String> testData = IntStream.range(0, 19).mapToObj(i -> Integer.toString(i)).collect(Collectors.toList());
        List<String> expected = testData.stream().map(i -> "Processed" + i).collect(Collectors.toList());
        CountDownLatch testChannelLatch = new CountDownLatch(testData.size());
        KafkaSampleBean kafkaConsumingBean = cdiContainer.select(KafkaSampleBean.class).get();
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produce(testData, TEST_TOPIC_2, testChannelLatch);
        assertEquals(expected.size(), kafkaConsumingBean.getConsumed().size());
        assertTrue(kafkaConsumingBean.getConsumed().containsAll(expected));
    }

    private void produce(List<String> testData, String topic, CountDownLatch testChannelLatch) throws InterruptedException {
        // Producer
        Map<String, String> p = Map.of(
                "mp.messaging.outgoing.test-channel.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.outgoing.test-channel.topic", topic,
                "mp.messaging.outgoing.test-channel.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel.value.serializer", StringSerializer.class.getName()
        );

        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        try (SimpleKafkaProducer<Long, String> producer = 
                new SimpleKafkaProducer<>(config.get("mp.messaging.outgoing.test-channel"))) {
            //Send all test messages(async send means order is not guaranteed) and in parallel
            testData.parallelStream().forEach(msg -> producer.produceAsync(msg));
            // Wait till records are delivered
            assertTrue(testChannelLatch.await(60, TimeUnit.SECONDS)
                    , "All messages not delivered in time, number of unreceived messages: "
                            + testChannelLatch.getCount());
        }
    }

    private <T> void forEachBean(Class<T> beanType, Annotation annotation, Consumer<T> consumer) {
        getInstance(beanType, annotation).stream().forEach(consumer);
    }
    
    private <T> Instance<T> getInstance(Class<T> beanType, Annotation annotation){
        return cdiContainer.select(beanType, annotation);
    }

    private SeContainer startCdiContainer(Map<String, String> p, Set<Class<?>> beanClasses) {
        p.put("mp.initializer.allow", "true");
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();
        MpConfigProviderResolver.instance()
                .registerConfig((org.eclipse.microprofile.config.Config) config,
                        Thread.currentThread().getContextClassLoader());
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertNotNull(initializer);
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }
}