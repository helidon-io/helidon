/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.connectors.kafka;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.config.Config;

/**
 * Utility class for Kafka configuration.
 *
 * @see io.helidon.config.Config
 */
class HelidonToKafkaConfigParser {
    /**
     * Topic or topics delimited by commas.
     */
    private static final String TOPIC_NAME = "topic";

    private HelidonToKafkaConfigParser() {
    }

    /**
     * Utility method to translate the Config into a Map<String, Object> for Kafka.
     * @param config
     * @return the map
     */
    static Map<String, Object> toMap(Config config) {
        return new HashMap<>(config.detach()
                .asMap()
                .orElseGet(Map::of));
    }

    /**
     * Split comma separated topic names.
     * @param kafkaConfig
     * @return list of topic names
     */
    static List<String> topicNameList(Map<String, Object> kafkaConfig) {
        Object topics = kafkaConfig.get(TOPIC_NAME);
        if (topics != null) {
            return Arrays.stream(Objects.toString(topics).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
