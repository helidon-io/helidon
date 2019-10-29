package io.helidon.messaging.kafka;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class SimpleKafkaTest {

    public static final String TEST_PRODUCER = "test-producer";
    public static final String TEST_CONSUMER_1 = "test-consumer-1";
    public static final String TEST_CONSUMER_2 = "test-consumer-2";
    public static final String TEST_MESSAGE = "this is a test message";

    @RegisterExtension
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource();
    public static final String TEST_TOPIC = "graph-done";

    @BeforeAll
    static void setUp() {
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC, 10, (short) 1);
    }

    @Test
    public void sendAndReceive() throws ExecutionException, InterruptedException, TimeoutException {
        Properties p = new Properties();
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".topic", TEST_TOPIC);
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".key.serializer", LongSerializer.class.getName());
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".value.serializer", StringSerializer.class.getName());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".topic", TEST_TOPIC);
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".key.deserializer", LongDeserializer.class.getName());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".value.deserializer", StringDeserializer.class.getName());
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        // Consumer
        SimpleKafkaConsumer<Long, String> consumer = new SimpleKafkaConsumer<>(TEST_CONSUMER_1, config);
        Future<?> consumerClosedFuture = consumer.consumeAsync(r -> {
            assertEquals(TEST_MESSAGE, r.value());
            consumer.close();
        });

        consumer.waitForPartitionAssigment(10, TimeUnit.SECONDS);

        // Producer
        SimpleKafkaProducer<Long, String> producer = new SimpleKafkaProducer<>(TEST_PRODUCER, config);
        producer.produceAsync(TEST_MESSAGE);

        try {
            consumerClosedFuture.get(10, TimeUnit.SECONDS);
            producer.close();
        } catch (TimeoutException e) {
            fail("Didn't receive test message in time");
        }
    }

    @Test
    public void queueBySameConsumerGroup() throws ExecutionException, InterruptedException, TimeoutException {
        final String TEST_GROUP = "XXX";

        Properties p = new Properties();
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".topic", TEST_TOPIC);
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".key.serializer", LongSerializer.class.getName());
        p.setProperty("mp.messaging.outcoming." + TEST_PRODUCER + ".value.serializer", StringSerializer.class.getName());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_2 + ".bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_2 + ".topic", TEST_TOPIC);
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_2 + ".group.id", TEST_GROUP);
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_2 + ".key.deserializer", LongDeserializer.class.getName());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_2 + ".value.deserializer", StringDeserializer.class.getName());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".bootstrap.servers", kafkaResource.getKafkaConnectString());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".topic", TEST_TOPIC);
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".group.id", TEST_GROUP);
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".key.deserializer", LongDeserializer.class.getName());
        p.setProperty("mp.messaging.incoming." + TEST_CONSUMER_1 + ".value.deserializer", StringDeserializer.class.getName());
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        List<String> receiviedByConsumer1 = Collections.synchronizedList(new ArrayList<>(4));
        List<String> receiviedByConsumer2 = Collections.synchronizedList(new ArrayList<>(4));

        CountDownLatch messagesCountingLatch = new CountDownLatch(4);

        // Consumer 1
        SimpleKafkaConsumer<Long, String> consumer1 = new SimpleKafkaConsumer<>(TEST_CONSUMER_1, config);
        consumer1.consumeAsync(r -> {
            messagesCountingLatch.countDown();
            receiviedByConsumer1.add(r.value());
        });

        // Consumer 2
        SimpleKafkaConsumer<Long, String> consumer2 = new SimpleKafkaConsumer<>(TEST_CONSUMER_2, config);
        consumer2.consumeAsync(r -> {
            messagesCountingLatch.countDown();
            receiviedByConsumer2.add(r.value());
        });

        // Wait till all consumers are ready
        consumer1.waitForPartitionAssigment(10, TimeUnit.SECONDS);
        consumer2.waitForPartitionAssigment(10, TimeUnit.SECONDS);

        // Producer
        SimpleKafkaProducer<Long, String> producer = new SimpleKafkaProducer<>(TEST_PRODUCER, config);
        List<Future<RecordMetadata>> producerFutures = new ArrayList<>(4);
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 1));
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 2));
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 3));
        producerFutures.addAll(producer.produceAsync(TEST_MESSAGE + 4));

        // Wait for all sent(this is example usage, sent doesn't mean delivered)
        producerFutures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                fail(e);
            }
        });

        // Wait till 4 records are delivered
        assertTrue(messagesCountingLatch.await(10, TimeUnit.SECONDS)
                , "All messages not delivered in time");

        consumer1.close();
        consumer2.close();
        producer.close();

        assertFalse(receiviedByConsumer1.isEmpty());
        assertFalse(receiviedByConsumer2.isEmpty());
        assertTrue(receiviedByConsumer1.stream().noneMatch(receiviedByConsumer2::contains));
        assertTrue(receiviedByConsumer2.stream().noneMatch(receiviedByConsumer1::contains));
    }

}
