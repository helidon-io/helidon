/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.kafka;

import java.util.regex.Pattern;

import io.helidon.messaging.ConnectorConfigBuilder;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;


/**
 * Build Kafka specific config.
 */
public final class KafkaConfigBuilder extends ConnectorConfigBuilder {

    KafkaConfigBuilder() {
        super();
        super.property(ConnectorFactory.CONNECTOR_ATTRIBUTE, KafkaConnector.CONNECTOR_NAME);
    }

    /**
     * Add custom property.
     *
     * @param key   property key
     * @param value property value
     * @return this builder
     */
    public KafkaConfigBuilder property(String key, String value) {
        super.property(key, value);
        return this;
    }

    /**
     * A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
     * The client will make use of all servers irrespective of which servers are specified here for
     * bootstrapping—this list only impacts the initial hosts used to discover the full set of servers.
     * This list should be in the form {@code host1:port1,host2:port2,....}
     * Since these servers are just used for the initial connection to discover the full cluster
     * membership (which may change dynamically), this list need not contain the full set of servers
     * (you may want more than one, though, in case a server is down).
     * <ul>
     * <li>Type: list</li>
     * <li>Default: ""</li>
     * <li>Valid Values: non-null string</li>
     * </ul>
     *
     * @param servers list of host/port pairs
     * @return this builder
     */
    public KafkaConfigBuilder bootstrapServers(String servers) {
        super.property("bootstrap.servers", servers);
        return this;
    }

    /**
     * Names of the topics to consume from.
     *
     * @param topics topic name
     * @return this builder
     */
    public KafkaConfigBuilder topic(String... topics) {
        super.property("topic", String.join(",", topics));
        return this;
    }

    /**
     * Pattern for topic names to consume from.
     *
     * @param topicPattern topic name regex pattern
     * @return this builder
     */
    public KafkaConfigBuilder topicPattern(Pattern topicPattern) {
        super.property("topic.pattern", topicPattern.pattern());
        return this;
    }

    /**
     * Regex pattern for topic names to consume from.
     *
     * @param topicPattern topic name regex pattern
     * @return this builder
     */
    public KafkaConfigBuilder topicPattern(String topicPattern) {
        super.property("topic.pattern", topicPattern);
        return this;
    }

    /**
     * A unique string that identifies the consumer group this consumer belongs to.
     * This property is required.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param groupId consumer group identifier
     * @return this builder
     */
    public KafkaConfigBuilder groupId(String groupId) {
        super.property("group.id", groupId);
        return this;
    }

    /**
     * If true the consumer's offset will be periodically committed in the background.
     *
     * <ul>
     * <li>Type: boolean</li>
     * <li>Default: true</li>
     * </ul>
     *
     * @param enableAutoCommit true for automatic offset committing
     * @return this builder
     */
    public KafkaConfigBuilder enableAutoCommit(boolean enableAutoCommit) {
        super.property("enable.auto.commit", String.valueOf(enableAutoCommit));
        return this;
    }

    /**
     * Serializer class for key that implements the {@link org.apache.kafka.common.serialization.Serializer} interface.
     *
     * @param keySerializer class responsible for key serializing
     * @return this builder
     */
    public KafkaConfigBuilder keySerializer(Class<? extends Serializer<?>> keySerializer) {
        super.property("key.serializer", keySerializer.getName());
        return this;
    }

    /**
     * Deserializer class for key that implements the {@link org.apache.kafka.common.serialization.Deserializer} interface.
     *
     * @param keyDeserializer class responsible for key de-serializing
     * @return this builder
     */
    public KafkaConfigBuilder keyDeserializer(Class<? extends Deserializer<?>> keyDeserializer) {
        super.property("key.deserializer", keyDeserializer.getName());
        return this;
    }

    /**
     * Serializer class for value that implements the {@link org.apache.kafka.common.serialization.Serializer} interface.
     *
     * @param valueSerializer class responsible for value serializing
     * @return this builder
     */
    public KafkaConfigBuilder valueSerializer(Class<? extends Serializer<?>> valueSerializer) {
        super.property("value.serializer", valueSerializer.getName());
        return this;
    }

    /**
     * Deserializer class for value that implements the {@link org.apache.kafka.common.serialization.Deserializer} interface.
     *
     * @param valueDeserializer class responsible for value de-serializing
     * @return this builder
     */
    public KafkaConfigBuilder valueDeserializer(Class<? extends Deserializer<?>> valueDeserializer) {
        super.property("value.deserializer", valueDeserializer.getName());
        return this;
    }

    /**
     * The maximum time to block polling loop in milliseconds.
     *
     * @param pollTimeout time to block polling loop in milliseconds
     * @return this builder
     */
    public KafkaConfigBuilder pollTimeout(long pollTimeout) {
        super.property("poll.timeout", String.valueOf(pollTimeout));
        return this;
    }

    /**
     * Period between successive executions of polling loop.
     *
     * @param periodExecutions in milliseconds
     * @return this builder
     */
    public KafkaConfigBuilder periodExecutions(long periodExecutions) {
        super.property("period.executions", String.valueOf(periodExecutions));
        return this;
    }

    /**
     * What to do when there is no initial offset in Kafka or if the current offset does not exist any more on the server
     * (e.g. because that data has been deleted):
     *
     * <ul>
     * <li>earliest: automatically reset the offset to the earliest offset</li>
     * <li>latest: automatically reset the offset to the latest offset</li>
     * <li>none: throw exception to the consumer if no previous offset is found for the consumer's group</li>
     * </ul>
     * <ul>
     * <li>Type: string</li>
     * <li>Default: latest</li>
     * <li>Valid Values: [latest, earliest, none]</li>
     * </ul>
     * <p>
     *
     * @param autoOffsetReset [latest, earliest, none]
     * @return this builder
     */
    public KafkaConfigBuilder autoOffsetReset(AutoOffsetReset autoOffsetReset) {
        super.property("auto.offset.reset", autoOffsetReset.name().toLowerCase());
        return this;
    }

