/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.NodeConfigSource;

/**
 * Testing implementation of {@link io.helidon.config.spi.ConfigSource}.
 */
// https://github.com/helidon-io/helidon/issues/7488
class TestingConfigSource implements ConfigSource, EventConfigSource, NodeConfigSource {
    private final String keyToMutate;
    private BiConsumer<String, ConfigNode> consumer;

    TestingConfigSource(String keyToMutate) {
        this.keyToMutate = keyToMutate;
    }
    @Override
    public void onChange(BiConsumer<String, ConfigNode> changedNode) {
        this.consumer = changedNode;
    }

    @Override
    public Optional<ConfigContent.NodeContent> load() throws ConfigException {
        ConfigNode.ObjectNode rootNode = ConfigNode.ObjectNode.empty();

        return Optional.of(ConfigContent.NodeContent.builder()
                                   .node(rootNode)
                                   .build());
    }

    void update(String newValue) {
        consumer.accept(keyToMutate, ConfigNode.ValueNode.create(newValue));
    }

}
