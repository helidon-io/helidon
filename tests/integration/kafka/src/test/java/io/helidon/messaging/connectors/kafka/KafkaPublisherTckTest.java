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
 *
 */

package io.helidon.messaging.connectors.kafka;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.Test;

@Test
class KafkaPublisherTckTest extends PublisherVerification<KafkaMessage<String, Long>> {

    private static final String TEST_TOPIC_1 = "graph-done-1";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    KafkaPublisherTckTest() {
        super(new TestEnvironment(5000));
    }

    @Override
    public Publisher<KafkaMessage<String, Long>> createPublisher(long elements) {
        Consumer<String, Long> kafkaConsumer = Mockito.mock(Consumer.class);
        // Emulates that it is buffering 50 elements from Kafka in every poll.
        // This is buffered and it doesn't mean that it will publish them. The elements to publish depends on request
        Mockito.when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class))).thenReturn(createData(5));
        return ReactiveStreams.fromPublisher(
                KafkaPublisher.<String, Long>builder().scheduler(scheduler)
                .consumerSupplier(() -> kafkaConsumer).topics(Arrays.asList(TEST_TOPIC_1))
                .pollTimeout(1L).periodExecutions(1L).autoCommit(true).build())
                .limit(elements).buildRs();
    }

    private ConsumerRecords<String, Long> createData(long elementsToPoll){
        List<ConsumerRecord<String, Long>> records = new LinkedList<>();
        for (long i=0; i<elementsToPoll; i++) {
            records.add(new ConsumerRecord<>(TEST_TOPIC_1, 0, 0, "key", i));
        };
        return new ConsumerRecords<>(Map.of(new TopicPartition(TEST_TOPIC_1, 0), records));
    }

    @Override
    public Publisher<KafkaMessage<String, Long>> createFailedPublisher() {
        Consumer<String, Long> kafkaConsumer = Mockito.mock(Consumer.class);
        Mockito.doThrow(new RuntimeException("test error")).when(kafkaConsumer).poll(ArgumentMatchers.any(Duration.class));
        return KafkaPublisher.<String, Long>builder().scheduler(scheduler)
                .consumerSupplier(() -> kafkaConsumer).topics(Arrays.asList(TEST_TOPIC_1))
                .pollTimeout(1L).periodExecutions(1L).autoCommit(true).build();
    }
}