    /**
     * What to do when there is no initial offset in Kafka.
     */
    public enum AutoOffsetReset {
        /**
         * Automatically reset the offset to the earliest offset.
         */
        LATEST,
        /**
         * Automatically reset the offset to the latest offset.
         */
        EARLIEST,
        /**
         * Throw exception to the consumer if no previous offset is found for the consumer's group.
         */
        NONE
    }

    /**
     * The producer will attempt to batch records together into fewer requests whenever multiple records
     * are being sent to the same partition. This helps performance on both the client and the server.
     * This configuration controls the default batch size in bytes. No attempt will be made to batch
     * records larger than this size. Requests sent to brokers will contain multiple batches,
     * one for each partition with data available to be sent. A small batch size will make batching
     * less common and may reduce throughput (a batch size of zero will disable batching entirely).
     * A very large batch size may use memory a bit more wastefully as we will always allocate
     * a buffer of the specified batch size in anticipation of additional records.
     *
     * <ul>
     * <li>Type: int</li>
     * <li>Default: 16384</li>
     * </ul>
     *
     * @param batchSize batch size in bytes
     * @return this builder
     */
    public KafkaConfigBuilder batchSize(int batchSize) {
        super.property("batch.size", String.valueOf(batchSize));
        return this;
    }

    /**
     * The number of acknowledgments the producer requires the leader to have received before considering a request complete.
     * This controls the durability of records that are sent.
     * <p>
     * The following settings are allowed:
     * </p>
     * <ul>
     * <li><b>acks=0</b> If set to zero then the producer will not wait for any acknowledgment from the server at all.
     * The record will be immediately added to the socket buffer and considered sent.
     * No guarantee can be made that the server has received the record in this case,
     * and the retries configuration will not take effect (as the client won't generally
     * know of any failures). The offset given back for each record will always be set to -1.</li>
     * <li><b>acks=1</b> This will mean the leader will write the record to its local log but will
     * respond without awaiting full acknowledgement from all followers. In this case should
     * the leader fail immediately after acknowledging the record but before the followers
     * have replicated it then the record will be lost.</li>
     * <li><b>acks=all</b> This means the leader will wait for the full set of in-sync replicas
     * to acknowledge the record. This guarantees that the record will not be lost
     * as long as at least one in-sync replica remains alive. This is the strongest available guarantee.
     * This is equivalent to the acks=-1 setting.</li>
     *
     * </ul>
     * <ul>
     * <li>Type: string</li>
     * <li>Default: 1</li>
     * <li>Valid Values: [all, -1, 0, 1]</li>
     * </ul>
     *
     * @param acks acknowledge mode
     * @return this builder
     */
    public KafkaConfigBuilder acks(String acks) {
        super.property("acks", acks);
        return this;
    }

    /**
     * The total bytes of memory the producer can use to buffer records waiting to be sent to the server.
     * If records are sent faster than they can be delivered to the server the producer will
     * block for {@code max.block.ms} after which it will throw an exception.
     * This setting should correspond roughly to the total memory the producer will use,
     * but is not a hard bound since not all memory the producer uses is used for buffering.
     * Some additional memory will be used for compression
     * (if compression is enabled) as well as for maintaining in-flight requests.
     *
     * <ul>
     * <li>Type: long</li>
     * <li>Default: 33554432</li>
     * </ul>
     *
     * @param bufferMemory bytes of memory
     * @return this builder
     */
    public KafkaConfigBuilder bufferMemory(long bufferMemory) {
        super.property("buffer.memory", String.valueOf(bufferMemory));
        return this;
    }

    /**
     * The compression type for all data generated by the producer. The default is none (i.e. no compression).
     * Valid values are none, gzip, snappy, lz4, or zstd. Compression is of full batches of data,
     * so the efficacy of batching will also impact the compression ratio (more batching means better compression).
     *
     * <ul>
     * <li>Type: string</li>
     * <li>Default: none</li>
     * <li>Valid Values: [none, gzip, snappy, lz4, zstd]</li>
     * </ul>
     *
     * @param compressionType compression type
     * @return this builder
     */
    public KafkaConfigBuilder compressionType(String compressionType) {
        super.property("compression.type", compressionType);
        return this;
    }

    /**
     * Setting a value greater than zero will cause the client to resend any record whose send
     * fails with a potentially transient error. Note that this retry is no different than
     * if the client resent the record upon receiving the error.
     * Allowing retries without setting {@code max.in.flight.requests.per.connection} to 1 will potentially
     * change the ordering of records because if two batches are sent to a single partition,
     * and the first fails and is retried but the second succeeds,
     * then the records in the second batch may appear first. Note additionally that produce
     * requests will be failed before the number of retries has been exhausted if the timeout
     * configured by delivery.timeout.ms expires first before successful acknowledgement.
     * Users should generally prefer to leave this config unset and instead use delivery.timeout.ms
     * to control retry behavior.
     *
     * <ul>
     * <li>Type: int</li>
     * <li>Default: 2147483647</li>
     * </ul>
     *
     * @param retries number of retries
     * @return this builder
     */
    public KafkaConfigBuilder retries(int retries) {
        super.property("retries", String.valueOf(retries));
        return this;
    }
}
