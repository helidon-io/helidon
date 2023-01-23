/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.messaging.NackHandler;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.reactivestreams.FlowAdapters;

interface KafkaNackHandler<K, V> extends NackHandler<KafkaMessage<K, V>> {

    Function<Throwable, CompletionStage<Void>> getNack(KafkaMessage<K, V> message);

    static <K, V> KafkaNackHandler<K, V> create(EmittingPublisher<KafkaMessage<K, V>> emitter, Config config) {
        Config dlq = config.get("nack-dlq");
        Config logOnly = config.get("nack-log-only");
        if (dlq.exists()) {
            dlq = dlq.detach();
            return new KafkaNackHandler.KafkaDLQ<>(emitter, config, dlq);
        } else if (logOnly.exists() && logOnly.asBoolean().orElse(true)) {
            logOnly = logOnly.detach();
            return new KafkaNackHandler.Log<>(config, logOnly);
        }
        return new KafkaNackHandler.KillChannel<>(emitter);
    }

    class Log<K, V> implements KafkaNackHandler<K, V> {

        Log(Config config, Config logOnlyConfig) {
        }

        private static final System.Logger LOGGER = System.getLogger(KafkaNackHandler.Log.class.getName());

        @Override
        public Function<Throwable, CompletionStage<Void>> getNack(KafkaMessage<K, V> message) {
            return t -> nack(t, message);
        }

        private CompletionStage<Void> nack(Throwable t, KafkaMessage<K, V> message) {
            LOGGER.log(Level.WARNING, messageToString("NACKED Message - ignored", message));
            return CompletableFuture.completedFuture(null);
        }
    }

    class KillChannel<K, V> implements KafkaNackHandler<K, V> {

        private static final System.Logger LOGGER = System.getLogger(KafkaNackHandler.KillChannel.class.getName());
        private final EmittingPublisher<KafkaMessage<K, V>> emitter;

        KillChannel(EmittingPublisher<KafkaMessage<K, V>> emitter) {
            this.emitter = emitter;
        }

        @Override
        public Function<Throwable, CompletionStage<Void>> getNack(KafkaMessage<K, V> message) {
            return throwable -> nack(throwable, message);
        }

        private CompletionStage<Void> nack(Throwable t, KafkaMessage<K, V> message) {
            LOGGER.log(Level.ERROR, messageToString("NACKED message - killing the channel", message), t);
            emitter.fail(t);
            return CompletableFuture.failedStage(t);
        }
    }

    class KafkaDLQ<K, V> implements KafkaNackHandler<K, V> {

        private static final String DESERIALIZER_MASK = "([^.]*)Deserializer([^.]*$)";
        private static final String DESERIALIZER_REPLACEMENT = "$1Serializer$2";
        private final BufferedEmittingPublisher<KafkaMessage<K, V>> dlqEmitter;
        private final String dlqTopic;

