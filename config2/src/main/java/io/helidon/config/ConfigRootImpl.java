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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class ConfigRootImpl implements Config {
    private final Map<String, ConfigNodeImpl> children = new ConcurrentHashMap<>();
    private final Key key = ConfigKeyImpl.of();
    private final ConfigSourcesRuntime sources;

    ConfigRootImpl(Builder builder) {
        ConfigSetup setup = new ConfigSetup(builder);
        List<ConfigSourceSetup> setups = builder.sources()
                .stream()
                .map(it -> new ConfigSourceSetup(it, false, null, null))
                .collect(Collectors.toList());
        this.sources = new ConfigSourcesRuntime(setup, setups);
        this.sources.init();
    }

    @Override
    public Config get(String key) {
        return get(Key.create(key));
    }

    @Override
    public Config get(Key key) {
        if (key.isRoot()) {
            return this;
        }
        // key = app.config.myMessage
        // or key = "enabled"
        List<String> elements = key.elements();
        String directChild = elements.get(0);

        if (elements.size() == 1) {
            return children.computeIfAbsent(directChild, it -> new ConfigNodeImpl(sources, this.key, it, key));
        }

        Key directChildKey = this.key.child(Key.create(directChild));
        // remove direct child key
        elements.remove(0);
        Key theRest = Key.create(elements);
        return children.computeIfAbsent(directChild, it -> new ConfigNodeImpl(sources, this.key, it, directChildKey))
                .get(theRest);
    }

    @Override
    public Optional<String> getValue() {
        // root cannot have a direct value
        return Optional.empty();
    }
}
