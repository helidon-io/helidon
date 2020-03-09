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

package io.helidon.config;

import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.config.spi.ConfigNode;

import org.eclipse.microprofile.config.spi.ConfigSource;

class ConfigSourceMpRuntimeImpl extends ConfigSourceRuntimeBase {
    private final ConfigSource source;

    ConfigSourceMpRuntimeImpl(ConfigSource source) {
        this.source = source;
    }

    @Override
    public boolean isLazy() {
        // MP config sources are considered eager
        return false;
    }

    @Override
    public void onChange(BiConsumer<String, ConfigNode> change) {
        // this is a no-op - MP config sources do not support changes
    }

    @Override
    public Optional<ConfigNode.ObjectNode> load() {
        return Optional.of(ConfigUtils.mapToObjectNode(source.getProperties(), false));
    }

    @Override
    public Optional<ConfigNode> node(String key) {
        String value = source.getValue(key);

        if (null == value) {
            return Optional.empty();
        }

        return Optional.of(ConfigNode.ValueNode.create(value));
    }

    @Override
    public ConfigSource asMpSource() {
        return source;
    }

    @Override
    public String description() {
        return source.getName();
    }
}
