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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.config.Config;

class KafkaConfig {

    private static final String TOPIC_NAME = "topic";
    private static final String TOPIC_PATTERN = "topic.pattern";

    private final Map<String, Object> kafkaConfig;
    private List<String> topics;
    private Pattern topicPattern;

    private KafkaConfig(Map<String, Object> kafkaConfig) {
        this.kafkaConfig = kafkaConfig;
    }

    Map<String, Object> asMap(){
        return kafkaConfig;
    }

    List<String> topics(){
        return topics;
    }

    Optional<Pattern> topicPattern(){
        return Optional.ofNullable(topicPattern);
    }

    static KafkaConfig create(Config config) {
        Map<String, Object> kafkaConfig = new HashMap<>(config.detach().asMap().orElseGet(Map::of));
        KafkaConfig kc = new KafkaConfig(kafkaConfig);
        kc.topics = config.get(TOPIC_NAME).asList(String.class).orElseGet(List::of);
        kc.topicPattern = config.get(TOPIC_PATTERN).asString().map(Pattern::compile).orElse(null);
        return kc;
    }
}
