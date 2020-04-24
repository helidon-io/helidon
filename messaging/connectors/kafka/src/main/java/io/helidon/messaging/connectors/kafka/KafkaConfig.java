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

import io.helidon.config.Config;

class KafkaConfig {

    private static final String TOPIC_NAME = "topic";

    private final Map<String, Object> kafkaConfig;
    private final List<String> topics;

    private KafkaConfig(Map<String, Object> kafkaConfig, List<String> topics) {
        this.kafkaConfig = kafkaConfig;
        this.topics = topics;
    }

    Map<String, Object> asMap(){
        return kafkaConfig;
    }

    List<String> topics(){
        return topics;
    }

    static KafkaConfig create(Config config) {
        Map<String, Object> kafkaConfig = new HashMap<>(config.detach().asMap().orElseGet(Map::of));
        List<String> topics = config.get(TOPIC_NAME).asList(String.class).orElseGet(List::of);
        return new KafkaConfig(kafkaConfig, topics);
    }
}
