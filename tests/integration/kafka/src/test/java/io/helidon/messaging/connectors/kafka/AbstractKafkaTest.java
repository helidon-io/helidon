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

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractKafkaTest {

    @RegisterExtension
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource()
            .withBrokers(4)
            .withBrokerProperty("replication.factor", "2")
            .withBrokerProperty("min.insync.replicas", "1")
            .withBrokerProperty("auto.create.topics.enable", Boolean.toString(false));
    public static final String TEST_TOPIC_1 = "graph-done-1";
    public static final String TEST_TOPIC_2 = "graph-done-2";
    public static final String TEST_TOPIC_3 = "graph-done-3";
    public static final String TEST_TOPIC_4 = "graph-done-4";
    public static final String TEST_TOPIC_5 = "graph-done-5";
    public static final String TEST_TOPIC_6 = "graph-done-6";
    public static final String TEST_TOPIC_7 = "graph-done-7";
    public static final String TEST_TOPIC_8 = "graph-done-8";
    public static final String TEST_TOPIC_10 = "graph-done-10";
    public static final String TEST_TOPIC_13 = "graph-done-13";
    protected final String KAFKA_SERVER = kafkaResource.getKafkaConnectString();


    @BeforeAll
    static void prepareTopics() {
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_1, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_2, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_3, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_4, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_5, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_6, 1, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_7, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_8, 2, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_10, 1, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_13, 1, (short) 2);
    }
}
