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

package io.helidon.messaging.kafka;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Prepare Kafka properties from Helidon {@link io.helidon.config.Config Config}.
 * Configuration format as specified in the MicroProfile Reactive Messaging
 * Specification https://github.com/eclipse/microprofile-reactive-messaging
 *
 * <p>
 * See example with YAML configuration:
 * <pre>{@code
 * mp.messaging:
 *   incoming:
 *     test-channel:
 *       bootstrap.servers: localhost:9092
 *       topic: graph-done
 *       key.deserializer: org.apache.kafka.common.serialization.LongDeserializer
 *       value.deserializer: org.apache.kafka.common.serialization.StringDeserializer
 *
 *   outgoing:
 *     test-channel:
 *       bootstrap.servers: localhost:9092
 *       topic: graph-done
 *       key.serializer: org.apache.kafka.common.serialization.LongSerializer
 *       value.serializer: org.apache.kafka.common.serialization.StringSerializer
 *
 * }</pre>
 * <p>
 *
 * @see io.helidon.config.Config
 */
public class KafkaConfigProperties extends Properties {

    /**
     * Topic or topics delimited by commas
     */
    static final String TOPIC_NAME = "topic";

    /**
     * Consumer group id
     */
    static final String GROUP_ID = "group.id";

    /**
     * Prepare Kafka properties from Helidon {@link io.helidon.config.Config Config},
     * underscores in keys are translated to dots.
     *
     * @param config   parent config of kafka key
     */
    KafkaConfigProperties(Config config) {
        config.asNodeList().get().forEach(this::addProperty);
    }

    /**
     * Split comma separated topic names
     *
     * @return list of topic names
     */
    public List<String> getTopicNameList() {
        return Arrays.stream(getProperty(TOPIC_NAME)
                .split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private void addProperty(Config c) {
        String key = c.traverse().map(m -> m.key().parent().name() + "." + m.key().name())
                .collect(Collectors.joining("."));
        if (key.isEmpty()) {
            key = c.key().name();
        }
        String value;
        if (c.hasValue()) {
            value = c.asString().get();
        } else {
            value = c.traverse(v -> v.type() == Config.Type.VALUE)
                    .map(Config::asString)
                    .map(v -> v.orElse(""))
                    .findFirst()
                    .orElse("");
        }
        this.setProperty(key, value);
    }
}