        @SuppressWarnings("unchecked")
        KafkaDLQ(EmittingPublisher<KafkaMessage<K, V>> emitter, Config config, Config dlqConfig) {

            KafkaConfig kafkaConfig;

            if (dlqConfig.isLeaf()) {
                // nack-dlq=dlq_topic_name - Uses actual connection config with derived serializers, just set dql topic
                kafkaConfig = KafkaConfig.create(config);
                this.dlqTopic = dlqConfig.asString().get();
                kafkaConfig.topics().clear();
                kafkaConfig.topics().add(dlqTopic);
            } else {
                // Custom dlq connection config
                // nack-dlq.topic=dql_topic_name
                // nack-dlq.bootstrap.servers=...
                // nack-dlq.key.serializer=...
                kafkaConfig = KafkaConfig.create(dlqConfig);
                this.dlqTopic = dlqConfig.get("topic")
                        .asString()
                        .orElseThrow(() -> new IllegalStateException("Missing dlq.topic property!"));
                kafkaConfig.putIfAbsent("bootstrap.servers", () -> config.get("bootstrap.servers").as(String.class).get());
            }

            // Try to derive serializers from existing deserializers on actual connection config
            kafkaConfig.putIfAbsent("key.serializer", () -> config.get("key.deserializer")
                    .asString()
                    .orElseThrow(() -> new IllegalStateException("Missing dlq.key.serializer property!"))
                    .replaceAll(DESERIALIZER_MASK, DESERIALIZER_REPLACEMENT)
            );
            kafkaConfig.putIfAbsent("value.serializer", () -> config.get("value.deserializer")
                    .asString()
                    .orElseThrow(() -> new IllegalStateException("Missing dlq.value.serializer property!"))
                    .replaceAll(DESERIALIZER_MASK, DESERIALIZER_REPLACEMENT)
            );

            // Don't accidentally reuse original topic
            kafkaConfig.remove("topic");

            this.dlqEmitter = BufferedEmittingPublisher.create();
            dlqEmitter.onAbort(t -> {
                if (t != null) {
                    emitter.fail(new Exception("DLQ channel failed", t));
                } else {
                    emitter.fail(new Exception("DLQ channel cancelled"));
                }
            });
            Multi.create(dlqEmitter)
                    .subscribe(FlowAdapters.toFlowSubscriber((KafkaSubscriber<K, V>)
                            KafkaSubscriber.builder().config(kafkaConfig).build()));

        }

        @Override
        public Function<Throwable, CompletionStage<Void>> getNack(KafkaMessage<K, V> message) {
            return t -> this.nack(t, message);
        }

        private CompletionStage<Void> nack(Throwable t, KafkaMessage<K, V> origMsg) {
            KafkaMessage<K, V> dlqMsg = origMsg.getKey()
                    .map(k -> KafkaMessage.of(k, origMsg.getPayload(), () -> origMsg.ack()))
                    .orElseGet(() -> KafkaMessage.of(origMsg.getPayload(), () -> origMsg.ack()));

            Headers headers = dlqMsg.getHeaders();
            // add all original headers
            origMsg.getHeaders().forEach(headers::add);
            // Cleanup original topic if exists
            headers.remove("topic");
            // DLQ topic
            headers.add(new StringHeader("topic", dlqTopic));
            // Error cause of the nack
            headers.add(new StringHeader("dlq-error", t.getClass().getName()));
            headers.add(new StringHeader("dlq-error-msg", t.getMessage()));
            // Original message topic, offset and partition
            origMsg.getTopic().ifPresent(s -> headers.add(new StringHeader("dlq-orig-topic", s)));
            origMsg.getOffset().ifPresent(o -> headers.add(new StringHeader("dlq-orig-offset", o)));
            origMsg.getPartition().ifPresent(p -> headers.add(new StringHeader("dlq-orig-partition", p)));

            // Not really needed but try to fail faster this in edge case
            if (dlqEmitter.isCancelled()) {
                return CompletableFuture.failedFuture(new IllegalStateException("DQL stream  to " + dlqTopic + " cancelled!"));
            }

            // Looks like RC, but it's guarded above with dlqEmitter.onAbort
            // main channel gets killed and message doesn't get to ack in dlq channel before connection is killed anyway
            dlqEmitter.emit(dlqMsg);
            return CompletableFuture.completedFuture(null);
        }
    }

    class StringHeader extends RecordHeader {
        StringHeader(String key, Object value) {
            super(key, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    static <K, V> String messageToString(String prefix, KafkaMessage<K, V> message) {
        StringBuilder msg = new StringBuilder(prefix);
        message.getKey().ifPresent(k -> msg.append(" key: ").append(k));
        message.getTopic().ifPresent(s -> msg.append(" topic: ").append(s));
        message.getOffset().ifPresent(s -> msg.append(" offset: ").append(s));
        message.getPartition().ifPresent(s -> msg.append(" partition: ").append(s));
        return msg.toString();
    }
}
